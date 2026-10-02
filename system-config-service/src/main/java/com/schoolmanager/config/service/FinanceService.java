package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service métier pour la gestion financière des établissements :
 * Frais de scolarité, bourses d'études, encaissements, reçus, tableau d'amortissement et statistiques.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final TuitionFeeRepository tuitionFeeRepository;
    private final StudentPaymentRepository studentPaymentRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final StudentScholarshipRepository studentScholarshipRepository;
    private final ReceiptReportService receiptReportService;
    private final NotificationService notificationService;

    // ==================== DASHBOARD FINANCIER ====================

    @Transactional(readOnly = true)
    public FinanceDashboardStatsDto getFinanceDashboardStats(UUID tenantId, UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);

        List<StudentEnrollment> enrollments = studentEnrollmentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);
        List<TuitionFee> allFees = tuitionFeeRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);

        Map<UUID, List<TuitionFee>> feesByLevel = allFees.stream()
                .filter(f -> f.getAcademicLevel() != null)
                .collect(Collectors.groupingBy(f -> f.getAcademicLevel().getId()));

        List<StudentPayment> allPayments = studentPaymentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);

        BigDecimal totalPaid = allPayments.stream()
                .map(p -> p.getAmountPaid() != null ? p.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Classroom, List<StudentEnrollment>> enrollmentsByClass = enrollments.stream()
                .filter(e -> e.getClassroom() != null)
                .collect(Collectors.groupingBy(StudentEnrollment::getClassroom));

        BigDecimal totalExigible = BigDecimal.ZERO;
        List<ClassRecoveryRateDto> classRecoveryRates = new ArrayList<>();

        Map<UUID, BigDecimal> paymentsByStudent = allPayments.stream()
                .filter(p -> p.getStudent() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getStudent().getId(),
                        Collectors.reducing(BigDecimal.ZERO, p -> p.getAmountPaid() != null ? p.getAmountPaid() : BigDecimal.ZERO, BigDecimal::add)
                ));

        for (Map.Entry<Classroom, List<StudentEnrollment>> entry : enrollmentsByClass.entrySet()) {
            Classroom classroom = entry.getKey();
            List<StudentEnrollment> classEnrollments = entry.getValue();

            BigDecimal classExigible = BigDecimal.ZERO;
            if (classroom.getAcademicLevel() != null) {
                UUID levelId = classroom.getAcademicLevel().getId();
                List<TuitionFee> levelFees = feesByLevel.getOrDefault(levelId, Collections.emptyList());
                BigDecimal levelTotalFee = levelFees.stream()
                        .map(f -> f.getAmount() != null ? f.getAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                classExigible = levelTotalFee.multiply(BigDecimal.valueOf(classEnrollments.size()));
            }

            BigDecimal classPaid = BigDecimal.ZERO;
            for (StudentEnrollment enrollment : classEnrollments) {
                if (enrollment.getStudent() != null) {
                    BigDecimal studentPaid = paymentsByStudent.getOrDefault(enrollment.getStudent().getId(), BigDecimal.ZERO);
                    classPaid = classPaid.add(studentPaid);
                }
            }

            double classRate = classExigible.compareTo(BigDecimal.ZERO) > 0
                    ? classPaid.divide(classExigible, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0;

            classRecoveryRates.add(ClassRecoveryRateDto.builder()
                    .classroomId(classroom.getId())
                    .className(classroom.getName())
                    .classCode(classroom.getCode())
                    .totalStudents(classEnrollments.size())
                    .exigible(classExigible)
                    .paid(classPaid)
                    .recoveryRate(Math.round(classRate * 100.0) / 100.0)
                    .build());

            totalExigible = totalExigible.add(classExigible);
        }

        classRecoveryRates.sort(Comparator.comparingDouble(ClassRecoveryRateDto::getRecoveryRate));

        BigDecimal totalBalance = totalExigible.subtract(totalPaid);
        if (totalBalance.compareTo(BigDecimal.ZERO) < 0) {
            totalBalance = BigDecimal.ZERO;
        }
        double overallRate = totalExigible.compareTo(BigDecimal.ZERO) > 0
                ? totalPaid.divide(totalExigible, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        Map<String, BigDecimal> monthlyMap = new TreeMap<>();
        for (StudentPayment p : allPayments) {
            if (p.getPaymentDate() != null) {
                String monthKey = p.getPaymentDate().getYear() + "-" + String.format("%02d", p.getPaymentDate().getMonthValue());
                BigDecimal amt = p.getAmountPaid() != null ? p.getAmountPaid() : BigDecimal.ZERO;
                monthlyMap.put(monthKey, monthlyMap.getOrDefault(monthKey, BigDecimal.ZERO).add(amt));
            }
        }
        List<MonthlyCollectionDto> monthlyCollections = monthlyMap.entrySet().stream()
                .map(e -> MonthlyCollectionDto.builder()
                        .month(e.getKey())
                        .amount(e.getValue())
                        .build())
                .collect(Collectors.toList());

        Map<String, BigDecimal> methodMap = new HashMap<>();
        for (StudentPayment p : allPayments) {
            String method = p.getPaymentMethod() != null && !p.getPaymentMethod().trim().isEmpty()
                    ? p.getPaymentMethod().trim().toUpperCase()
                    : "AUTRE";
            BigDecimal amt = p.getAmountPaid() != null ? p.getAmountPaid() : BigDecimal.ZERO;
            methodMap.put(method, methodMap.getOrDefault(method, BigDecimal.ZERO).add(amt));
        }
        List<PaymentMethodStatDto> paymentMethodBreakdown = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : methodMap.entrySet()) {
            double methodRate = totalPaid.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().divide(totalPaid, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0;
            paymentMethodBreakdown.add(PaymentMethodStatDto.builder()
                    .method(entry.getKey())
                    .amount(entry.getValue())
                    .percentage(Math.round(methodRate * 100.0) / 100.0)
                    .build());
        }

        List<StudentPayment> recentPayments = allPayments.stream()
                .sorted(Comparator.comparing(StudentPayment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        return FinanceDashboardStatsDto.builder()
                .totalExigible(totalExigible)
                .totalPaid(totalPaid)
                .totalBalance(totalBalance)
                .recoveryRate(Math.round(overallRate * 100.0) / 100.0)
                .monthlyCollections(monthlyCollections)
                .paymentMethodBreakdown(paymentMethodBreakdown)
                .classRecoveryRates(classRecoveryRates)
                .recentPayments(recentPayments)
                .build();
    }

    // ==================== CONFIGURATION DES FRAIS ====================

    @Transactional(readOnly = true)
    public List<TuitionFee> getFees(UUID tenantId, UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return tuitionFeeRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);
    }

    @Transactional
    public TuitionFee saveFee(TuitionFee fee) {
        if (!SecurityUtils.isSuperAdmin()) {
            fee.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return tuitionFeeRepository.save(fee);
    }

    @Transactional
    public void deleteFee(UUID id) {
        TuitionFee fee = tuitionFeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Frais de scolarité introuvable"));
        SecurityUtils.assertOwnership(fee.getTenantId());
        tuitionFeeRepository.deleteById(id);
    }

    // ==================== REGISTRE DES PAIEMENTS ====================

    @Transactional(readOnly = true)
    public List<StudentPayment> getPayments(UUID tenantId, UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return studentPaymentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);
    }

    @Transactional
    public StudentPayment savePayment(StudentPayment payment) {
        UUID tenantId = SecurityUtils.isSuperAdmin() ? payment.getTenantId() : SecurityUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        payment.setTenantId(tenantId);

        // Résolution Student
        if (payment.getStudent() != null && payment.getStudent().getId() != null) {
            Student s = studentRepository.findById(payment.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            SecurityUtils.assertOwnership(s.getTenantId());
            payment.setStudent(s);
        }

        // Intégrité Financière : Validation du montant payé par rapport au solde restant
        if (payment.getStudent() != null && payment.getStudent().getId() != null && payment.getAcademicYearId() != null) {
            UUID studentId = payment.getStudent().getId();
            UUID yearId = payment.getAcademicYearId();

            Optional<StudentEnrollment> enrollmentOpt = studentEnrollmentRepository.findByStudentIdAndAcademicYearId(studentId, yearId);
            if (enrollmentOpt.isPresent()) {
                StudentEnrollment enrollment = enrollmentOpt.get();
                if (enrollment.getClassroom() != null && enrollment.getClassroom().getAcademicLevel() != null) {
                    UUID levelId = enrollment.getClassroom().getAcademicLevel().getId();

                    List<TuitionFee> fees = tuitionFeeRepository.findByAcademicLevelIdAndAcademicYearId(levelId, yearId);
                    BigDecimal totalGrossExigible = fees.stream()
                            .map(TuitionFee::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    List<StudentScholarship> scholarships = studentScholarshipRepository.findByStudentIdAndAcademicYearId(studentId, yearId);
                    BigDecimal totalScholarshipDiscount = BigDecimal.ZERO;
                    for (StudentScholarship sch : scholarships) {
                        if ("PERCENTAGE".equalsIgnoreCase(sch.getDiscountType())) {
                            BigDecimal disc = totalGrossExigible.multiply(sch.getDiscountValue())
                                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                            totalScholarshipDiscount = totalScholarshipDiscount.add(disc);
                        } else {
                            totalScholarshipDiscount = totalScholarshipDiscount.add(sch.getDiscountValue());
                        }
                    }
                    if (totalScholarshipDiscount.compareTo(totalGrossExigible) > 0) {
                        totalScholarshipDiscount = totalGrossExigible;
                    }
                    BigDecimal totalExigible = totalGrossExigible.subtract(totalScholarshipDiscount);

                    List<StudentPayment> existingPayments = studentPaymentRepository.findByStudentIdAndAcademicYearId(studentId, yearId);
                    BigDecimal totalPaid = existingPayments.stream()
                            .filter(p -> payment.getId() == null || !p.getId().equals(payment.getId()))
                            .map(StudentPayment::getAmountPaid)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal remainingBalance = totalExigible.subtract(totalPaid);

                    if (payment.getAmountPaid() != null && payment.getAmountPaid().compareTo(remainingBalance.add(BigDecimal.valueOf(0.01))) > 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                String.format("Le versement saisi (%s) dépasse le solde restant dû de l'élève qui est de %s pour cette année scolaire.",
                                        payment.getAmountPaid(), remainingBalance));
                    }
                }
            }
        }

        if (payment.getReceiptNumber() == null || payment.getReceiptNumber().trim().isEmpty()) {
            long count = studentPaymentRepository.count() + 1;
            String prefix = "REC-" + LocalDate.now().getYear() + "-";
            payment.setReceiptNumber(prefix + String.format("%05d", count));
        }

        StudentPayment saved = studentPaymentRepository.save(payment);
        if (saved.getStudent() != null) {
            String title = "Reçu de paiement enregistré";
            String textMsg = String.format("Un versement de %s a été enregistré pour votre enfant %s %s. Reçu N° %s.",
                    saved.getAmountPaid().toString(),
                    saved.getStudent().getFirstName(), saved.getStudent().getLastName(),
                    saved.getReceiptNumber());
            notificationService.sendNotification(saved.getTenantId(), null, saved.getStudent().getParentPhone(), saved.getStudent().getEmail(),
                    title, textMsg, "FINANCE");
        }
        return saved;
    }

    @Transactional
    public void deletePayment(UUID id) {
        StudentPayment payment = studentPaymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
        SecurityUtils.assertOwnership(payment.getTenantId());
        studentPaymentRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public byte[] generatePaymentReceiptPdf(UUID id) {
        return receiptReportService.generatePaymentReceipt(id);
    }

    // ==================== BOURSES & PRISES EN CHARGE ====================

    @Transactional(readOnly = true)
    public List<StudentScholarship> getScholarships(UUID tenantId, UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return studentScholarshipRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);
    }

    @Transactional
    public StudentScholarship saveScholarship(StudentScholarship scholarship) {
        if (!SecurityUtils.isSuperAdmin()) {
            scholarship.setTenantId(SecurityUtils.getCurrentTenantId());
        }

        if (scholarship.getStudent() != null && scholarship.getStudent().getId() != null) {
            Student s = studentRepository.findById(scholarship.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            SecurityUtils.assertOwnership(s.getTenantId());
            scholarship.setStudent(s);
        }

        return studentScholarshipRepository.save(scholarship);
    }

    @Transactional
    public void deleteScholarship(UUID id) {
        StudentScholarship scholarship = studentScholarshipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bourse ou prise en charge introuvable"));
        SecurityUtils.assertOwnership(scholarship.getTenantId());
        studentScholarshipRepository.deleteById(id);
    }

    // ==================== RELEVÉ FINANCIER & ÉCHÉANCIER D'AMORTISSEMENT ====================

    @Transactional(readOnly = true)
    public StudentLedgerDto getStudentLedger(UUID studentId, UUID yearId, UUID levelId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));

        SecurityUtils.assertOwnership(student.getTenantId());

        List<TuitionFee> fees = tuitionFeeRepository.findByAcademicLevelIdAndAcademicYearId(levelId, yearId);

        BigDecimal totalGrossExigible = fees.stream()
                .map(TuitionFee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StudentScholarship> scholarships = studentScholarshipRepository.findByStudentIdAndAcademicYearId(studentId, yearId);

        BigDecimal totalScholarshipDiscount = BigDecimal.ZERO;
        for (StudentScholarship sch : scholarships) {
            if ("PERCENTAGE".equalsIgnoreCase(sch.getDiscountType())) {
                BigDecimal disc = totalGrossExigible.multiply(sch.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                totalScholarshipDiscount = totalScholarshipDiscount.add(disc);
            } else {
                totalScholarshipDiscount = totalScholarshipDiscount.add(sch.getDiscountValue());
            }
        }
        if (totalScholarshipDiscount.compareTo(totalGrossExigible) > 0) {
            totalScholarshipDiscount = totalGrossExigible;
        }

        BigDecimal totalExigible = totalGrossExigible.subtract(totalScholarshipDiscount);

        List<StudentPayment> payments = studentPaymentRepository.findByStudentIdAndAcademicYearId(studentId, yearId);

        BigDecimal totalPaid = payments.stream()
                .map(StudentPayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = totalExigible.subtract(totalPaid);

        List<AmortizationInstallmentDto> amortizationTable = new ArrayList<>();

        for (TuitionFee fee : fees) {
            int count = fee.getInstallmentsCount() != null ? fee.getInstallmentsCount() : 1;
            String freq = fee.getPaymentFrequency() != null ? fee.getPaymentFrequency() : "UNIQUE";
            BigDecimal totalAmount = fee.getAmount();
            if (totalGrossExigible.compareTo(BigDecimal.ZERO) > 0 && totalScholarshipDiscount.compareTo(BigDecimal.ZERO) > 0) {
                totalAmount = fee.getAmount().multiply(totalExigible)
                        .divide(totalGrossExigible, 2, java.math.RoundingMode.HALF_UP);
            }

            if (count <= 1) {
                amortizationTable.add(AmortizationInstallmentDto.builder()
                        .feeName(fee.getName())
                        .installmentNumber(1)
                        .totalInstallments(1)
                        .amountDue(totalAmount)
                        .amountPaid(BigDecimal.ZERO)
                        .amountRemaining(totalAmount)
                        .dueDate(LocalDate.now().withMonth(9).withDayOfMonth(15))
                        .status("PENDING")
                        .build());
            } else {
                BigDecimal installmentAmount = totalAmount.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal remainder = totalAmount.subtract(installmentAmount.multiply(BigDecimal.valueOf(count - 1)));

                LocalDate baseDate = LocalDate.now().withMonth(9).withDayOfMonth(15);

                for (int i = 1; i <= count; i++) {
                    BigDecimal due = (i == count) ? remainder : installmentAmount;
                    LocalDate dueDate;
                    if ("MONTHLY".equalsIgnoreCase(freq)) {
                        dueDate = baseDate.plusMonths(i - 1);
                    } else if ("TRIMESTRIEL".equalsIgnoreCase(freq)) {
                        dueDate = baseDate.plusMonths((i - 1) * 3);
                    } else {
                        dueDate = baseDate;
                    }

                    amortizationTable.add(AmortizationInstallmentDto.builder()
                            .feeName(fee.getName())
                            .installmentNumber(i)
                            .totalInstallments(count)
                            .amountDue(due)
                            .amountPaid(BigDecimal.ZERO)
                            .amountRemaining(due)
                            .dueDate(dueDate)
                            .status("PENDING")
                            .build());
                }
            }
        }

        amortizationTable.sort(Comparator.comparing(AmortizationInstallmentDto::getDueDate));

        BigDecimal remainingPaid = totalPaid;
        for (AmortizationInstallmentDto inst : amortizationTable) {
            if (remainingPaid.compareTo(BigDecimal.ZERO) <= 0) {
                inst.setStatus("PENDING");
                continue;
            }

            BigDecimal due = inst.getAmountDue();
            if (remainingPaid.compareTo(due) >= 0) {
                inst.setAmountPaid(due);
                inst.setAmountRemaining(BigDecimal.ZERO);
                inst.setStatus("PAID");
                remainingPaid = remainingPaid.subtract(due);
            } else {
                inst.setAmountPaid(remainingPaid);
                inst.setAmountRemaining(due.subtract(remainingPaid));
                inst.setStatus("PARTIAL");
                remainingPaid = BigDecimal.ZERO;
            }
        }

        return StudentLedgerDto.builder()
                .studentId(studentId)
                .studentName(student.getLastName() + " " + student.getFirstName())
                .registrationNumber(student.getRegistrationNumber())
                .totalGrossExigible(totalGrossExigible)
                .totalScholarshipDiscount(totalScholarshipDiscount)
                .totalExigible(totalExigible)
                .totalPaid(totalPaid)
                .balance(balance)
                .scholarships(scholarships)
                .payments(payments)
                .feesStructure(fees)
                .amortizationTable(amortizationTable)
                .build();
    }

    // ==================== DTOs FINANCIERS ====================

    @Data
    @Builder
    public static class StudentLedgerDto {
        private UUID studentId;
        private String studentName;
        private String registrationNumber;
        private BigDecimal totalGrossExigible;
        private BigDecimal totalScholarshipDiscount;
        private BigDecimal totalExigible;
        private BigDecimal totalPaid;
        private BigDecimal balance;
        private List<StudentScholarship> scholarships;
        private List<StudentPayment> payments;
        private List<TuitionFee> feesStructure;
        private List<AmortizationInstallmentDto> amortizationTable;
    }

    @Data
    @Builder
    public static class AmortizationInstallmentDto {
        private String feeName;
        private int installmentNumber;
        private int totalInstallments;
        private BigDecimal amountDue;
        private BigDecimal amountPaid;
        private BigDecimal amountRemaining;
        private LocalDate dueDate;
        private String status; // PAID, PARTIAL, PENDING
    }

    @Data
    @Builder
    public static class FinanceDashboardStatsDto {
        private BigDecimal totalExigible;
        private BigDecimal totalPaid;
        private BigDecimal totalBalance;
        private double recoveryRate;
        private List<MonthlyCollectionDto> monthlyCollections;
        private List<PaymentMethodStatDto> paymentMethodBreakdown;
        private List<ClassRecoveryRateDto> classRecoveryRates;
        private List<StudentPayment> recentPayments;
    }

    @Data
    @Builder
    public static class MonthlyCollectionDto {
        private String month;
        private BigDecimal amount;
    }

    @Data
    @Builder
    public static class PaymentMethodStatDto {
        private String method;
        private BigDecimal amount;
        private double percentage;
    }

    @Data
    @Builder
    public static class ClassRecoveryRateDto {
        private UUID classroomId;
        private String className;
        private String classCode;
        private int totalStudents;
        private BigDecimal exigible;
        private BigDecimal paid;
        private double recoveryRate;
    }
}
