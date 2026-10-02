package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.service.AttendanceService;
import com.schoolmanager.config.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour les présences/absences et la gestion financière (frais, paiements, relevé, dashboard).
 * Délègue les règles métier et transactions aux services dédiés AttendanceService et FinanceService.
 */
@RestController
@RequestMapping("/api/v1/system/academics-finance")
@RequiredArgsConstructor
public class AttendanceAndFinanceController {

    private final AttendanceService attendanceService;
    private final FinanceService financeService;

    // ==================== 1. ABSENCES & ASSIDUITÉ ====================

    @GetMapping("/attendance/classroom/{classroomId}/date/{date}")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ResponseEntity<List<Attendance>> getAttendance(
            @PathVariable UUID classroomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getAttendanceByClassroomAndDate(classroomId, date));
    }

    @GetMapping("/attendance/classroom/{classroomId}/date/{date}/pdf")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ResponseEntity<byte[]> downloadAttendanceListPdf(
            @PathVariable UUID classroomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        byte[] pdf = attendanceService.generateAttendanceListPdf(classroomId, date);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "liste_presence_" + date + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/attendance/classroom/{classroomId}/range/pdf")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ResponseEntity<byte[]> downloadAttendanceListRangePdf(
            @PathVariable UUID classroomId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        byte[] pdf = attendanceService.generateAttendanceListRangePdf(classroomId, startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "liste_presence_" + startDate + "_au_" + endDate + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping("/attendance/save-all")
    @PreAuthorize("hasAuthority('ATTENDANCE_EDIT')")
    public ResponseEntity<List<Attendance>> saveAttendance(@RequestBody List<Attendance> attendanceList) {
        return ResponseEntity.ok(attendanceService.saveAttendanceList(attendanceList));
    }

    // ==================== 2. TABLEAU DE BORD FINANCIER ====================

    @GetMapping("/dashboard/stats/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<FinanceService.FinanceDashboardStatsDto> getFinanceDashboardStats(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(financeService.getFinanceDashboardStats(tenantId, yearId));
    }

    // ==================== 3. CONFIGURATION DES FRAIS ====================

    @GetMapping("/fees/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<List<TuitionFee>> getFees(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(financeService.getFees(tenantId, yearId));
    }

    @PostMapping("/fees")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<TuitionFee> saveFee(@RequestBody TuitionFee fee) {
        return ResponseEntity.ok(financeService.saveFee(fee));
    }

    @DeleteMapping("/fees/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deleteFee(@PathVariable UUID id) {
        financeService.deleteFee(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. REGISTRE DES PAIEMENTS ====================

    @GetMapping("/payments/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<List<StudentPayment>> getPayments(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(financeService.getPayments(tenantId, yearId));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<StudentPayment> savePayment(@RequestBody StudentPayment payment) {
        return ResponseEntity.ok(financeService.savePayment(payment));
    }

    @DeleteMapping("/payments/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        financeService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/payments/{id}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<byte[]> generateReceipt(@PathVariable UUID id) {
        byte[] pdfBytes = financeService.generatePaymentReceiptPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"recu_" + id + ".pdf\"")
                .body(pdfBytes);
    }

    // ==================== 5. BOURSES & PRISES EN CHARGE ====================

    @GetMapping("/scholarships/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<List<StudentScholarship>> getScholarships(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(financeService.getScholarships(tenantId, yearId));
    }

    @PostMapping("/scholarships")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<StudentScholarship> saveScholarship(@RequestBody StudentScholarship scholarship) {
        return ResponseEntity.ok(financeService.saveScholarship(scholarship));
    }

    @DeleteMapping("/scholarships/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deleteScholarship(@PathVariable UUID id) {
        financeService.deleteScholarship(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 6. RELEVÉ FINANCIER ÉLÈVE ====================

    @GetMapping("/ledger/student/{studentId}/year/{yearId}/level/{levelId}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<FinanceService.StudentLedgerDto> getStudentLedger(
            @PathVariable UUID studentId,
            @PathVariable UUID yearId,
            @PathVariable UUID levelId) {
        return ResponseEntity.ok(financeService.getStudentLedger(studentId, yearId, levelId));
    }
}
