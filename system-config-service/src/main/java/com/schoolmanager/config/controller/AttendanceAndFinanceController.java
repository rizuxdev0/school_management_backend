package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Attendance;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentPayment;
import com.schoolmanager.config.entity.TuitionFee;
import com.schoolmanager.config.repository.AttendanceRepository;
import com.schoolmanager.config.repository.StudentPaymentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.repository.TuitionFeeRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public ResponseEntity<List<Attendance>> getAttendance(
            @PathVariable UUID classroomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceRepository.findByClassroomIdAndAttendanceDate(classroomId, date));
    }

    @PostMapping("/attendance/save-all")
    public ResponseEntity<List<Attendance>> saveAttendance(@RequestBody List<Attendance> attendanceList) {
        List<Attendance> saved = new ArrayList<>();
        for (Attendance att : attendanceList) {
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
    public ResponseEntity<List<TuitionFee>> getFees(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(tuitionFeeRepository.findByTenantIdAndAcademicYearId(tenantId, yearId));
    }

    @PostMapping("/fees")
    public ResponseEntity<TuitionFee> saveFee(@RequestBody TuitionFee fee) {
        return ResponseEntity.ok(tuitionFeeRepository.save(fee));
    }

    @DeleteMapping("/fees/{id}")
    public ResponseEntity<Void> deleteFee(@PathVariable UUID id) {
        tuitionFeeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. REGISTRE DES PAIEMENTS / PAYMENTS ====================

    @GetMapping("/payments/tenant/{tenantId}/year/{yearId}")
    public ResponseEntity<List<StudentPayment>> getPayments(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(studentPaymentRepository.findByTenantIdAndAcademicYearId(tenantId, yearId));
    }

    @PostMapping("/payments")
    public ResponseEntity<StudentPayment> savePayment(@RequestBody StudentPayment payment) {
        // Auto-generate receipt number if missing
        if (payment.getReceiptNumber() == null || payment.getReceiptNumber().trim().isEmpty()) {
            long count = studentPaymentRepository.count() + 1;
            String prefix = "REC-" + LocalDate.now().getYear() + "-";
            payment.setReceiptNumber(prefix + String.format("%05d", count));
        }
        return ResponseEntity.ok(studentPaymentRepository.save(payment));
    }

    @DeleteMapping("/payments/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        studentPaymentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. LE RELEVÉ FINANCIER DE L'ÉLÈVE / LEDGER ====================

    @GetMapping("/ledger/student/{studentId}/year/{yearId}/level/{levelId}")
    public ResponseEntity<StudentLedgerDto> getStudentLedger(
            @PathVariable UUID studentId,
            @PathVariable UUID yearId,
            @PathVariable UUID levelId) {

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable"));

        // Fetch tuition fees for this student's level
        List<TuitionFee> fees = tuitionFeeRepository
                .findByAcademicLevelIdAndAcademicYearId(levelId, yearId);

        BigDecimal totalExigible = fees.stream()
                .map(TuitionFee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fetch student's payments
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
