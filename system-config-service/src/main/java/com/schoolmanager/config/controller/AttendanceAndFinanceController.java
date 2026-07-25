package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Attendance;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentPayment;
import com.schoolmanager.config.entity.TuitionFee;
import com.schoolmanager.config.repository.AttendanceRepository;
import com.schoolmanager.config.repository.StudentPaymentRepository;
import com.schoolmanager.config.repository.StudentRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrôleur REST pour les absences et la finance (frais, paiements, relevé).
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
    private final com.schoolmanager.config.service.ReceiptReportService receiptReportService;

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

        BigDecimal totalExigible = fees.stream()
                .map(TuitionFee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                .totalExigible(totalExigible)
                .totalPaid(totalPaid)
                .balance(balance)
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
        private BigDecimal totalExigible;
        private BigDecimal totalPaid;
        private BigDecimal balance;
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
}
