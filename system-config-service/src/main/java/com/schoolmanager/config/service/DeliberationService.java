package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliberationService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final BulletinCalculationService bulletinCalculationService;
    private final AuditLogService auditLogService;

    @Data
    @Builder
    public static class DeliberationSimulationDto {
        private UUID studentId;
        private String studentName;
        private String registrationNumber;
        private Map<UUID, BigDecimal> periodAverages; // periodId -> average
        private BigDecimal annualAverage;
        private int rank;
        private String proposedDecision; // PROMOTED, RETAINED
        private boolean hasMissingGrades;
        private UUID suggestedTargetClassroomId;
    }

    @Data
    public static class DeliberationRequestDto {
        private UUID classroomId;
        private UUID yearId;
        private UUID targetYearId;
        private List<StudentDecisionDto> decisions;
    }

    @Data
    public static class StudentDecisionDto {
        private UUID studentId;
        private String decision; // PROMOTED, RETAINED, EXCLUDED
        private UUID targetClassroomId;
    }

    /**
     * Simule les délibérations de fin d'année pour une classe et calcule les moyennes annuelles pondérées.
     */
    public List<DeliberationSimulationDto> simulateDeliberation(UUID classroomId, UUID yearId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe d'origine introuvable"));

        SecurityUtils.assertOwnership(classroom.getTenantId());

        // 1. Charger les périodes de cette année
        List<AcademicPeriod> periods = academicPeriodRepository.findByAcademicYearId(yearId);
        if (periods.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune période académique configurée pour cette année scolaire.");
        }

        // 2. Charger les élèves inscrits dans cette classe
        List<StudentEnrollment> enrollments = studentEnrollmentRepository.findByClassroomIdAndAcademicYearId(classroomId, yearId);
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Calculer les moyennes de chaque période pour tous les élèves
        Map<UUID, Map<UUID, BigDecimal>> studentPeriodAverages = new HashMap<>(); // studentId -> {periodId -> average}
        Map<UUID, Boolean> studentMissingGrades = new HashMap<>();

        for (AcademicPeriod period : periods) {
            try {
                var reports = bulletinCalculationService.calculateReports(classroomId, period.getId(), yearId);
                for (var rep : reports) {
                    studentPeriodAverages.computeIfAbsent(rep.getStudentId(), id -> new HashMap<>())
                            .put(period.getId(), rep.getGlobalAverage());
                }
            } catch (Exception ex) {
                // S'il n'y a pas encore de notes pour cette période, les moyennes vaudront zéro
                for (StudentEnrollment enr : enrollments) {
                    studentPeriodAverages.computeIfAbsent(enr.getStudent().getId(), id -> new HashMap<>())
                            .put(period.getId(), BigDecimal.ZERO);
                    studentMissingGrades.put(enr.getStudent().getId(), true);
                }
            }
        }

        // 4. Calculer la moyenne annuelle pondérée
        List<DeliberationSimulationDto> simulations = new ArrayList<>();
        BigDecimal totalPeriodWeights = periods.stream()
                .map(p -> p.getWeight() != null ? p.getWeight() : BigDecimal.ONE)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Trouver la classe suggérée supérieure
        UUID suggestedTargetClassroomId = findSuggestedNextClassroom(classroom, yearId);

        for (StudentEnrollment enr : enrollments) {
            Student student = enr.getStudent();
            Map<UUID, BigDecimal> averages = studentPeriodAverages.getOrDefault(student.getId(), Collections.emptyMap());

            BigDecimal weightedSum = BigDecimal.ZERO;
            boolean missing = studentMissingGrades.getOrDefault(student.getId(), false);

            for (AcademicPeriod period : periods) {
                BigDecimal avg = averages.get(period.getId());
                if (avg == null) {
                    avg = BigDecimal.ZERO;
                    missing = true;
                }
                BigDecimal weight = period.getWeight() != null ? period.getWeight() : BigDecimal.ONE;
                weightedSum = weightedSum.add(avg.multiply(weight));
            }

            BigDecimal annualAvg = totalPeriodWeights.compareTo(BigDecimal.ZERO) > 0
                    ? weightedSum.divide(totalPeriodWeights, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String decision = annualAvg.compareTo(BigDecimal.valueOf(10.0)) >= 0 ? "PROMOTED" : "RETAINED";

            simulations.add(DeliberationSimulationDto.builder()
                    .studentId(student.getId())
                    .studentName(student.getLastName() + " " + student.getFirstName())
                    .registrationNumber(student.getRegistrationNumber())
                    .periodAverages(averages)
                    .annualAverage(annualAvg)
                    .proposedDecision(decision)
                    .hasMissingGrades(missing)
                    .suggestedTargetClassroomId(decision.equals("PROMOTED") ? suggestedTargetClassroomId : classroomId)
                    .build());
        }

        // 5. Calculer les rangs annuels
        simulations.sort((a, b) -> b.getAnnualAverage().compareTo(a.getAnnualAverage()));
        for (int i = 0; i < simulations.size(); i++) {
            simulations.get(i).setRank(i + 1);
        }

        return simulations;
    }

    /**
     * Exécute la délibération et inscrit les élèves dans l'année scolaire de destination.
     */
    @Transactional
    public void executeDeliberation(DeliberationRequestDto request) {
        Classroom classroom = classroomRepository.findById(request.getClassroomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe d'origine introuvable"));

        SecurityUtils.assertOwnership(classroom.getTenantId());

        AcademicYear targetYear = academicYearRepository.findById(request.getTargetYearId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Année scolaire de destination introuvable"));

        if (!"ACTIVE".equals(targetYear.getStatus()) && !"PLANNED".equals(targetYear.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'année scolaire de destination doit être en cours ou planifiée.");
        }

        for (StudentDecisionDto dec : request.getDecisions()) {
            // Rechercher l'élève
            StudentEnrollment currentEnr = studentEnrollmentRepository
                    .findByStudentIdAndAcademicYearId(dec.getStudentId(), request.getYearId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inscription introuvable pour l'élève"));

            // 1. Clôturer l'inscription courante en y consignant la décision de fin d'année
            currentEnr.setStatus("CLOSED"); // statut d'archive historique
            currentEnr.setNotes("Décision délibération : " + dec.getDecision());
            studentEnrollmentRepository.save(currentEnr);

            // 2. Si non exclu, créer la nouvelle inscription pour la nouvelle année
            if (!"EXCLUDED".equalsIgnoreCase(dec.getDecision()) && dec.getTargetClassroomId() != null) {
                Classroom targetClass = classroomRepository.findById(dec.getTargetClassroomId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe de destination introuvable"));

                // Vérification de la capacité de la classe cible
                long currentCapacityCount = studentEnrollmentRepository
                        .findByClassroomIdAndAcademicYearId(dec.getTargetClassroomId(), request.getTargetYearId())
                        .stream().filter(e -> "ACTIVE".equals(e.getStatus())).count();

                if (currentCapacityCount >= targetClass.getCapacity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            String.format("La classe de destination %s a déjà atteint sa capacité maximale de %s élèves.",
                                    targetClass.getName(), targetClass.getCapacity()));
                }

                // Inscription de l'élève
                StudentEnrollment newEnr = StudentEnrollment.builder()
                        .tenantId(classroom.getTenantId())
                        .student(currentEnr.getStudent())
                        .classroom(targetClass)
                        .academicYearId(request.getTargetYearId())
                        .enrollmentDate(LocalDate.now())
                        .status("ACTIVE")
                        .notes("Inscrit via Deliberation depuis la classe " + classroom.getName())
                        .build();

                studentEnrollmentRepository.save(newEnr);
            }
        }

        auditLogService.log("UPDATE", "Classroom", request.getClassroomId().toString(),
                String.format("Exécution des délibérations de fin d'année pour la classe %s. Transfert vers l'année %s.",
                        classroom.getName(), targetYear.getCode()));
    }

    /**
     * Suggère la classe supérieure en cherchant le niveau ayant la sequenceOrder immédiatement supérieure.
     */
    private UUID findSuggestedNextClassroom(Classroom origin, UUID yearId) {
        AcademicLevel currentLevel = origin.getAcademicLevel();
        if (currentLevel == null) return origin.getId();

        int nextOrder = currentLevel.getSequenceOrder() + 1;
        // Chercher les classes du niveau supérieur pour ce tenant
        List<Classroom> nextLevelClassrooms = classroomRepository.findByTenantId(origin.getTenantId())
                .stream()
                .filter(c -> c.getAcademicLevel() != null && c.getAcademicLevel().getSequenceOrder() == nextOrder)
                .collect(Collectors.toList());

        if (!nextLevelClassrooms.isEmpty()) {
            return nextLevelClassrooms.get(0).getId();
        }

        return origin.getId();
    }
}
