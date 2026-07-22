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
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/system/evaluations")
@RequiredArgsConstructor
public class EvaluationAndGradesController {

    private final SubjectRepository subjectRepository;
    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final StudentRepository studentRepository;

    // ==================== 1. MATIÈRES / SUBJECTS ====================

    @GetMapping("/subjects/tenant/{tenantId}")
    public ResponseEntity<List<Subject>> getSubjects(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(subjectRepository.findByTenantId(tenantId));
    }

    @PostMapping("/subjects")
    public ResponseEntity<Subject> saveSubject(@RequestBody Subject subject) {
        return ResponseEntity.ok(subjectRepository.save(subject));
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable UUID id) {
        subjectRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. ÉVALUATIONS / ASSESSMENTS ====================

    @GetMapping("/tenant/{tenantId}/period/{periodId}")
    public ResponseEntity<List<Evaluation>> getEvaluationsByPeriod(
            @PathVariable UUID tenantId,
            @PathVariable UUID periodId) {
        return ResponseEntity.ok(evaluationRepository.findByTenantIdAndAcademicPeriodId(tenantId, periodId));
    }

    @PostMapping
    public ResponseEntity<Evaluation> saveEvaluation(@RequestBody Evaluation evaluation) {
        return ResponseEntity.ok(evaluationRepository.save(evaluation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvaluation(@PathVariable UUID id) {
        evaluationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. SAISIE DES NOTES / GRADES ====================

    @GetMapping("/grades/evaluation/{evaluationId}")
    public ResponseEntity<List<StudentGrade>> getGradesForEvaluation(@PathVariable UUID evaluationId) {
        return ResponseEntity.ok(studentGradeRepository.findByEvaluationId(evaluationId));
    }

    @PostMapping("/grades")
    public ResponseEntity<List<StudentGrade>> saveGrades(@RequestBody List<StudentGrade> grades) {
        List<StudentGrade> saved = new ArrayList<>();
        for (StudentGrade grade : grades) {
            // Check unique constraint to update if already exists
            Optional<StudentGrade> existing = studentGradeRepository.findByStudentIdAndEvaluationId(
                    grade.getStudent().getId(),
                    grade.getEvaluation().getId()
            );
            if (existing.isPresent()) {
                StudentGrade e = existing.get();
                e.setScore(grade.getScore());
                e.setRemarks(grade.getRemarks());
                saved.add(studentGradeRepository.save(e));
            } else {
                saved.add(studentGradeRepository.save(grade));
            }
        }
        return ResponseEntity.ok(saved);
    }

    // ==================== 4. CALCUL DU BULLETIN / REPORT CARD ====================

    @GetMapping("/reports/classroom/{classroomId}/period/{periodId}/year/{yearId}")
    public ResponseEntity<List<StudentReportDto>> calculateReportCards(
            @PathVariable UUID classroomId,
            @PathVariable UUID periodId,
            @PathVariable UUID yearId) {

        // 1. Get all students enrolled in this classroom
        List<StudentEnrollment> enrollments = studentEnrollmentRepository
                .findByClassroomIdAndAcademicYearId(classroomId, yearId);

        // 2. Get all evaluations in this class for the period
        List<Evaluation> evals = evaluationRepository
                .findByClassroomIdAndAcademicPeriodId(classroomId, periodId);

        List<StudentReportDto> reports = new ArrayList<>();

        for (StudentEnrollment enr : enrollments) {
            Student student = enr.getStudent();
            Map<UUID, SubjectGradesTracker> subjectMap = new HashMap<>();

            // 3. Gather student's grades for all evaluations in the period
            for (Evaluation ev : evals) {
                Optional<StudentGrade> gradeOpt = studentGradeRepository
                        .findByStudentIdAndEvaluationId(student.getId(), ev.getId());

                if (gradeOpt.isPresent()) {
                    Subject sub = ev.getSubject();
                    subjectMap.computeIfAbsent(sub.getId(), id -> new SubjectGradesTracker(sub))
                              .addGrade(gradeOpt.get().getScore(), ev.getWeight(), ev.getMaxScore());
                }
            }

            // 4. Calculate Subject Averages and Global Average
            List<SubjectAverageDto> averages = new ArrayList<>();
            BigDecimal totalWeightedAverage = BigDecimal.ZERO;
            BigDecimal totalCoefficients = BigDecimal.ZERO;

            for (SubjectGradesTracker tracker : subjectMap.values()) {
                BigDecimal subAvg = tracker.calculateAverage();
                BigDecimal coef = tracker.getSubject().getCoefficient();

                averages.add(SubjectAverageDto.builder()
                        .subjectId(tracker.getSubject().getId())
                        .subjectCode(tracker.getSubject().getCode())
                        .subjectNameFr(tracker.getSubject().getNameFr())
                        .coefficient(coef)
                        .average(subAvg)
                        .remarks(subAvg.compareTo(new BigDecimal("10.00")) >= 0 ? "Moyenne acquise" : "Insuffisant")
                        .build());

                totalWeightedAverage = totalWeightedAverage.add(subAvg.multiply(coef));
                totalCoefficients = totalCoefficients.add(coef);
            }

            BigDecimal globalAverage = totalCoefficients.compareTo(BigDecimal.ZERO) > 0
                    ? totalWeightedAverage.divide(totalCoefficients, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            reports.add(StudentReportDto.builder()
                    .studentId(student.getId())
                    .studentName(student.getLastName() + " " + student.getFirstName())
                    .registrationNumber(student.getRegistrationNumber())
                    .subjectsAverages(averages)
                    .globalAverage(globalAverage)
                    .build());
        }

        // Sort students by average descending to compute ranks
        reports.sort((a, b) -> b.getGlobalAverage().compareTo(a.getGlobalAverage()));
        for (int i = 0; i < reports.size(); i++) {
            reports.get(i).setRank(i + 1);
        }

        return ResponseEntity.ok(reports);
    }

    // Helper classes for Report Card computation
    @Data
    private static class SubjectGradesTracker {
        private final Subject subject;
        private List<BigDecimal> scores = new ArrayList<>();
        private List<BigDecimal> weights = new ArrayList<>();
        private List<BigDecimal> maxScores = new ArrayList<>();

        public void addGrade(BigDecimal score, BigDecimal weight, BigDecimal maxScore) {
            // Normalize score out of 20
            BigDecimal normalized = score;
            if (maxScore.compareTo(new BigDecimal("20.00")) != 0 && maxScore.compareTo(BigDecimal.ZERO) > 0) {
                normalized = score.multiply(new BigDecimal("20.00")).divide(maxScore, 4, RoundingMode.HALF_UP);
            }
            scores.add(normalized);
            weights.add(weight);
        }

        public BigDecimal calculateAverage() {
            BigDecimal totalScore = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (int i = 0; i < scores.size(); i++) {
                totalScore = totalScore.add(scores.get(i).multiply(weights.get(i)));
                totalWeight = totalWeight.add(weights.get(i));
            }
            return totalWeight.compareTo(BigDecimal.ZERO) > 0
                    ? totalScore.divide(totalWeight, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }
    }

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
