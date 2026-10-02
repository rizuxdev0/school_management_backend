package com.schoolmanager.config.controller;

import com.schoolmanager.config.dto.ImportResultDto;
import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.security.UserPrincipal;
import com.schoolmanager.config.service.AcademicImportExportService;
import com.schoolmanager.config.service.StudentAcademicsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la scolarité des élèves (Inscriptions, Classes, Élèves, Emploi du Temps, Salles & Amphis).
 * Délègue toutes les règles métier et transactions au service StudentAcademicsService.
 */
@RestController
@RequestMapping("/api/v1/system/academics")
@RequiredArgsConstructor
@Tag(name = "Gestion Académique", description = "Endpoints pour gérer la scolarité des élèves, inscriptions, classes, emplois du temps et infrastructures.")
public class StudentAcademicsController {

    private final StudentAcademicsService studentAcademicsService;
    private final AcademicImportExportService academicImportExportService;

    // ==================== 1. CLASSES / CLASSROOMS ====================

    @GetMapping("/classrooms/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Classroom>> getClassroomsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(studentAcademicsService.getClassroomsByTenant(tenantId));
    }

    @PostMapping("/classrooms")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Classroom> saveClassroom(@RequestBody Classroom classroom) {
        return ResponseEntity.ok(studentAcademicsService.saveClassroom(classroom));
    }

