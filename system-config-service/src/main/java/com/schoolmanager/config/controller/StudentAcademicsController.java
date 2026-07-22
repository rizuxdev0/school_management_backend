package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentEnrollment;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.StudentEnrollmentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion de la scolarité des élèves (Scolarité, Inscriptions, Classes).
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
    public ResponseEntity<List<Classroom>> getClassroomsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(classroomRepository.findByTenantId(tenantId));
    }

    @PostMapping("/classrooms")
    public ResponseEntity<Classroom> saveClassroom(@RequestBody Classroom classroom) {
        return ResponseEntity.ok(classroomRepository.save(classroom));
    }

    @DeleteMapping("/classrooms/{id}")
    public ResponseEntity<Void> deleteClassroom(@PathVariable UUID id) {
        classroomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. ÉLÈVES / STUDENTS ====================

    @GetMapping("/students/tenant/{tenantId}")
    public ResponseEntity<List<Student>> getStudentsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(studentRepository.findByTenantId(tenantId));
    }

    @PostMapping("/students")
    public ResponseEntity<Student> saveStudent(@RequestBody Student student) {
        // Auto-generate a registration number if not provided
        if (student.getRegistrationNumber() == null || student.getRegistrationNumber().trim().isEmpty()) {
            String yearCode = String.valueOf(java.time.LocalDate.now().getYear());
            long count = studentRepository.count() + 1;
            student.setRegistrationNumber("MAT-" + yearCode + "-" + String.format("%04d", count));
        }
        return ResponseEntity.ok(studentRepository.save(student));
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
        studentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. INSCRIPTIONS / ENROLLMENTS ====================

    @GetMapping("/enrollments/tenant/{tenantId}/year/{yearId}")
    public ResponseEntity<List<StudentEnrollment>> getEnrollments(
            @PathVariable UUID tenantId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(studentEnrollmentRepository.findByTenantIdAndAcademicYearId(tenantId, yearId));
    }

    @PostMapping("/enrollments")
    public ResponseEntity<StudentEnrollment> enrollStudent(@RequestBody StudentEnrollment enrollment) {
        // Ensure student has tenantId set
        if (enrollment.getStudent() != null && enrollment.getStudent().getId() != null) {
            Student s = studentRepository.findById(enrollment.getStudent().getId())
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable"));
            enrollment.setStudent(s);
        }
        // Ensure classroom has tenantId set
        if (enrollment.getClassroom() != null && enrollment.getClassroom().getId() != null) {
            Classroom c = classroomRepository.findById(enrollment.getClassroom().getId())
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));
            enrollment.setClassroom(c);
        }
        
        // Check if student is already enrolled in the same academic year
        studentEnrollmentRepository.findByStudentIdAndAcademicYearId(
                enrollment.getStudent().getId(),
                enrollment.getAcademicYearId()
        ).ifPresent(existing -> {
            enrollment.setId(existing.getId()); // overwrite/update
        });

        return ResponseEntity.ok(studentEnrollmentRepository.save(enrollment));
    }

    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable UUID id) {
        studentEnrollmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
