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

/**
 * Service responsable de l'initialisation complète d'un établissement (tenant).
 *
 * La logique de création des cycles/niveaux est désormais ciblée par type
 * d'établissement (institutionType) : on ne crée que ce dont l'école a besoin.
 *
 * Types supportés :
 *  - PRIMARY    → Primaire (CP1–CM2) uniquement
 *  - MIDDLE     → Collège (6ème–3ème) uniquement
 *  - HIGH       → Lycée (Seconde–Terminale) uniquement
 *  - UNIVERSITY → Campus LMD (L1–L3, M1–M2) uniquement
 *  - MIXED      → Tous les cycles du primaire à l'université
 */
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
    private final SchoolEventRepository schoolEventRepository;

    @Transactional
    public void setupInstitution(SetupWizardDto dto) {
        if (systemSettingRepository.findByTenantId(dto.getTenantId()).isPresent()) {
            throw new IllegalStateException("L'établissement est déjà initialisé");
        }

        boolean isFrench = !"ENGLISH".equalsIgnoreCase(dto.getSystemPreset());

        // 1. Devise principale
        Currency currency = currencyRepository.findByCode(dto.getCurrencyCode())
                .orElseGet(() -> currencyRepository.save(Currency.builder()
                        .code(dto.getCurrencyCode().toUpperCase())
                        .symbol(dto.getCurrencySymbol())
                        .nameFr(dto.getCurrencyNameFr() != null ? dto.getCurrencyNameFr() : dto.getCurrencyCode())
                        .nameEn(dto.getCurrencyNameFr() != null ? dto.getCurrencyNameFr() : dto.getCurrencyCode())
                        .decimalDigits(2)
                        .exchangeRate(BigDecimal.ONE)
                        .isActive(true)
                        .build()));

        // 2. Profil de l'Établissement (SaaS Tenant Setting)
        SystemSetting settings = SystemSetting.builder()
                .tenantId(dto.getTenantId())
                .institutionName(dto.getInstitutionName())
                .institutionType(dto.getInstitutionType() != null ? dto.getInstitutionType() : "PRIVATE")
                .primaryColor("#2563eb") // couleur bleue moderne par défaut
                .bulletinTemplate("PROFESSIONAL")
                .defaultLanguage(isFrench ? "fr" : "en")
                .mainCurrency(currency)
                .build();
        systemSettingRepository.save(settings);

        // 3. Cursus et Niveaux ciblés selon le type d'établissement choisi
        String institutionType = dto.getInstitutionType();
        if (institutionType != null) {
            switch (institutionType) {
                case "PRIMARY"    -> createPrimary(dto.getTenantId(), isFrench);
                case "MIDDLE"     -> createMiddle(dto.getTenantId(), isFrench);
                case "HIGH"       -> createHigh(dto.getTenantId(), isFrench);
                case "BTS"        -> createBTS(dto.getTenantId(), isFrench);
                case "UNIVERSITY" -> createUniversity(dto.getTenantId(), isFrench);
                default           -> createMixed(dto.getTenantId(), isFrench); // MIXED
            }
        }

        // 4. Année académique initiale
        AcademicYear year = null;
        if (dto.getAcademicYearCode() != null && !dto.getAcademicYearCode().isBlank()) {
            year = academicYearRepository.save(AcademicYear.builder()
                    .tenantId(dto.getTenantId())
                    .code(dto.getAcademicYearCode())
                    .startDate(dto.getYearStartDate())
                    .endDate(dto.getYearEndDate())
                    .isCurrent(true)
                    .build());
            createPeriods(dto, year);
        }

        // 5. Système de notation par défaut
        GradingSystem gs = GradingSystem.builder()
                .tenantId(dto.getTenantId())
                .name(isFrench ? "Système sur 20" : "Grading out of 20")
                .gradingType("NUMERIC_20") // champ NOT NULL — valeur par défaut : notation sur 20
                .maxScore(BigDecimal.valueOf(20))
                .passingScore(BigDecimal.valueOf(10))
                .isDefault(true)
                .build();
        gradingSystemRepository.save(gs);

        // 6. Événements par défaut sur le calendrier
        if (year != null) {
            createDefaultEvents(dto, isFrench);
        }
    }

    /**
     * Génère automatiquement des événements types et cohérents pour le calendrier scolaire
     * basés sur la plage temporelle de l'année scolaire renseignée (anti-cold-start).
     */
    private void createDefaultEvents(SetupWizardDto dto, boolean fr) {
        LocalDate start = dto.getYearStartDate();
        LocalDate end = dto.getYearEndDate();
        UUID tenantId = dto.getTenantId();

        // 1. Rentrée académique (le premier jour de l'année)
        schoolEventRepository.save(SchoolEvent.builder()
                .tenantId(tenantId)
                .titleFr("Rentrée Scolaire")
                .titleEn("Academic School Opening")
                .descriptionFr("Premier jour de l'année scolaire et accueil des nouveaux élèves.")
                .descriptionEn("First day of the school year and welcoming of new students.")
                .startDate(start)
                .category("ACADEMIC")
                .status("PLANNED")
                .isMandatory(true)
                .build());

        // 2. Congés de Noël / Fin d'année (autour du 20 décembre de l'année de début)
        LocalDate christmasStart = LocalDate.of(start.getYear(), 12, 20);
        LocalDate christmasEnd = LocalDate.of(start.getYear() + 1, 1, 3);
        if (christmasStart.isAfter(start) && christmasEnd.isBefore(end)) {
            schoolEventRepository.save(SchoolEvent.builder()
                    .tenantId(tenantId)
                    .titleFr("Vacances de Noël")
                    .titleEn("Christmas Holidays")
                    .descriptionFr("Période de congés de fin d'année.")
                    .descriptionEn("End-of-year school holidays.")
                    .startDate(christmasStart)
                    .endDate(christmasEnd)
                    .category("HOLIDAY")
                    .status("PLANNED")
                    .isMandatory(false)
                    .build());
        }

        // 3. Période d'Examens Pédagogiques (2 semaines avant la fin de l'année)
        LocalDate examsStart = end.minusWeeks(2);
        LocalDate examsEnd = end.minusDays(2);
        if (examsStart.isAfter(start)) {
            schoolEventRepository.save(SchoolEvent.builder()
                    .tenantId(tenantId)
                    .titleFr("Session d'Examens et Compositions")
                    .titleEn("Examination Session")
                    .descriptionFr("Évaluations et examens finaux de l'établissement.")
                    .descriptionEn("Final evaluation and examination session.")
                    .startDate(examsStart)
                    .endDate(examsEnd)
                    .category("EXAM")
                    .status("PLANNED")
                    .isMandatory(true)
                    .build());
        }

        // 4. Clôture administrative (le dernier jour de l'année)
        schoolEventRepository.save(SchoolEvent.builder()
                .tenantId(tenantId)
                .titleFr("Clôture et Conseil de Classe Final")
                .titleEn("Academic Year Closing")
                .descriptionFr("Délibérations de fin d'année et remise des bulletins de notes.")
                .descriptionEn("End-of-year deliberations and distribution of final report cards.")
                .startDate(end)
                .category("ADMINISTRATIVE")
                .status("PLANNED")
                .isMandatory(true)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIMAIRE (CP1–CM2) — Terminologie : Élèves · Classes
    // ─────────────────────────────────────────────────────────────────────────
    private void createPrimary(UUID tenantId, boolean fr) {
        AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId)
                .code("PRIMARY")
                .nameFr("Primaire")
                .nameEn("Primary School")
                .sequenceOrder(1)
                .build());

        String[] codes  = {"CP1", "CP2", "CE1", "CE2", "CM1", "CM2"};
        String[] namesFr = {
            "Cours Préparatoire 1ère année", "Cours Préparatoire 2ème année",
            "Cours Élémentaire 1ère année",  "Cours Élémentaire 2ème année",
            "Cours Moyen 1ère année",         "Cours Moyen 2ème année"
        };
        String[] namesEn = {"Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6"};

        for (int i = 0; i < codes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(cycle)
                    .code(codes[i])
                    .nameFr(fr ? namesFr[i] : namesEn[i])
                    .nameEn(namesEn[i])
                    .sequenceOrder(i + 1).build());
            // 2 classes par niveau
            for (String suffix : new String[]{"A", "B"}) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(codes[i] + "-" + suffix)
                        .name(codes[i] + " - Classe " + suffix)
                        .academicLevel(lvl).capacity(40).isActive(true).build());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COLLÈGE (6ème–3ème) — Terminologie : Élèves · Classes
    // ─────────────────────────────────────────────────────────────────────────
    private void createMiddle(UUID tenantId, boolean fr) {
        AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId)
                .code("MIDDLE")
                .nameFr("Collège")
                .nameEn("Middle School")
                .sequenceOrder(1)
                .build());

        String[] codes   = {"6EME", "5EME", "4EME", "3EME"};
        String[] namesFr = {"6ème", "5ème", "4ème", "3ème"};
        String[] namesEn = {"Grade 7", "Grade 8", "Grade 9", "Grade 10"};

        for (int i = 0; i < codes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(cycle)
                    .code(codes[i])
                    .nameFr(fr ? namesFr[i] : namesEn[i])
                    .nameEn(namesEn[i])
                    .sequenceOrder(i + 1).build());
            for (String suffix : new String[]{"A", "B"}) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(codes[i] + "-" + suffix)
                        .name((fr ? namesFr[i] : namesEn[i]) + " " + suffix)
                        .academicLevel(lvl).capacity(45).isActive(true).build());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LYCÉE — Terminologie : Élèves · Séries (A4, C, D) / Tracks
    // ─────────────────────────────────────────────────────────────────────────
    private void createHigh(UUID tenantId, boolean fr) {
        AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId)
                .code("HIGH")
                .nameFr("Lycée")
                .nameEn("High School")
                .sequenceOrder(1)
                .build());

        // Togo : Seconde (L/S), Première (A4/D/C), Terminale (A4/D/C)
        String[] codes   = {"2ND_LE","2ND_S","1ERE_A4","1ERE_D","1ERE_C","TLE_A4","TLE_D","TLE_C"};
        String[] namesFr = {
            "Seconde Littéraire","Seconde Scientifique",
            "Première A4","Première D","Première C",
            "Terminale A4","Terminale D","Terminale C"
        };
        String[] namesEn = {
            "10th - Literary","10th - Science",
            "11th - Series A4","11th - Series D","11th - Series C",
            "12th - Series A4","12th - Series D","12th - Series C"
        };

        for (int i = 0; i < codes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(cycle)
                    .code(codes[i])
                    .nameFr(fr ? namesFr[i] : namesEn[i])
                    .nameEn(namesEn[i])
                    .sequenceOrder(i + 1).build());
            // 1 classe par série
            classroomRepository.save(Classroom.builder()
                    .tenantId(tenantId)
                    .code(codes[i] + "-1")
                    .name((fr ? namesFr[i] : namesEn[i]) + " 1")
                    .academicLevel(lvl).capacity(50).isActive(true).build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UNIVERSITÉ / CAMPUS — Terminologie : Étudiants · Filières · Promotions
    // Système LMD : Licence (L1–L3), Master (M1–M2)
    // ─────────────────────────────────────────────────────────────────────────
    private void createUniversity(UUID tenantId, boolean fr) {
        // 1. Cycle BTS (2 ans)
        AcademicCycle bts = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("BTS")
                .nameFr("Brevet de Technicien Supérieur").nameEn("Higher National Diploma")
                .sequenceOrder(1).build());

        // 2. Cycle Licence (3 ans)
        AcademicCycle licence = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("LICENCE")
                .nameFr("Licence (LMD)").nameEn("Bachelor (LMD)")
                .sequenceOrder(2).build());

        // 3. Cycle Master (2 ans)
        AcademicCycle master = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("MASTER")
                .nameFr("Master (LMD)").nameEn("Master (LMD)")
                .sequenceOrder(3).build());

        // Filières types créées par défaut
        String[] tracks   = {"GL", "FCG", "RIT", "MKT"};
        String[] tracksFr = {"Génie Logiciel", "Finance, Comptabilité & Gestion", "Réseaux & Informatique Télécoms", "Marketing & Communication"};
        String[] tracksEn = {"Software Engineering", "Finance, Accounting & Management", "Networks & Telecom IT", "Marketing & Communication"};

        // Initialisation BTS 1 et BTS 2
        String[] btsCodes   = {"BTS1", "BTS2"};
        String[] btsNamesFr = {"BTS 1ère année", "BTS 2ème année"};
        String[] btsNamesEn = {"1st Year BTS", "2nd Year BTS"};

        for (int i = 0; i < btsCodes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(bts)
                    .code(btsCodes[i])
                    .nameFr(fr ? btsNamesFr[i] : btsNamesEn[i])
                    .nameEn(btsNamesEn[i])
                    .sequenceOrder(i + 1).build());
            for (int t = 0; t < tracks.length; t++) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(btsCodes[i] + "-" + tracks[t])
                        .name(btsCodes[i] + " " + (fr ? tracksFr[t] : tracksEn[t]))
                        .academicLevel(lvl).capacity(60).isActive(true).build());
            }
        }

        // Niveaux Licence L1, L2, L3
        String[] lCodes   = {"L1","L2","L3"};
        String[] lNamesFr = {"Licence 1ère année","Licence 2ème année","Licence 3ème année"};
        String[] lNamesEn = {"1st Year Bachelor","2nd Year Bachelor","3rd Year Bachelor"};

        for (int i = 0; i < lCodes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(licence)
                    .code(lCodes[i])
                    .nameFr(fr ? lNamesFr[i] : lNamesEn[i])
                    .nameEn(lNamesEn[i])
                    .sequenceOrder(i + 1).build());
            for (int t = 0; t < tracks.length; t++) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(lCodes[i] + "-" + tracks[t])
                        .name(lCodes[i] + " " + (fr ? tracksFr[t] : tracksEn[t]))
                        .academicLevel(lvl).capacity(60).isActive(true).build());
            }
        }

        // Niveaux Master M1, M2
        String[] mCodes   = {"M1","M2"};
        String[] mNamesFr = {"Master 1ère année","Master 2ème année"};
        String[] mNamesEn = {"1st Year Master","2nd Year Master"};

        for (int i = 0; i < mCodes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(master)
                    .code(mCodes[i])
                    .nameFr(fr ? mNamesFr[i] : mNamesEn[i])
                    .nameEn(mNamesEn[i])
                    .sequenceOrder(i + 1).build());
            for (int t = 0; t < tracks.length; t++) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(mCodes[i] + "-" + tracks[t])
                        .name(mCodes[i] + " " + (fr ? tracksFr[t] : tracksEn[t]))
                        .academicLevel(lvl).capacity(40).isActive(true).build());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENSEIGNEMENT TECHNIQUE / BTS (Brevet de Technicien Supérieur)
    // ─────────────────────────────────────────────────────────────────────────
    private void createBTS(UUID tenantId, boolean fr) {
        createBtsWithOrder(tenantId, fr, 1);
    }

    private void createBtsWithOrder(UUID tenantId, boolean fr, int order) {
        AcademicCycle bts = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("BTS")
                .nameFr("Brevet de Technicien Supérieur").nameEn("Higher National Diploma")
                .sequenceOrder(order).build());

        String[] tracks   = {"GL", "FCG", "RIT", "MKT"};
        String[] tracksFr = {"Génie Logiciel", "Finance, Comptabilité & Gestion", "Réseaux & Informatique Télécoms", "Marketing & Communication"};
        String[] tracksEn = {"Software Engineering", "Finance, Accounting & Management", "Networks & Telecom IT", "Marketing & Communication"};

        String[] btsCodes   = {"BTS1", "BTS2"};
        String[] btsNamesFr = {"BTS 1ère année", "BTS 2ème année"};
        String[] btsNamesEn = {"1st Year BTS", "2nd Year BTS"};

        for (int i = 0; i < btsCodes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(bts)
                    .code(btsCodes[i])
                    .nameFr(fr ? btsNamesFr[i] : btsNamesEn[i])
                    .nameEn(btsNamesEn[i])
                    .sequenceOrder(i + 1).build());
            for (int t = 0; t < tracks.length; t++) {
                classroomRepository.save(Classroom.builder()
                        .tenantId(tenantId)
                        .code(btsCodes[i] + "-" + tracks[t])
                        .name(btsCodes[i] + " " + (fr ? tracksFr[t] : tracksEn[t]))
                        .academicLevel(lvl).capacity(60).isActive(true).build());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MULTI-NIVEAUX (MIXED) — Tous les cycles du primaire à l'université
    // ─────────────────────────────────────────────────────────────────────────
    private void createMixed(UUID tenantId, boolean fr) {
        createPrimaryWithOrder(tenantId, fr, 1);
        createMiddleWithOrder(tenantId, fr, 2);
        createHighWithOrder(tenantId, fr, 3);
        createBtsWithOrder(tenantId, fr, 4);
        createUniversityWithOrder(tenantId, fr, 5);
    }

    // ─── Variantes avec sequenceOrder configurable pour MIXED ────────────────

    private void createPrimaryWithOrder(UUID tenantId, boolean fr, int order) {
        AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("PRIMARY").nameFr("Primaire").nameEn("Primary School").sequenceOrder(order).build());
        String[] codes   = {"CP1","CP2","CE1","CE2","CM1","CM2"};
        String[] namesFr = {"Cours Préparatoire 1","Cours Préparatoire 2","Cours Élémentaire 1","Cours Élémentaire 2","Cours Moyen 1","Cours Moyen 2"};
        String[] namesEn = {"Grade 1","Grade 2","Grade 3","Grade 4","Grade 5","Grade 6"};
        for (int i = 0; i < codes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(cycle).code(codes[i])
                    .nameFr(fr ? namesFr[i] : namesEn[i]).nameEn(namesEn[i]).sequenceOrder(i + 1).build());
            for (String s : new String[]{"A","B"})
                classroomRepository.save(Classroom.builder().tenantId(tenantId).code(codes[i]+"-"+s)
                        .name(codes[i]+" - Classe "+s).academicLevel(lvl).capacity(40).isActive(true).build());
        }
    }

    private void createMiddleWithOrder(UUID tenantId, boolean fr, int order) {
        AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("MIDDLE").nameFr("Collège").nameEn("Middle School").sequenceOrder(order).build());
        String[] codes   = {"6EME","5EME","4EME","3EME"};
        String[] namesFr = {"6ème","5ème","4ème","3ème"};
        String[] namesEn = {"Grade 7","Grade 8","Grade 9","Grade 10"};
        for (int i = 0; i < codes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(cycle).code(codes[i])
                    .nameFr(fr ? namesFr[i] : namesEn[i]).nameEn(namesEn[i]).sequenceOrder(i + 1).build());
            for (String s : new String[]{"A","B"})
                classroomRepository.save(Classroom.builder().tenantId(tenantId).code(codes[i]+"-"+s)
                        .name((fr ? namesFr[i] : namesEn[i])+" "+s).academicLevel(lvl).capacity(45).isActive(true).build());
        }
    }

    private void createHighWithOrder(UUID tenantId, boolean fr, int order) {
        AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("HIGH").nameFr("Lycée").nameEn("High School").sequenceOrder(order).build());
        String[] codes   = {"2ND_LE","2ND_S","1ERE_A4","1ERE_D","1ERE_C","TLE_A4","TLE_D","TLE_C"};
        String[] namesFr = {"Seconde Littéraire","Seconde Scientifique","Première A4","Première D","Première C","Terminale A4","Terminale D","Terminale C"};
        String[] namesEn = {"10th Literary","10th Science","11th A4","11th D","11th C","12th A4","12th D","12th C"};
        for (int i = 0; i < codes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(cycle).code(codes[i])
                    .nameFr(fr ? namesFr[i] : namesEn[i]).nameEn(namesEn[i]).sequenceOrder(i + 1).build());
            classroomRepository.save(Classroom.builder().tenantId(tenantId).code(codes[i]+"-1")
                    .name((fr ? namesFr[i] : namesEn[i])+" 1").academicLevel(lvl).capacity(50).isActive(true).build());
        }
    }

    private void createUniversityWithOrder(UUID tenantId, boolean fr, int order) {
        AcademicCycle licence = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("LICENCE").nameFr("Licence (LMD)").nameEn("Bachelor (LMD)").sequenceOrder(order).build());
        AcademicCycle master = academicCycleRepository.save(AcademicCycle.builder()
                .tenantId(tenantId).code("MASTER").nameFr("Master (LMD)").nameEn("Master (LMD)").sequenceOrder(order + 1).build());

        String[] tracks   = {"GL","FCG","RIT","MKT"};
        String[] tracksFr = {"Génie Logiciel","Finance & Gestion","Réseaux & Télécoms","Marketing"};
        String[] tracksEn = {"Software Eng.","Finance & Mgmt","Networks & Telecom","Marketing"};

        String[] lCodes = {"L1","L2","L3"};
        String[] lFr    = {"Licence 1","Licence 2","Licence 3"};
        String[] lEn    = {"1st Bachelor","2nd Bachelor","3rd Bachelor"};
        for (int i = 0; i < lCodes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(licence).code(lCodes[i])
                    .nameFr(fr ? lFr[i] : lEn[i]).nameEn(lEn[i]).sequenceOrder(i+1).build());
            for (int t = 0; t < tracks.length; t++)
                classroomRepository.save(Classroom.builder().tenantId(tenantId)
                        .code(lCodes[i]+"-"+tracks[t]).name(lCodes[i]+" "+(fr ? tracksFr[t] : tracksEn[t]))
                        .academicLevel(lvl).capacity(60).isActive(true).build());
        }

        String[] mCodes = {"M1","M2"};
        String[] mFr    = {"Master 1","Master 2"};
        String[] mEn    = {"1st Master","2nd Master"};
        for (int i = 0; i < mCodes.length; i++) {
            AcademicLevel lvl = academicLevelRepository.save(AcademicLevel.builder()
                    .tenantId(tenantId).cycle(master).code(mCodes[i])
                    .nameFr(fr ? mFr[i] : mEn[i]).nameEn(mEn[i]).sequenceOrder(i+1).build());
            for (int t = 0; t < tracks.length; t++)
                classroomRepository.save(Classroom.builder().tenantId(tenantId)
                        .code(mCodes[i]+"-"+tracks[t]).name(mCodes[i]+" "+(fr ? tracksFr[t] : tracksEn[t]))
                        .academicLevel(lvl).capacity(40).isActive(true).build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM — Cycles entièrement définis par le client
    // ─────────────────────────────────────────────────────────────────────────
    private void createCustomCycles(SetupWizardDto dto) {
        for (SetupWizardDto.CycleDto cycleDto : dto.getCustomCycles()) {
            AcademicCycle cycle = academicCycleRepository.save(AcademicCycle.builder()
                    .tenantId(dto.getTenantId())
                    .code(cycleDto.getCode())
                    .nameFr(cycleDto.getNameFr())
                    .nameEn(cycleDto.getNameEn())
                    .sequenceOrder(cycleDto.getSequenceOrder())
                    .build());
            if (cycleDto.getLevels() != null) {
                for (int i = 0; i < cycleDto.getLevels().size(); i++) {
                    SetupWizardDto.LevelDto l = cycleDto.getLevels().get(i);
                    academicLevelRepository.save(AcademicLevel.builder()
                            .tenantId(dto.getTenantId())
                            .cycle(cycle)
                            .code(l.getCode())
                            .nameFr(l.getNameFr())
                            .nameEn(l.getNameEn())
                            .sequenceOrder(l.getSequenceOrder() > 0 ? l.getSequenceOrder() : i + 1)
                            .build());
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Périodes académiques (Trimestres / Semestres)
    // ─────────────────────────────────────────────────────────────────────────
    private void createPeriods(SetupWizardDto dto, AcademicYear year) {
        String pType = dto.getPeriodType() != null ? dto.getPeriodType() : "TRIMESTER";
        if ("SEMESTER".equalsIgnoreCase(pType)) {
            academicPeriodRepository.save(AcademicPeriod.builder()
                    .tenantId(dto.getTenantId())
                    .academicYearId(year.getId())
                    .nameFr("Semestre 1").nameEn("Semester 1")
                    .periodType("SEMESTER")
                    .startDate(dto.getYearStartDate())
                    .endDate(dto.getYearStartDate().plusMonths(5))
                    .build());
            academicPeriodRepository.save(AcademicPeriod.builder()
                    .tenantId(dto.getTenantId())
                    .academicYearId(year.getId())
                    .nameFr("Semestre 2").nameEn("Semester 2")
                    .periodType("SEMESTER")
                    .startDate(dto.getYearStartDate().plusMonths(5))
                    .endDate(dto.getYearEndDate())
                    .build());
        } else {
            String[][] trimesters = {
                {"T1","Trimestre 1","Term 1"},
                {"T2","Trimestre 2","Term 2"},
                {"T3","Trimestre 3","Term 3"}
            };
            for (int i = 0; i < trimesters.length; i++) {
                academicPeriodRepository.save(AcademicPeriod.builder()
                        .tenantId(dto.getTenantId())
                        .academicYearId(year.getId())
                        .nameFr(trimesters[i][1])
                        .nameEn(trimesters[i][2])
                        .periodType("TRIMESTER")
                        .startDate(dto.getYearStartDate().plusMonths(i * 3L))
                        .endDate(dto.getYearStartDate().plusMonths((i + 1) * 3L).minusDays(1))
                        .build());
            }
        }
    }
}
