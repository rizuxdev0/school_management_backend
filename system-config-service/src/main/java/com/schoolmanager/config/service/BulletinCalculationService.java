package com.schoolmanager.config.service;

import com.schoolmanager.config.controller.EvaluationAndGradesController.StudentReportDto;
import com.schoolmanager.config.controller.EvaluationAndGradesController.SubjectAverageDto;
import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service de calcul des bulletins scolaires.
 * Extrait et centralisé depuis EvaluationAndGradesController pour être réutilisé
 * par le service de génération PDF (BulletinReportService).
 *
 * Calcule pour chaque élève d'une classe :
 *  - Les moyennes pondérées par matière (normalisées sur 20)
 *  - La moyenne générale pondérée par les coefficients
 *  - Le rang dans la classe (trié par moyenne décroissante)
 *  - Les statistiques globales de classe (moyenne de classe, min, max, effectif)
 *  - La mention d'honneur ou appréciation personnalisable par l'établissement via HonorsAppreciationService
 */
@Service
@RequiredArgsConstructor
public class BulletinCalculationService {

    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final HonorsAppreciationService honorsAppreciationService;

    /**
     * Calcule les bulletins de toute une classe pour une période et une année académique.
     *
     * @param classroomId UUID de la classe
     * @param periodId    UUID de la période académique
     * @param yearId      UUID de l'année académique
     * @return Liste triée par rang (meilleur élève en premier)
     */
    public List<StudentReportDto> calculateReports(UUID classroomId, UUID periodId, UUID yearId) {
        // 1. Récupérer les inscriptions de la classe pour l'année
        List<StudentEnrollment> enrollments = studentEnrollmentRepository
                .findByClassroomIdAndAcademicYearId(classroomId, yearId);

        // 2. Récupérer toutes les évaluations de la classe pour la période
        List<Evaluation> evals = evaluationRepository
                .findByClassroomIdAndAcademicPeriodId(classroomId, periodId);

        // Récupérer les règles de mentions personnalisées de l'établissement
        UUID tenantId = !enrollments.isEmpty() && enrollments.get(0).getTenantId() != null
                ? enrollments.get(0).getTenantId()
                : SecurityUtils.getCurrentTenantId();
        List<HonorsAppreciationRule> appreciationRules = honorsAppreciationService.getOrCreateRulesForTenant(tenantId);

        List<StudentReportDto> reports = new ArrayList<>();

        for (StudentEnrollment enr : enrollments) {
            Student student = enr.getStudent();
            Map<UUID, SubjectGradesTracker> subjectMap = new LinkedHashMap<>();

            // 3. Parcourir les évaluations et collecter les notes de l'élève
            for (Evaluation ev : evals) {
                Optional<StudentGrade> gradeOpt = studentGradeRepository
                        .findByStudentIdAndEvaluationId(student.getId(), ev.getId());

                if (gradeOpt.isPresent()) {
                    Subject sub = ev.getSubject();
                    subjectMap.computeIfAbsent(sub.getId(), id -> new SubjectGradesTracker(sub))
                              .addGrade(gradeOpt.get().getScore(), ev.getWeight(), ev.getMaxScore());
                }
            }

            // 4. Calculer les moyennes par matière + moyenne générale pondérée
            List<SubjectAverageDto> averages = new ArrayList<>();
            BigDecimal totalWeightedAverage = BigDecimal.ZERO;
            BigDecimal totalCoefficients = BigDecimal.ZERO;

            for (SubjectGradesTracker tracker : subjectMap.values()) {
                BigDecimal subAvg = tracker.calculateAverage();
                BigDecimal coef = tracker.getSubject().getCoefficient();
                String remark = subAvg.compareTo(new BigDecimal("10.00")) >= 0
                        ? "Moyenne acquise" : "Insuffisant";

                averages.add(SubjectAverageDto.builder()
                        .subjectId(tracker.getSubject().getId())
                        .subjectCode(tracker.getSubject().getCode())
                        .subjectNameFr(tracker.getSubject().getNameFr())
                        .coefficient(coef)
                        .average(subAvg)
                        .remarks(remark)
                        .build());

                totalWeightedAverage = totalWeightedAverage.add(subAvg.multiply(coef));
                totalCoefficients = totalCoefficients.add(coef);
            }

            BigDecimal globalAverage = totalCoefficients.compareTo(BigDecimal.ZERO) > 0
                    ? totalWeightedAverage.divide(totalCoefficients, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String appreciation = honorsAppreciationService.evaluateAppreciation(globalAverage, appreciationRules);

            reports.add(StudentReportDto.builder()
                    .studentId(student.getId())
                    .studentName(student.getLastName() + " " + student.getFirstName())
                    .registrationNumber(student.getRegistrationNumber())
                    .subjectsAverages(averages)
                    .globalAverage(globalAverage)
                    .appreciation(appreciation)
                    .build());
        }

        // 5. Trier par moyenne décroissante et attribuer les rangs
        reports.sort((a, b) -> b.getGlobalAverage().compareTo(a.getGlobalAverage()));
        int totalStudents = reports.size();

        BigDecimal sumAverages = BigDecimal.ZERO;
        BigDecimal minAverage = totalStudents > 0 ? reports.get(totalStudents - 1).getGlobalAverage() : BigDecimal.ZERO;
        BigDecimal maxAverage = totalStudents > 0 ? reports.get(0).getGlobalAverage() : BigDecimal.ZERO;

        for (StudentReportDto r : reports) {
            sumAverages = sumAverages.add(r.getGlobalAverage());
        }

        BigDecimal classAverage = totalStudents > 0
                ? sumAverages.divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        for (int i = 0; i < reports.size(); i++) {
            StudentReportDto r = reports.get(i);
            r.setRank(i + 1);
            r.setTotalStudents(totalStudents);
            r.setClassAverage(classAverage);
            r.setMinClassAverage(minAverage);
            r.setMaxClassAverage(maxAverage);
        }

        return reports;
    }

    /**
     * Classe interne de calcul de la moyenne pondérée par matière.
     * Normalise les notes sur 20 avant le calcul.
     */
    @Data
    public static class SubjectGradesTracker {
        private final Subject subject;
        private final List<BigDecimal> scores = new ArrayList<>();
        private final List<BigDecimal> weights = new ArrayList<>();

        public void addGrade(BigDecimal score, BigDecimal weight, BigDecimal maxScore) {
            BigDecimal normalized = score;
            if (maxScore != null && maxScore.compareTo(new BigDecimal("20.00")) != 0
                    && maxScore.compareTo(BigDecimal.ZERO) > 0) {
                normalized = score.multiply(new BigDecimal("20.00"))
                        .divide(maxScore, 4, RoundingMode.HALF_UP);
            }
            scores.add(normalized);
            weights.add(weight != null ? weight : BigDecimal.ONE);
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
}
