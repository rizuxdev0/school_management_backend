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

        return ResponseEntity.ok(StudentLedgerDto.builder()
                .studentId(studentId)
                .studentName(student.getLastName() + " " + student.getFirstName())
                .registrationNumber(student.getRegistrationNumber())
                .totalExigible(totalExigible)
                .totalPaid(totalPaid)
                .balance(balance)
                .payments(payments)
                .feesStructure(fees)
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
    }
}
