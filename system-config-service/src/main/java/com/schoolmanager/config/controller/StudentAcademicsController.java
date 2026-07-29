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

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la scolarité des élèves (Inscriptions, Classes, Élèves, Emploi du Temps, Salles & Amphis).
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/academics")
@RequiredArgsConstructor
public class StudentAcademicsController {

    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final RoomRepository roomRepository;
    private final TimetableReportService timetableReportService;
    private final AcademicReportService academicReportService;

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

    @GetMapping("/students/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Student>> getStudentsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(studentRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/students")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Student> saveStudent(@RequestBody Student student) {
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
        if (!SecurityUtils.isSuperAdmin()) {
            slot.setTenantId(SecurityUtils.getCurrentTenantId());
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
}