    @DeleteMapping("/classrooms/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteClassroom(@PathVariable UUID id) {
        studentAcademicsService.deleteClassroom(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. ÉLÈVES / STUDENTS ====================

    @Operation(summary = "Lister les élèves", description = "Récupère tous les élèves enregistrés pour le tenant spécifié.")
    @GetMapping("/students/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Student>> getStudentsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(studentAcademicsService.getStudentsByTenant(tenantId));
    }

    @Operation(summary = "Enregistrer ou modifier un élève", description = "Crée ou met à jour les informations d'un élève avec validation stricte (JSR-380).")
    @PostMapping("/students")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Student> saveStudent(@Valid @RequestBody Student student) {
        return ResponseEntity.ok(studentAcademicsService.saveStudent(student));
    }

    @Operation(summary = "Supprimer un élève", description = "Supprime un profil d'élève par son UUID.")
    @DeleteMapping("/students/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
        studentAcademicsService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. INSCRIPTIONS / ENROLLMENTS ====================

    @GetMapping("/enrollments/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<StudentEnrollment>> getEnrollments(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(studentAcademicsService.getEnrollments(tenantId, yearId));
    }

    @GetMapping("/enrollments/classroom/{classroomId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<StudentEnrollment>> getEnrollmentsByClassroom(
            @PathVariable UUID classroomId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(studentAcademicsService.getEnrollmentsByClassroom(classroomId, yearId));
    }

    @PostMapping("/enrollments")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<StudentEnrollment> enrollStudent(@RequestBody StudentEnrollment enrollment) {
        return ResponseEntity.ok(studentAcademicsService.enrollStudent(enrollment));
    }

    @DeleteMapping("/enrollments/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable UUID id) {
        studentAcademicsService.deleteEnrollment(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. EMPLOI DU TEMPS / TIMETABLE ====================

    @GetMapping("/timetable/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<TimetableSlot>> getTimetableByYear(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(studentAcademicsService.getTimetableByYear(tenantId, yearId));
    }

    @GetMapping("/timetable/tenant/{tenantId}/year/{yearId}/classroom/{classroomId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<TimetableSlot>> getTimetableByClassroom(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId,
            @PathVariable UUID classroomId) {
        return ResponseEntity.ok(studentAcademicsService.getTimetableByClassroom(tenantId, yearId, classroomId));
    }

    @PostMapping("/timetable")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<TimetableSlot> saveTimetableSlot(@RequestBody TimetableSlot slot) {
        return ResponseEntity.ok(studentAcademicsService.saveTimetableSlot(slot));
    }

    @DeleteMapping("/timetable/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteTimetableSlot(@PathVariable UUID id) {
        studentAcademicsService.deleteTimetableSlot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/timetable/tenant/{tenantId}/year/{yearId}/classroom/{classroomId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadTimetablePdf(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId,
            @PathVariable UUID classroomId) {
        byte[] pdfBytes = studentAcademicsService.downloadTimetablePdf(tenantId, yearId, classroomId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timetable_" + classroomId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== 5. SALLES & AMPHITHEATRES ====================

    @GetMapping("/rooms/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Room>> getRoomsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(studentAcademicsService.getRoomsByTenant(tenantId));
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Room> saveRoom(@RequestBody Room room) {
        return ResponseEntity.ok(studentAcademicsService.saveRoom(room));
    }

    @DeleteMapping("/rooms/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        studentAcademicsService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/tenant/{tenantId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadStudentsPdf(@PathVariable UUID tenantId) {
        byte[] pdfBytes = studentAcademicsService.downloadStudentsPdf(tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students_list_" + tenantId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/classrooms/tenant/{tenantId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadClassroomsPdf(@PathVariable UUID tenantId) {
        byte[] pdfBytes = studentAcademicsService.downloadClassroomsPdf(tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"classrooms_list_" + tenantId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/rooms/tenant/{tenantId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadRoomsPdf(@PathVariable UUID tenantId) {
        byte[] pdfBytes = studentAcademicsService.downloadRoomsPdf(tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rooms_list_" + tenantId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== EXPORTS TEMPLATES & IMPORTS EXCEL ====================

    @GetMapping("/classrooms/template/excel")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadClassroomTemplate() {
        byte[] excelBytes = academicImportExportService.generateClassroomTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_import_classes.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @PostMapping(value = "/classrooms/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<ImportResultDto> importClassrooms(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tenantId") UUID tenantId) throws IOException {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        ImportResultDto result = academicImportExportService.importClassroomsExcel(jwtTenantId, file.getInputStream(), file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/students/template/excel")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadStudentTemplate() {
        byte[] excelBytes = academicImportExportService.generateStudentTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_import_eleves.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @PostMapping(value = "/students/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<ImportResultDto> importStudents(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tenantId") UUID tenantId) throws IOException {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        ImportResultDto result = academicImportExportService.importStudentsExcel(jwtTenantId, file.getInputStream(), file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rooms/template/excel")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadRoomTemplate() {
        byte[] excelBytes = academicImportExportService.generateRoomTemplateExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_import_salles.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @PostMapping(value = "/rooms/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<ImportResultDto> importRooms(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tenantId") UUID tenantId) throws IOException {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        ImportResultDto result = academicImportExportService.importRoomsExcel(jwtTenantId, file.getInputStream(), file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/students/{studentId}/certificate")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW') or hasRole('PARENT')")
    public ResponseEntity<byte[]> getSchoolCertificate(
            @PathVariable UUID studentId,
            @RequestParam(value = "lang", defaultValue = "fr") String lang) {
        boolean isParent = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PARENT"));
        UserPrincipal principal = isParent ? SecurityUtils.getCurrentPrincipal() : null;
        String parentPhone = principal != null ? principal.getPhoneNumber() : null;
        String parentEmail = principal != null ? principal.getEmail() : null;

        byte[] pdf = studentAcademicsService.getSchoolCertificate(studentId, lang, isParent, parentPhone, parentEmail);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificat_scolarite.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ==================== LIAISON PARENT → ENFANTS ====================

    record LinkParentRequest(String parentPhone, List<UUID> studentIds) {}

    @PostMapping("/students/link-parent")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> linkParentToStudents(@RequestBody LinkParentRequest request) {
        studentAcademicsService.linkParentToStudents(request.parentPhone(), request.studentIds());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/by-parent-phone")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Student>> getStudentsByParentPhone(@RequestParam String parentPhone) {
        return ResponseEntity.ok(studentAcademicsService.getStudentsByParentPhone(parentPhone));
    }
}
