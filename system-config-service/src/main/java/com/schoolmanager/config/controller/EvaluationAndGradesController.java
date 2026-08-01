package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Evaluation;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentEnrollment;
import com.schoolmanager.config.entity.StudentGrade;
import com.schoolmanager.config.entity.Subject;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.EvaluationRepository;
import com.schoolmanager.config.repository.StudentEnrollmentRepository;
import com.schoolmanager.config.repository.StudentGradeRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.repository.SubjectRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Contrôleur REST pour les évaluations, notes et bulletins scolaires.
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/evaluations")
@RequiredArgsConstructor
public class EvaluationAndGradesController {

    private final SubjectRepository subjectRepository;
    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final StudentRepository studentRepository;

    private final com.schoolmanager.config.service.BulletinCalculationService bulletinCalculationService;
    private final com.schoolmanager.config.service.NotificationService notificationService;

    // ==================== 1. MATIÈRES / SUBJECTS ====================

    @GetMapping("/subjects/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW') or hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Subject>> getSubjects(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(subjectRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<Subject> saveSubject(@RequestBody Subject subject) {
        if (!SecurityUtils.isSuperAdmin()) {
            subject.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(subjectRepository.save(subject));
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<Void> deleteSubject(@PathVariable UUID id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Matière introuvable"));
        SecurityUtils.assertOwnership(subject.getTenantId());
        subjectRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. ÉVALUATIONS / ASSESSMENTS ====================

    @GetMapping("/tenant/{tenantId}/period/{periodId}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW')")
    public ResponseEntity<List<Evaluation>> getEvaluationsByPeriod(
            @PathVariable UUID tenantId,
            @PathVariable UUID periodId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(evaluationRepository.findByTenantIdAndAcademicPeriodId(jwtTenantId, periodId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<Evaluation> saveEvaluation(@RequestBody Evaluation evaluation) {
        if (!SecurityUtils.isSuperAdmin()) {
            evaluation.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(evaluationRepository.save(evaluation));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<Void> deleteEvaluation(@PathVariable UUID id) {
        Evaluation evaluation = evaluationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Évaluation introuvable"));
        SecurityUtils.assertOwnership(evaluation.getTenantId());
        evaluationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. SAISIE DES NOTES / GRADES ====================

    @GetMapping("/grades/evaluation/{evaluationId}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW')")
    public ResponseEntity<List<StudentGrade>> getGradesForEvaluation(@PathVariable UUID evaluationId) {
        return ResponseEntity.ok(studentGradeRepository.findByEvaluationId(evaluationId));
    }

    @PostMapping("/grades")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<List<StudentGrade>> saveGrades(@RequestBody List<StudentGrade> grades) {
        List<StudentGrade> saved = new ArrayList<>();
        for (StudentGrade grade : grades) {
            if (!SecurityUtils.isSuperAdmin()) {
                grade.setTenantId(SecurityUtils.getCurrentTenantId());
            }

            // Resolve transient Student entity to prevent TransientPropertyValueException
            if (grade.getStudent() != null && grade.getStudent().getId() != null) {
                Student s = studentRepository.findById(grade.getStudent().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
                SecurityUtils.assertOwnership(s.getTenantId());
                grade.setStudent(s);
            }

            // Resolve transient Evaluation entity to prevent TransientPropertyValueException
            if (grade.getEvaluation() != null && grade.getEvaluation().getId() != null) {
                Evaluation eval = evaluationRepository.findById(grade.getEvaluation().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Évaluation introuvable"));
                SecurityUtils.assertOwnership(eval.getTenantId());
                grade.setEvaluation(eval);
            }

            Optional<StudentGrade> existing = studentGradeRepository.findByStudentIdAndEvaluationId(
                    grade.getStudent().getId(),
                    grade.getEvaluation().getId()
            );
            StudentGrade savedGrade;
            if (existing.isPresent()) {
                StudentGrade e = existing.get();
                e.setScore(grade.getScore());
                e.setRemarks(grade.getRemarks());
                savedGrade = studentGradeRepository.save(e);
            } else {
                savedGrade = studentGradeRepository.save(grade);
            }
            saved.add(savedGrade);

            // Déclencher une notification de publication de note
            try {
                UUID tenantId = savedGrade.getTenantId();
                Student s = savedGrade.getStudent();
                Evaluation eval = savedGrade.getEvaluation();
                String subjectName = eval.getSubject() != null ? eval.getSubject().getNameFr() : "une matière";
                String title = "Nouvelle note disponible";
                String message = String.format("La note de %s %s pour l'évaluation '%s' en %s a été publiée : %s/%s.",
                        s.getFirstName(), s.getLastName(), eval.getTitle(), subjectName, savedGrade.getScore(), eval.getMaxScore());

                // Envoyer la notification au parent via son téléphone et à l'élève via son email
                notificationService.sendNotification(tenantId, null, s.getParentPhone(), s.getEmail(), title, message, "GRADE");
            } catch (Exception ex) {
                // Ignore failure to ensure the grade save transaction is not aborted
            }
        }
        return ResponseEntity.ok(saved);
    }

    // ==================== 4. CALCUL DU BULLETIN / REPORT CARD ====================

    @GetMapping("/reports/classroom/{classroomId}/period/{periodId}/year/{yearId}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW')")
    public ResponseEntity<List<StudentReportDto>> calculateReportCards(
            @PathVariable UUID classroomId,
            @PathVariable UUID periodId,
            @PathVariable UUID yearId) {

        return ResponseEntity.ok(bulletinCalculationService.calculateReports(classroomId, periodId, yearId));
    }

    // Helper classes for Report Card computation

    @Data
    @Builder
    public static class StudentReportDto {
        private UUID studentId;
        private String studentName;
        private String registrationNumber;
        private List<SubjectAverageDto> subjectsAverages;
        private BigDecimal globalAverage;
        private int rank;
    }

    @Data
    @Builder
    public static class SubjectAverageDto {
        private UUID subjectId;
        private String subjectCode;
        private String subjectNameFr;
        private BigDecimal coefficient;
        private BigDecimal average;
        private String remarks;
    }
}
