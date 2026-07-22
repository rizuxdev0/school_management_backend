package com.schoolmanager.config.service;

import com.schoolmanager.config.dto.SetupWizardDto;
import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SetupWizardService {

    private final SystemSettingRepository systemSettingRepository;
    private final CurrencyRepository currencyRepository;
    private final AcademicCycleRepository academicCycleRepository;
    private final AcademicLevelRepository academicLevelRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final GradingSystemRepository gradingSystemRepository;

    @Transactional
    public void setupInstitution(SetupWizardDto dto) {
        if (systemSettingRepository.findByTenantId(dto.getTenantId()).isPresent()) {
            throw new IllegalStateException("L'établissement est déjà initialisé");
        }

        // 1. Initialisation / Sélection de la devise
        Currency currency = currencyRepository.findByCode(dto.getCurrencyCode().toUpperCase())
                .orElseGet(() -> currencyRepository.save(Currency.builder()
                        .code(dto.getCurrencyCode().toUpperCase())
                        .symbol(dto.getCurrencySymbol())
                        .nameFr(dto.getCurrencyNameFr())
                        .nameEn(dto.getCurrencyNameFr())
                        .decimalDigits(2)
                        .isActive(true)
                        .build()));

        // 2. Création des paramètres système
        SystemSetting setting = SystemSetting.builder()
                .tenantId(dto.getTenantId())
                .institutionName(dto.getInstitutionName())
                .institutionType(dto.getInstitutionType())
                .defaultLanguage(dto.getDefaultLanguage())
                .mainCurrency(currency)
                .build();
        systemSettingRepository.save(setting);

        // 3. Création des Cycles et Niveaux (selon Preset)
        if ("FRENCH".equalsIgnoreCase(dto.getSystemPreset())) {
            createFrenchPreset(dto.getTenantId());
        } else if ("ENGLISH".equalsIgnoreCase(dto.getSystemPreset())) {
            createEnglishPreset(dto.getTenantId());
        } else if (dto.getCustomCycles() != null) {
            for (SetupWizardDto.CycleDto cycleDto : dto.getCustomCycles()) {
                AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                        .tenantId(dto.getTenantId())
                        .code(cycleDto.getCode())
                        .nameFr(cycleDto.getNameFr())
                        .nameEn(cycleDto.getNameEn())
                        .sequenceOrder(cycleDto.getSequenceOrder())
                        .build());

                if (cycleDto.getLevels() != null) {
                    for (SetupWizardDto.LevelDto levelDto : cycleDto.getLevels()) {
                        academicLevelRepository.save(AcademicLevel.builder()
                                .tenantId(dto.getTenantId())
                                .cycle(cycle)
                                .code(levelDto.getCode())
                                .nameFr(levelDto.getNameFr())
                                .nameEn(levelDto.getNameEn())
                                .sequenceOrder(levelDto.getSequenceOrder())
                                .build());
                    }
                }
            }
        }

        // 4. Création de l'année scolaire et périodes
        AcademicYear year = academicYearRepository.save(AcademicYear.builder()
                .tenantId(dto.getTenantId())
                .code(dto.getAcademicYearCode())
                .startDate(dto.getYearStartDate())
                .endDate(dto.getYearEndDate())
                .isCurrent(true)
                .status("ACTIVE")
                .build());

        createAcademicPeriods(dto.getTenantId(), year, dto.getPeriodType());

        // 5. Création d'un système de notation par défaut
        gradingSystemRepository.save(GradingSystem.builder()
                .tenantId(dto.getTenantId())
                .name("Système standard")
                .maxScore(new BigDecimal("20.00"))
                .passingScore(new BigDecimal("10.00"))
                .gradingType("NUMERIC_20")
                .isDefault(true)
                .build());
    }

    private void createAcademicPeriods(UUID tenantId, AcademicYear year, String periodType) {
        if ("SEMESTER".equalsIgnoreCase(periodType)) {
            academicPeriodRepository.save(AcademicPeriod.builder()
                    .tenantId(tenantId)
                    .academicYearId(year.getId())
                    .nameFr("Semestre 1")
                    .nameEn("Semester 1")
                    .periodType("SEMESTER")
                    .startDate(year.getStartDate())
                    .endDate(year.getStartDate().plusMonths(5))
                    .weight(new BigDecimal("1.0"))
                    .build());

            academicPeriodRepository.save(AcademicPeriod.builder()
                    .tenantId(tenantId)
                    .academicYearId(year.getId())
                    .nameFr("Semestre 2")
                    .nameEn("Semester 2")
                    .periodType("SEMESTER")
                    .startDate(year.getStartDate().plusMonths(5).plusDays(1))
                    .endDate(year.getEndDate())
                    .weight(new BigDecimal("1.0"))
                    .build());
        } else {
            // Trimestres par défaut
            for (int i = 1; i <= 3; i++) {
                academicPeriodRepository.save(AcademicPeriod.builder()
                        .tenantId(tenantId)
                        .academicYearId(year.getId())
                        .nameFr("Trimestre " + i)
                        .nameEn("Term " + i)
                        .periodType("TRIMESTER")
                        .startDate(year.getStartDate().plusMonths((i - 1) * 3))
                        .endDate(year.getStartDate().plusMonths(i * 3))
                        .weight(new BigDecimal("1.0"))
                        .build());
            }
        }
    }

    private void createFrenchPreset(UUID tenantId) {
        // Primaire
        AcademicCycle prim = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("PRIMARY").nameFr("Primaire").nameEn("Primary").sequenceOrder(1).build());
        String[] primLevels = {"CP", "CE1", "CE2", "CM1", "CM2"};
        for (int i = 0; i < primLevels.length; i++) {
            academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(prim).code(primLevels[i]).nameFr(primLevels[i]).nameEn(primLevels[i]).sequenceOrder(i + 1).build());
        }

        // Collège
        AcademicCycle mid = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("MIDDLE").nameFr("Collège").nameEn("Middle School").sequenceOrder(2).build());
        String[] midLevels = {"6EME", "5EME", "4EME", "3EME"};
        String[] midNames = {"6ème", "5ème", "4ème", "3ème"};
        for (int i = 0; i < midLevels.length; i++) {
            academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(mid).code(midLevels[i]).nameFr(midNames[i]).nameEn(midNames[i]).sequenceOrder(i + 1).build());
        }

        // Lycée
        AcademicCycle high = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("HIGH").nameFr("Lycée").nameEn("High School").sequenceOrder(3).build());
        String[] highLevels = {"2ND", "1ERE", "TERM"};
        String[] highNames = {"Seconde", "Première", "Terminale"};
        for (int i = 0; i < highLevels.length; i++) {
            academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(high).code(highLevels[i]).nameFr(highNames[i]).nameEn(highNames[i]).sequenceOrder(i + 1).build());
        }
    }

    private void createEnglishPreset(UUID tenantId) {
        AcademicCycle school = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("SCHOOL").nameFr("Cursus Scolaire").nameEn("K-12 Cursus").sequenceOrder(1).build());
        for (int i = 1; i <= 12; i++) {
            academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(school).code("GRADE_" + i).nameFr("Grade " + i).nameEn("Grade " + i).sequenceOrder(i).build());
        }
    }
}
