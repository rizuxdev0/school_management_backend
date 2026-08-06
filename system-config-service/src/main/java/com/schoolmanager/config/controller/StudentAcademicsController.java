package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Room;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentEnrollment;
import com.schoolmanager.config.entity.TimetableSlot;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.RoomRepository;
import com.schoolmanager.config.repository.StudentEnrollmentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.repository.TimetableSlotRepository;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.service.AcademicReportService;
import com.schoolmanager.config.service.TimetableReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import com.schoolmanager.config.dto.ImportResultDto;
import com.schoolmanager.config.service.AcademicImportExportService;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la scolarité des élèves (Inscriptions, Classes, Élèves, Emploi du Temps, Salles & Amphis).
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/academics")
@RequiredArgsConstructor
@Tag(name = "Gestion Académique", description = "Endpoints pour gérer la scolarité des élèves, inscriptions, classes, emplois du temps et infrastructures.")
public class StudentAcademicsController {

    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final RoomRepository roomRepository;
    private final TimetableReportService timetableReportService;
    private final AcademicReportService academicReportService;
    private final AcademicImportExportService academicImportExportService;

    // ==================== 1. CLASSES / CLASSROOMS ====================

    @GetMapping("/classrooms/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Classroom>> getClassroomsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(classroomRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/classrooms")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Classroom> saveClassroom(@RequestBody Classroom classroom) {
        if (!SecurityUtils.isSuperAdmin()) {
            classroom.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(classroomRepository.save(classroom));
    }

    @DeleteMapping("/classrooms/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteClassroom(@PathVariable UUID id) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        SecurityUtils.assertOwnership(classroom.getTenantId());
        classroomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. ÉLÈVES / STUDENTS ====================

    @Operation(summary = "Lister les élèves", description = "Récupère tous les élèves enregistrés pour le tenant spécifié.")
    @GetMapping("/students/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Student>> getStudentsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(studentRepository.findByTenantId(jwtTenantId));
    }

    @Operation(summary = "Enregistrer ou modifier un élève", description = "Crée ou met à jour les informations d'un élève avec validation stricte (JSR-380).")
    @PostMapping("/students")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Student> saveStudent(@Valid @RequestBody Student student) {
        if (!SecurityUtils.isSuperAdmin()) {
            student.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        // Auto-generate a registration number if not provided
        if (student.getRegistrationNumber() == null || student.getRegistrationNumber().trim().isEmpty()) {
            String yearCode = String.valueOf(java.time.LocalDate.now().getYear());
            long count = studentRepository.count() + 1;
            student.setRegistrationNumber("MAT-" + yearCode + "-" + String.format("%04d", count));
        }
        return ResponseEntity.ok(studentRepository.save(student));
    }

    @Operation(summary = "Supprimer un élève", description = "Supprime un profil d'élève par son UUID.")
    @DeleteMapping("/students/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
        SecurityUtils.assertOwnership(student.getTenantId());
        studentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. INSCRIPTIONS / ENROLLMENTS ====================

    @GetMapping("/enrollments/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<StudentEnrollment>> getEnrollments(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(studentEnrollmentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId));
    }

    @GetMapping("/enrollments/classroom/{classroomId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<StudentEnrollment>> getEnrollmentsByClassroom(
            @PathVariable UUID classroomId,
            @PathVariable UUID yearId) {
        Classroom c = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        SecurityUtils.assertOwnership(c.getTenantId());
        return ResponseEntity.ok(studentEnrollmentRepository.findByClassroomIdAndAcademicYearId(classroomId, yearId));
    }

    @PostMapping("/enrollments")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<StudentEnrollment> enrollStudent(@RequestBody StudentEnrollment enrollment) {
        if (!SecurityUtils.isSuperAdmin()) {
            enrollment.setTenantId(SecurityUtils.getCurrentTenantId());
        }

        // Ensure student belongs to the same tenant
        if (enrollment.getStudent() != null && enrollment.getStudent().getId() != null) {
            Student s = studentRepository.findById(enrollment.getStudent().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            SecurityUtils.assertOwnership(s.getTenantId());
            enrollment.setStudent(s);
        }
        // Ensure classroom belongs to the same tenant
        if (enrollment.getClassroom() != null && enrollment.getClassroom().getId() != null) {
            Classroom c = classroomRepository.findById(enrollment.getClassroom().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
            SecurityUtils.assertOwnership(c.getTenantId());
            enrollment.setClassroom(c);
        }

        // Check if student is already enrolled in the same academic year (upsert)
        studentEnrollmentRepository.findByStudentIdAndAcademicYearId(
                enrollment.getStudent().getId(),
                enrollment.getAcademicYearId()
        ).ifPresent(existing -> {
            enrollment.setId(existing.getId());
        });

        return ResponseEntity.ok(studentEnrollmentRepository.save(enrollment));
    }

    @DeleteMapping("/enrollments/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable UUID id) {
        StudentEnrollment enrollment = studentEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscription introuvable"));
        SecurityUtils.assertOwnership(enrollment.getTenantId());
        studentEnrollmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. EMPLOI DU TEMPS / TIMETABLE ====================

    @GetMapping("/timetable/tenant/{tenantId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<TimetableSlot>> getTimetableByYear(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(timetableSlotRepository.findByTenantIdAndAcademicYearIdOrderByDayOfWeekAscStartTimeAsc(jwtTenantId, yearId));
    }

    @GetMapping("/timetable/tenant/{tenantId}/year/{yearId}/classroom/{classroomId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<TimetableSlot>> getTimetableByClassroom(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId,
            @PathVariable UUID classroomId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(timetableSlotRepository.findByTenantIdAndAcademicYearIdAndClassroomIdOrderByDayOfWeekAscStartTimeAsc(jwtTenantId, yearId, classroomId));
    }

    @PostMapping("/timetable")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<TimetableSlot> saveTimetableSlot(@RequestBody TimetableSlot slot) {
        UUID tenantId = SecurityUtils.isSuperAdmin() ? slot.getTenantId() : SecurityUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        slot.setTenantId(tenantId);

        if (slot.getStartTime() == null || slot.getEndTime() == null || slot.getStartTime().compareTo(slot.getEndTime()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'heure de début doit être antérieure à l'heure de fin.");
        }

        List<TimetableSlot> overlapping = timetableSlotRepository.findOverlappingSlots(
                slot.getTenantId(),
                slot.getAcademicYearId(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getId()
        );

        for (TimetableSlot match : overlapping) {
            if (slot.getClassroomId().equals(match.getClassroomId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("La classe a déjà un cours programmé (%s) de %s à %s.",
                        match.getSubjectNameFr(), match.getStartTime(), match.getEndTime()));
            }

            if (slot.getTeacherName() != null && !slot.getTeacherName().trim().isEmpty() &&
                match.getTeacherName() != null && slot.getTeacherName().equalsIgnoreCase(match.getTeacherName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("L'enseignant %s est déjà programmé pour un cours de %s à %s.",
                        slot.getTeacherName(), match.getStartTime(), match.getEndTime()));
            }

            if (slot.getRoom() != null && !slot.getRoom().trim().isEmpty() &&
                match.getRoom() != null && slot.getRoom().equalsIgnoreCase(match.getRoom())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("La salle %s est déjà occupée de %s à %s par un autre cours.",
                        slot.getRoom(), match.getStartTime(), match.getEndTime()));
            }
        }

        return ResponseEntity.ok(timetableSlotRepository.save(slot));
    }

    @DeleteMapping("/timetable/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteTimetableSlot(@PathVariable UUID id) {
        TimetableSlot slot = timetableSlotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Créneau horaire introuvable"));
        SecurityUtils.assertOwnership(slot.getTenantId());
        timetableSlotRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/timetable/tenant/{tenantId}/year/{yearId}/classroom/{classroomId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadTimetablePdf(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId,
            @PathVariable UUID classroomId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        byte[] pdfBytes = timetableReportService.generateClassroomTimetablePdf(jwtTenantId, yearId, classroomId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timetable_" + classroomId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== 5. SALLES & AMPHITHEATRES ====================

    @GetMapping("/rooms/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Room>> getRoomsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        List<Room> rooms = roomRepository.findByTenantId(jwtTenantId);
        if (rooms.isEmpty()) {
            rooms = List.of(
                Room.builder().tenantId(jwtTenantId).code("S-101").name("Salle 101 (RDC)").capacity(40).roomType("CLASSROOM").building("Bâtiment Principal").isActive(true).build(),
                Room.builder().tenantId(jwtTenantId).code("S-102").name("Salle 102 (RDC)").capacity(40).roomType("CLASSROOM").building("Bâtiment Principal").isActive(true).build(),
                Room.builder().tenantId(jwtTenantId).code("AMPHI-A").name("Amphithéâtre A (Central)").capacity(150).roomType("AMPHITHEATER").building("Bâtiment A").isActive(true).build(),
                Room.builder().tenantId(jwtTenantId).code("LAB-INFO").name("Laboratoire Informatique 1").capacity(30).roomType("LABORATORY").building("Bâtiment des Sciences").isActive(true).build()
            );
            roomRepository.saveAll(rooms);
            rooms = roomRepository.findByTenantId(jwtTenantId);
        }
        return ResponseEntity.ok(rooms);
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Room> saveRoom(@RequestBody Room room) {
        if (!SecurityUtils.isSuperAdmin()) {
            room.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(roomRepository.save(room));
    }

    @DeleteMapping("/rooms/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salle / Amphi introuvable"));
        SecurityUtils.assertOwnership(room.getTenantId());
        roomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/tenant/{tenantId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadStudentsPdf(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        byte[] pdfBytes = academicReportService.generateStudentsListPdf(jwtTenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students_list_" + jwtTenantId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/classrooms/tenant/{tenantId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadClassroomsPdf(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        byte[] pdfBytes = academicReportService.generateClassroomsListPdf(jwtTenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"classrooms_list_" + jwtTenantId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/rooms/tenant/{tenantId}/pdf")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<byte[]> downloadRoomsPdf(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        byte[] pdfBytes = academicReportService.generateRoomsListPdf(jwtTenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rooms_list_" + jwtTenantId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== EXPORTS EXEMPLAIRES (TEMPLATES) & IMPORTS EXCEL/CSV ====================

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
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        
        // Validation anti-IDOR pour les parents
        boolean isParent = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PARENT"));
        if (isParent) {
            com.schoolmanager.config.security.UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            boolean phoneMatches = student.getParentPhone() != null && student.getParentPhone().equals(principal.getPhoneNumber());
            boolean emailMatches = student.getEmail() != null && student.getEmail().equals(principal.getEmail());
            if (!phoneMatches && !emailMatches) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès non autorisé au certificat de cet élève");
            }
        }

        byte[] pdf = academicReportService.generateSchoolCertificatePdf(tenantId, studentId, lang);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificat_scolarite.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ==================== 7. LIAISON PARENT → ENFANTS ====================

    /**
     * DTO d'entrée pour la liaison d'un parent à ses enfants.
     */
    record LinkParentRequest(String parentPhone, List<UUID> studentIds) {}

    /**
     * Lie un compte parent (via son numéro de téléphone) à une liste d'élèves.
     * Met à jour le champ parentPhone de chaque élève sélectionné.
     * Réinitialise également le parentPhone des anciens enfants qui ne sont plus dans la liste,
     * afin d'éviter tout lien résiduel (ghost links).
     *
     * @param request DTO contenant le téléphone du parent et les IDs des élèves enfants
     */
    @PostMapping("/students/link-parent")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> linkParentToStudents(@RequestBody LinkParentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        // 1. Retirer ce numéro de téléphone des anciens enfants non retenus dans la nouvelle sélection
        List<Student> previousLinked = studentRepository.findByTenantIdAndParentPhone(tenantId, request.parentPhone());
        for (Student s : previousLinked) {
            if (!request.studentIds().contains(s.getId())) {
                s.setParentPhone(null);
                studentRepository.save(s);
            }
        }

        // 2. Assigner le numéro de téléphone aux nouveaux enfants sélectionnés
        for (UUID studentId : request.studentIds()) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable : " + studentId));
            SecurityUtils.assertOwnership(student.getTenantId());
            student.setParentPhone(request.parentPhone());
            studentRepository.save(student);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Récupère les élèves actuellement liés à un numéro de téléphone parent donné.
     * Permet d'initialiser le formulaire d'édition d'un compte parent avec ses enfants déjà sélectionnés.
     *
     * @param parentPhone Le numéro de téléphone du parent
     */
    @GetMapping("/students/by-parent-phone")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Student>> getStudentsByParentPhone(
            @RequestParam String parentPhone) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(studentRepository.findByTenantIdAndParentPhone(tenantId, parentPhone));
    }

}
