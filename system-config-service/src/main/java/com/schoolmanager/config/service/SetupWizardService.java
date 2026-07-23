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
    private final ClassroomRepository classroomRepository;

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
        // 1. Primaire (Togo / Francophone : CP1, CP2, CE1, CE2, CM1, CM2)
        AcademicCycle prim = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("PRIMARY").nameFr("Primaire").nameEn("Primary School").sequenceOrder(1).build());
        String[] primLevels = {"CP1", "CP2", "CE1", "CE2", "CM1", "CM2"};
        String[] primNames = {"Cours Préparatoire 1ère année", "Cours Préparatoire 2ème année", "Cours Élémentaire 1ère année", "Cours Élémentaire 2ème année", "Cours Moyen 1ère année", "Cours Moyen 2ème année"};
        for (int i = 0; i < primLevels.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(prim).code(primLevels[i]).nameFr(primNames[i]).nameEn(primLevels[i]).sequenceOrder(i + 1).build());
            
            // Classes CP1-A, CP1-B, etc.
            classroomRepository.save(Classroom.builder()
                    .tenantId(tenantId).code(primLevels[i] + "-A").name(primLevels[i] + " - Classe A").academicLevel(lvl).capacity(40).isActive(true).build());
            classroomRepository.save(Classroom.builder()
                    .tenantId(tenantId).code(primLevels[i] + "-B").name(primLevels[i] + " - Classe B").academicLevel(lvl).capacity(40).isActive(true).build());
        }

        // 2. Collège (6ème, 5ème, 4ème, 3ème)
        AcademicCycle mid = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("MIDDLE").nameFr("Collège").nameEn("Middle School").sequenceOrder(2).build());
        String[] midLevels = {"6EME", "5EME", "4EME", "3EME"};
        String[] midNames = {"6ème", "5ème", "4ème", "3ème"};
        for (int i = 0; i < midLevels.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(mid).code(midLevels[i]).nameFr(midNames[i]).nameEn(midNames[i]).sequenceOrder(i + 1).build());
            
            // Classes 6e A, 6e B, etc.
            classroomRepository.save(Classroom.builder()
                    .tenantId(tenantId).code(midLevels[i] + "-A").name(midNames[i] + " A").academicLevel(lvl).capacity(45).isActive(true).build());
            classroomRepository.save(Classroom.builder()
                    .tenantId(tenantId).code(midLevels[i] + "-B").name(midNames[i] + " B").academicLevel(lvl).capacity(45).isActive(true).build());
        }

        // 3. Lycée (Togo : Secondes L/S, Premières et Terminales Séries A4/D/C)
        AcademicCycle high = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("HIGH").nameFr("Lycée").nameEn("High School").sequenceOrder(3).build());
        String[] highLevels = {"2ND_LE", "2ND_S", "1ERE_A4", "1ERE_D", "1ERE_C", "TLE_A4", "TLE_D", "TLE_C"};
        String[] highNames = {"Seconde Littéraire", "Seconde Scientifique", "Première A4", "Première D", "Première C", "Terminale A4", "Terminale D", "Terminale C"};
        for (int i = 0; i < highLevels.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(high).code(highLevels[i]).nameFr(highNames[i]).nameEn(highNames[i]).sequenceOrder(i + 1).build());
            
            // Classes (ex: Tle D1, Tle C1)
            classroomRepository.save(Classroom.builder()
                    .tenantId(tenantId).code(highLevels[i] + "-1").name(highNames[i] + " 1").academicLevel(lvl).capacity(50).isActive(true).build());
        }

        // 4. Université / Campus (Système LMD : Licence L1-L3, Master M1-M2)
        AcademicCycle licenceCycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("CAMPUS_LICENCE").nameFr("Enseignement Supérieur (Licence)").nameEn("University (Bachelor)").sequenceOrder(4).build());
        AcademicCycle masterCycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("CAMPUS_MASTER").nameFr("Enseignement Supérieur (Master)").nameEn("University (Master)").sequenceOrder(5).build());

        // Filières professionnelles types d'Afrique de l'Ouest
        String[] tracks = {"GL", "FCG", "RIT", "MKT"};
        String[] trackNames = {"Génie Logiciel", "Finance Comptabilité & Gestion", "Réseaux & Informatique Télécoms", "Marketing & Communication"};

        // Niveaux Licence (L1, L2, L3)
        String[] lLevels = {"L1", "L2", "L3"};
        String[] lNames = {"Licence 1ère année", "Licence 2ème année", "Licence 3ème année"};
        for (int i = 0; i < lLevels.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(licenceCycle).code(lLevels[i]).nameFr(lNames[i]).nameEn(lLevels[i]).sequenceOrder(i + 1).build());
            
            // Création des filières sous forme de classes physiques (ex: L1 Génie Logiciel)
            for (int t = 0; t < tracks.length; t++) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(lLevels[i] + "-" + tracks[t])
                        .name(lLevels[i] + " - " + trackNames[t])
                        .academicLevel(lvl)
                        .capacity(60)
                        .isActive(true)
                        .build());
            }
        }

        // Niveaux Master (M1, M2)
        String[] mLevels = {"M1", "M2"};
        String[] mNames = {"Master 1ère année", "Master 2ème année"};
        for (int i = 0; i < mLevels.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(masterCycle).code(mLevels[i]).nameFr(mNames[i]).nameEn(mLevels[i]).sequenceOrder(i + 1).build());
            
            // Création des filières sous forme de classes physiques (ex: M1 Génie Logiciel)
            for (int t = 0; t < tracks.length; t++) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(mLevels[i] + "-" + tracks[t])
                        .name(mLevels[i] + " - " + trackNames[t])
                        .academicLevel(lvl)
                        .capacity(40)
                        .isActive(true)
                        .build());
            }
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
