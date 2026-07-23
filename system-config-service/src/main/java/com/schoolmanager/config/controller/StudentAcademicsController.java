package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentEnrollment;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.StudentEnrollmentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la scolarité des élèves (Inscriptions, Classes, Élèves).
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/academics")
@RequiredArgsConstructor
public class StudentAcademicsController {

    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;

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
}
