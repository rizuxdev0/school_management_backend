package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Attendance;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentPayment;
import com.schoolmanager.config.entity.StudentScholarship;
import com.schoolmanager.config.entity.TuitionFee;
import com.schoolmanager.config.repository.AttendanceRepository;
import com.schoolmanager.config.repository.StudentPaymentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.repository.StudentScholarshipRepository;
import com.schoolmanager.config.repository.TuitionFeeRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.StudentEnrollment;
import com.schoolmanager.config.repository.StudentEnrollmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour les absences et la finance (frais, paiements, relevé, dashboard).
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/academics-finance")
@RequiredArgsConstructor
public class AttendanceAndFinanceController {

    private final AttendanceRepository attendanceRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final StudentPaymentRepository studentPaymentRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final com.schoolmanager.config.service.ReceiptReportService receiptReportService;
    private final StudentScholarshipRepository studentScholarshipRepository;

    // ==================== 1. ABSENCES & ASSIDUITÉ ====================

    @GetMapping("/attendance/classroom/{classroomId}/date/{date}")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ResponseEntity<List<Attendance>> getAttendance(
            @PathVariable UUID classroomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceRepository.findByClassroomIdAndAttendanceDate(classroomId, date));
    }

    @PostMapping("/attendance/save-all")
    @PreAuthorize("hasAuthority('ATTENDANCE_EDIT')")
    public ResponseEntity<List<Attendance>> saveAttendance(@RequestBody List<Attendance> attendanceList) {
        // Super Admin can save attendance for any tenant (tenantId comes from payload)
        List<Attendance> saved = new ArrayList<>();
        for (Attendance att : attendanceList) {
            if (!SecurityUtils.isSuperAdmin()) {
                att.setTenantId(SecurityUtils.getCurrentTenantId());
            }
            Optional<Attendance> existing = attendanceRepository.findByStudentIdAndAttendanceDate(
                    att.getStudent().getId(),
                    att.getAttendanceDate()
            );
            if (existing.isPresent()) {
                Attendance e = existing.get();
                e.setStatus(att.getStatus());
                e.setIsExcused(att.getIsExcused());
                e.setRemarks(att.getRemarks());
                saved.add(attendanceRepository.save(e));
            } else {
                saved.add(attendanceRepository.save(att));
            }
        }
        return ResponseEntity.ok(saved);
    }

    // ==================== 1.5. TABLEAU DE BORD FINANCIER & SCOLARITÉ (DASHBOARD) ====================

    @GetMapping("/dashboard/stats/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<FinanceDashboardStatsDto> getFinanceDashboardStats(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {

        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);

        // 1. Fetch all enrollments for this tenant and academic year
        List<StudentEnrollment> enrollments = studentEnrollmentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);

        // 2. Fetch all tuition fees for this tenant and academic year
        List<TuitionFee> allFees = tuitionFeeRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);

        // Map academicLevelId -> list of fees
        Map<UUID, List<TuitionFee>> feesByLevel = allFees.stream()
                .filter(f -> f.getAcademicLevel() != null)
                .collect(Collectors.groupingBy(f -> f.getAcademicLevel().getId()));

        // 3. Fetch all payments for this tenant and academic year
        List<StudentPayment> allPayments = studentPaymentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);

        BigDecimal totalPaid = allPayments.stream()
                .map(p -> p.getAmountPaid() != null ? p.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group enrollments by Classroom
        Map<Classroom, List<StudentEnrollment>> enrollmentsByClass = enrollments.stream()
                .filter(e -> e.getClassroom() != null)
                .collect(Collectors.groupingBy(StudentEnrollment::getClassroom));

        BigDecimal totalExigible = BigDecimal.ZERO;
        List<ClassRecoveryRateDto> classRecoveryRates = new ArrayList<>();

        // Map studentId -> total paid by that student
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

        // 4. Monthly collections
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

        // 5. Payment method breakdown
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

        // 6. Recent payments
        List<StudentPayment> recentPayments = allPayments.stream()
                .sorted(Comparator.comparing(StudentPayment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        return ResponseEntity.ok(FinanceDashboardStatsDto.builder()
                .totalExigible(totalExigible)
                .totalPaid(totalPaid)
                .totalBalance(totalBalance)
                .recoveryRate(Math.round(overallRate * 100.0) / 100.0)
                .monthlyCollections(monthlyCollections)
                .paymentMethodBreakdown(paymentMethodBreakdown)
                .classRecoveryRates(classRecoveryRates)
                .recentPayments(recentPayments)
                .build());
    }

    // ==================== 2. CONFIGURATION DES FRAIS / FEES ====================

    @GetMapping("/fees/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<List<TuitionFee>> getFees(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(tuitionFeeRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId));
    }

    @PostMapping("/fees")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<TuitionFee> saveFee(@RequestBody TuitionFee fee) {
        if (!SecurityUtils.isSuperAdmin()) {
            fee.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(tuitionFeeRepository.save(fee));
    }

    @DeleteMapping("/fees/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deleteFee(@PathVariable UUID id) {
        TuitionFee fee = tuitionFeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Frais de scolarité introuvable"));
        SecurityUtils.assertOwnership(fee.getTenantId());
        tuitionFeeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. REGISTRE DES PAIEMENTS / PAYMENTS ====================

    @GetMapping("/payments/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<List<StudentPayment>> getPayments(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(studentPaymentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<StudentPayment> savePayment(@RequestBody StudentPayment payment) {
        if (!SecurityUtils.isSuperAdmin()) {
            payment.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        // Auto-generate receipt number if missing
        if (payment.getReceiptNumber() == null || payment.getReceiptNumber().trim().isEmpty()) {
            long count = studentPaymentRepository.count() + 1;
            String prefix = "REC-" + LocalDate.now().getYear() + "-";
            payment.setReceiptNumber(prefix + String.format("%05d", count));
        }
        return ResponseEntity.ok(studentPaymentRepository.save(payment));
    }

    @DeleteMapping("/payments/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        StudentPayment payment = studentPaymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
        SecurityUtils.assertOwnership(payment.getTenantId());
        studentPaymentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/payments/{id}/receipt", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<byte[]> generateReceipt(@PathVariable UUID id) {
        byte[] pdfBytes = receiptReportService.generatePaymentReceipt(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"recu_" + id + ".pdf\"")
                .body(pdfBytes);
    }

    // ==================== 3.5. BOURSES & PRISES EN CHARGE (SCHOLARSHIPS & WAIVERS) ====================

    @GetMapping("/scholarships/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<List<StudentScholarship>> getScholarships(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(studentScholarshipRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId));
    }

    @PostMapping("/scholarships")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<StudentScholarship> saveScholarship(@RequestBody StudentScholarship scholarship) {
        if (!SecurityUtils.isSuperAdmin()) {
            scholarship.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(studentScholarshipRepository.save(scholarship));
    }

    @DeleteMapping("/scholarships/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deleteScholarship(@PathVariable UUID id) {
        StudentScholarship scholarship = studentScholarshipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bourse ou prise en charge introuvable"));
        SecurityUtils.assertOwnership(scholarship.getTenantId());
        studentScholarshipRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. LE RELEVÉ FINANCIER DE L'ÉLÈVE / LEDGER ====================

    @GetMapping("/ledger/student/{studentId}/year/{yearId}/level/{levelId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<StudentLedgerDto> getStudentLedger(
            @PathVariable UUID studentId,
            @PathVariable UUID yearId,
            @PathVariable UUID levelId) {

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));

        // Super Admin can see any tenant's ledger — regular users are restricted to their own
        SecurityUtils.assertOwnership(student.getTenantId());

        List<TuitionFee> fees = tuitionFeeRepository
                .findByAcademicLevelIdAndAcademicYearId(levelId, yearId);

        BigDecimal totalGrossExigible = fees.stream()
                .map(TuitionFee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StudentScholarship> scholarships = studentScholarshipRepository
                .findByStudentIdAndAcademicYearId(studentId, yearId);

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

        List<StudentPayment> payments = studentPaymentRepository
                .findByStudentIdAndAcademicYearId(studentId, yearId);

        BigDecimal totalPaid = payments.stream()
                .map(StudentPayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = totalExigible.subtract(totalPaid);

        // --- GÉNÉRATION DYNAMIQUE DU TABLEAU D'AMORTISSEMENT ---
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
                // Tranche unique
                amortizationTable.add(AmortizationInstallmentDto.builder()
                        .feeName(fee.getName())
                        .installmentNumber(1)
                        .totalInstallments(1)
                        .amountDue(totalAmount)
                        .amountPaid(BigDecimal.ZERO)
                        .amountRemaining(totalAmount)
                        .dueDate(LocalDate.now().withMonth(9).withDayOfMonth(15)) // Rentrée scolaire
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

        // Tri chronologique FIFO des tranches théoriques
        amortizationTable.sort(java.util.Comparator.comparing(AmortizationInstallmentDto::getDueDate));

        // Répartition FIFO du total payé
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

        return ResponseEntity.ok(StudentLedgerDto.builder()
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
                .build());
    }

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
