package com.schoolmanager.config.service;

import com.schoolmanager.config.controller.EvaluationAndGradesController.StudentReportDto;
import com.schoolmanager.config.controller.EvaluationAndGradesController.SubjectAverageDto;
import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.StudentEnrollment;
import com.schoolmanager.config.entity.SystemSetting;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.StudentEnrollmentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.repository.SystemSettingRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Service de génération des bulletins scolaires en PDF avec JasperReports.
 *
 * Responsabilités :
 *  1. Charge les données de l'établissement (SystemSetting) depuis la BDD.
 *  2. Calcule les moyennes et rangs des élèves pour la période donnée.
 *  3. Sérialise les données dans les paramètres JasperReports.
 *  4. Sélectionne dynamiquement le template JRXML selon le choix de l'école.
 *  5. Compile et remplit le template JRXML, puis exporte en bytes PDF.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulletinReportService {

    private final SystemSettingRepository systemSettingRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final BulletinCalculationService bulletinCalculationService;

    // === Mentions françaises standard ===
    private static final BigDecimal MENTION_TB = new BigDecimal("16.00");
    private static final BigDecimal MENTION_B  = new BigDecimal("14.00");
    private static final BigDecimal MENTION_AB = new BigDecimal("12.00");
    private static final BigDecimal MENTION_P  = new BigDecimal("10.00");

    /**
     * Résout le chemin JRXML du template selon la préférence de l'école.
     * Les 4 templates disponibles correspondent aux 4 designs de bulletin.
     *
     * @param templateCode  code stocké dans SystemSetting.bulletinTemplate
     * @return chemin classpath vers le fichier JRXML correspondant
     */
    private String resolveTemplatePath(String templateCode) {
        if (templateCode == null) return "reports/bulletin_professional.jrxml";
        return switch (templateCode.toUpperCase()) {
            case "CLASSIC"      -> "reports/bulletin_classic.jrxml";      // Institutionnel (style algérien)
            case "STRUCTURED"   -> "reports/bulletin_structured.jrxml";   // Colonnes prof (style togolais)
            case "MINIMAL"      -> "reports/bulletin_minimal.jrxml";      // Épuré moderne
            default             -> "reports/bulletin_professional.jrxml"; // Premium (défaut)
        };
    }

    /**
     * Génère le PDF des bulletins de TOUTE une classe pour une période donnée.
     *
     * @param classroomId  UUID de la classe
     * @param periodId     UUID de la période académique
     * @param yearId       UUID de l'année académique
     * @return bytes du fichier PDF multi-pages (1 bulletin par élève)
     */
    public byte[] generateClassBulletins(UUID classroomId, UUID periodId, UUID yearId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        SecurityUtils.assertOwnership(classroom.getTenantId());

        List<StudentReportDto> reports = bulletinCalculationService.calculateReports(classroomId, periodId, yearId);

        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT,
                    "Aucune note saisie pour cette classe et cette période.");
        }

        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Paramètres de l'établissement introuvables. Veuillez initialiser votre établissement."));

        return fillAndExportPdf(setting, classroom, reports, yearId, periodId);
    }

    /**
     * Génère le PDF du bulletin d'UN SEUL élève pour une période donnée.
     *
     * @param studentId UUID de l'élève
     * @param periodId  UUID de la période académique
     * @param yearId    UUID de l'année académique
     * @return bytes du fichier PDF (1 page bulletin)
     */
    public byte[] generateStudentBulletin(UUID studentId, UUID periodId, UUID yearId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
        SecurityUtils.assertOwnership(student.getTenantId());

        StudentEnrollment enrollment = studentEnrollmentRepository
                .findByStudentIdAndAcademicYearId(studentId, yearId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cet élève n'est pas inscrit pour cette année académique."));

        Classroom classroom = enrollment.getClassroom();

        List<StudentReportDto> allReports = bulletinCalculationService
                .calculateReports(classroom.getId(), periodId, yearId);

        List<StudentReportDto> studentReports = allReports.stream()
                .filter(r -> r.getStudentId().equals(studentId))
                .collect(Collectors.toList());

        if (studentReports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT,
                    "Aucune note trouvée pour cet élève pour cette période.");
        }

        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Paramètres de l'établissement introuvables."));

        return fillAndExportPdf(setting, classroom, studentReports, yearId, periodId);
    }

    /**
     * Génère un exemple de bulletin PDF (Aperçu) pour un template donné.
     * Utilise des données fictives complètes pour illustrer la mise en page.
     *
     * @param templateCode  Le code du design (CLASSIC, STRUCTURED, PROFESSIONAL, MINIMAL)
     * @return Les bytes du PDF généré
     */
    public byte[] generateTemplatePreview(String templateCode) {
        SystemSetting setting = SystemSetting.builder()
                .institutionName("GROUPE SCOLAIRE EXCELLENCE (APERÇU)")
                .institutionType("PRIVATE")
                .address("123 Rue de la Réussite, Abidjan")
                .phone("+225 01 02 03 04")
                .contactEmail("contact@excellence-ecole.com")
                .website("www.excellence-ecole.com")
                .motto("Rigueur - Travail - Succès")
                .watermarkText("SPECIMEN APERÇU")
                .primaryColor("#1A237E")
                .bulletinTemplate(templateCode)
                .build();

        Classroom classroom = Classroom.builder()
                .id(UUID.randomUUID())
                .name("3ème A (Classe Pilote)")
                .code("3A")
                .build();

        // Créer un rapport de notes fictif avec plusieurs matières
        List<SubjectAverageDto> mockAverages = new ArrayList<>();
        
        mockAverages.add(SubjectAverageDto.builder()
                .subjectCode("MATH")
                .subjectNameFr("Mathématiques")
                .coefficient(new BigDecimal("4"))
                .average(new BigDecimal("16.50"))
                .remarks("Excellent travail. Très bonne participation en classe.")
                .build());

        mockAverages.add(SubjectAverageDto.builder()
                .subjectCode("PC")
                .subjectNameFr("Physique-Chimie")
                .coefficient(new BigDecimal("3"))
                .average(new BigDecimal("14.00"))
                .remarks("Travail sérieux et régulier. Continuez ainsi.")
                .build());

        mockAverages.add(SubjectAverageDto.builder()
                .subjectCode("FR")
                .subjectNameFr("Français")
                .coefficient(new BigDecimal("5"))
                .average(new BigDecimal("15.25"))
                .remarks("Très bonne maîtrise à l'écrit comme à l'oral.")
                .build());

        mockAverages.add(SubjectAverageDto.builder()
                .subjectCode("HIST")
                .subjectNameFr("Histoire-Géographie")
                .coefficient(new BigDecimal("2"))
                .average(new BigDecimal("12.00"))
                .remarks("Assez bon ensemble. Peut encore progresser.")
                .build());

        mockAverages.add(SubjectAverageDto.builder()
                .subjectCode("ANG")
                .subjectNameFr("Anglais")
                .coefficient(new BigDecimal("3"))
                .average(new BigDecimal("17.00"))
                .remarks("Félicitations pour votre aisance linguistique !")
                .build());

        StudentReportDto report = StudentReportDto.builder()
                .studentId(UUID.randomUUID())
                .studentName("Jean DUPONT (Exemple)")
                .registrationNumber("REG-2026-894")
                .subjectsAverages(mockAverages)
                .globalAverage(new BigDecimal("15.15"))
                .rank(2)
                .build();

        List<StudentReportDto> reports = List.of(report);

        return fillAndExportPdf(setting, classroom, reports, UUID.randomUUID(), UUID.randomUUID());
    }

    // ============================================================
    // MÉTHODES PRIVÉES
    // ============================================================

    /**
     * Compile le template JRXML sélectionné, construit la datasource par matière (une ligne par
     * matière et par élève), et exporte en PDF.
     *
     * Chaque rapport d'élève génère un JasperPrint séparé. Tous sont ensuite fusionnés en un seul PDF.
     */
    private byte[] fillAndExportPdf(
            SystemSetting setting,
            Classroom classroom,
            List<StudentReportDto> reports,
            UUID yearId,
            UUID periodId) {

        try {
            // Choisir le template selon la préférence de l'école
            String jrxmlPath = resolveTemplatePath(setting.getBulletinTemplate());
            log.info("Génération du bulletin avec le template '{}' ({})", setting.getBulletinTemplate(), jrxmlPath);

            InputStream jrxmlStream = new ClassPathResource(jrxmlPath).getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);

            List<JasperPrint> allPrints = new ArrayList<>();

            for (StudentReportDto report : reports) {
                // Paramètres communs à l'établissement + contexte élève
                Map<String, Object> params = buildReportParameters(setting, classroom, reports.size(), yearId, periodId);

                // Paramètres spécifiques à l'élève
                params.put("studentName", report.getStudentName());
                params.put("registrationNumber", report.getRegistrationNumber());
                params.put("globalAverage", report.getGlobalAverage() != null
                        ? report.getGlobalAverage().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "—");
                params.put("rank", String.valueOf(report.getRank()));
                params.put("mention", computeMention(report.getGlobalAverage()));

                // Datasource : une ligne par matière
                List<Map<String, Object>> subjectRows = buildSubjectRows(report);
                JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource((Collection) subjectRows);

                JasperPrint print = JasperFillManager.fillReport(jasperReport, params, dataSource);
                
                // Appliquer l'orientation personnalisée configurée
                if ("LANDSCAPE".equalsIgnoreCase(setting.getBulletinOrientation())) {
                    print.setOrientation(net.sf.jasperreports.engine.type.OrientationEnum.LANDSCAPE);
                } else if ("PORTRAIT".equalsIgnoreCase(setting.getBulletinOrientation())) {
                    print.setOrientation(net.sf.jasperreports.engine.type.OrientationEnum.PORTRAIT);
                }
                
                allPrints.add(print);
            }

            // Fusion de tous les JasperPrint en un seul PDF
            return exportAllToPdf(allPrints);

        } catch (JRException e) {
            log.error("Erreur JasperReports lors de la génération du bulletin PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur lors de la génération du PDF : " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la génération du bulletin PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur inattendue lors de la génération du PDF.");
        }
    }

    /**
     * Construit une liste de lignes (une par matière) pour la datasource JasperReports.
     * Chaque ligne contient les champs attendus par les templates JRXML.
     */
    private List<Map<String, Object>> buildSubjectRows(StudentReportDto report) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<SubjectAverageDto> subjects = report.getSubjectsAverages();
        if (subjects == null || subjects.isEmpty()) return rows;

        // Pour le calcul du rang par matière, on ferait un enrichissement ici
        // Pour l'instant on met un rang générique (à enrichir avec BulletinCalculationService)
        for (SubjectAverageDto sub : subjects) {
            Map<String, Object> row = new HashMap<>();
            row.put("subjectName",  sub.getSubjectNameFr() != null ? sub.getSubjectNameFr() : sub.getSubjectCode());
            row.put("coefficient",  sub.getCoefficient() != null ? sub.getCoefficient().toPlainString() : "1");
            row.put("average",      sub.getAverage() != null
                    ? sub.getAverage().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "—");
            // total = coef × average
            row.put("total",        (sub.getCoefficient() != null && sub.getAverage() != null)
                    ? sub.getCoefficient().multiply(sub.getAverage())
                            .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "—");
            row.put("rank",         "—");          // à enrichir
            row.put("classMax",     "—");          // à enrichir
            row.put("classMin",     "—");          // à enrichir
            row.put("classAverage", "—");          // à enrichir
            row.put("remarks",      sub.getRemarks() != null ? sub.getRemarks() : "");
            row.put("teacherName",  "");           // à enrichir avec les données prof

            // Champ double pour les comparaisons conditionnelles de couleur dans JRXML
            row.put("averageDouble", sub.getAverage() != null ? sub.getAverage().doubleValue() : null);

            rows.add(row);
        }
        return rows;
    }

    /**
     * Exporte une liste de JasperPrint fusionnés en un seul PDF byte array.
     */
    private byte[] exportAllToPdf(List<JasperPrint> prints) throws JRException {
        if (prints.isEmpty()) return new byte[0];

        // Fusionner toutes les pages dans le premier JasperPrint
        JasperPrint merged = prints.get(0);
        for (int i = 1; i < prints.size(); i++) {
            for (Object page : prints.get(i).getPages()) {
                merged.addPage((net.sf.jasperreports.engine.JRPrintPage) page);
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JasperExportManager.exportReportToPdfStream(merged, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Construit la Map des paramètres communs JasperReports (établissement, classe, période).
     * Ces paramètres sont partagés par tous les templates JRXML.
     */
    private Map<String, Object> buildReportParameters(
            SystemSetting setting,
            Classroom classroom,
            int totalStudents,
            UUID yearId,
            UUID periodId) {

        Map<String, Object> params = new HashMap<>();

        // === Paramètres de l'établissement ===
        params.put("institutionName",    nullSafe(setting.getInstitutionName()));
        params.put("institutionType",    resolveSchoolType(setting.getInstitutionType()));
        params.put("institutionAddress", nullSafe(setting.getAddress()));
        params.put("institutionPhone",   nullSafe(setting.getPhone()));
        params.put("institutionEmail",   nullSafe(setting.getContactEmail()));
        params.put("institutionWebsite", nullSafe(setting.getWebsite()));
        params.put("motto",              nullSafe(setting.getMotto()));
        params.put("primaryColor",       setting.getPrimaryColor() != null ? setting.getPrimaryColor() : "#1A237E");
        params.put("accentColor",        setting.getPrimaryColor() != null ? setting.getPrimaryColor() : "#263238");
        params.put("primaryColorImage",  generateColorImage(setting.getPrimaryColor() != null ? setting.getPrimaryColor() : "#1A237E"));
        params.put("accentColorImage",   generateColorImage(setting.getPrimaryColor() != null ? setting.getPrimaryColor() : "#263238"));

        // Filigrane : texte configuré ou nom de l'école en fallback
        String watermark = (setting.getWatermarkText() != null && !setting.getWatermarkText().isBlank())
                ? setting.getWatermarkText()
                : setting.getInstitutionName();
        params.put("watermarkText", watermark);

        // Logo (Base64 string pour les nouveaux templates, InputStream pour l'ancien)
        String normalizedLogo = normalizeLogoToPngBase64(setting.getLogoBase64());
        params.put("logoBase64",    normalizedLogo);
        params.put("LOGO_IMAGE",    resolveLogo(normalizedLogo, setting.getLogoUrl(), setting.getTenantId()));

        // === Contexte académique ===
        params.put("className",         nullSafe(classroom.getName()));
        params.put("classEffective",     String.valueOf(totalStudents));
        params.put("periodLabel",        "Période : " + periodId.toString()); // à enrichir avec label réel
        params.put("academicYear",       yearId.toString());                   // à enrichir avec libellé réel
        params.put("absences",           "0");                                 // à enrichir

        // Directeur / principal
        params.put("principalName",      "");

        // Compatibilité avec les anciens paramètres de l'ancien template
        params.put("SCHOOL_NAME",        nullSafe(setting.getInstitutionName()));
        params.put("SCHOOL_TYPE",        resolveSchoolType(setting.getInstitutionType()));
        params.put("SCHOOL_ADDRESS",     nullSafe(setting.getAddress()));
        params.put("SCHOOL_PHONE",       nullSafe(setting.getPhone()));
        params.put("SCHOOL_EMAIL",       nullSafe(setting.getContactEmail()));
        params.put("SCHOOL_WEBSITE",     nullSafe(setting.getWebsite()));
        params.put("SCHOOL_MOTTO",       nullSafe(setting.getMotto()));
        params.put("SCHOOL_PRIMARY_COLOR", setting.getPrimaryColor() != null ? setting.getPrimaryColor() : "#1A237E");
        params.put("WATERMARK_TEXT",     watermark);
        params.put("YEAR_CODE",          yearId.toString());
        params.put("PERIOD_NAME",        periodId.toString());
        params.put("CLASS_NAME",         nullSafe(classroom.getName()));
        params.put("CLASS_CODE",         nullSafe(classroom.getCode()));
        params.put("TOTAL_STUDENTS",     totalStudents);
        params.put("GENERATED_DATE",     LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        return params;
    }

    /**
     * Résout l'image du logo à partir du Base64 stocké ou de l'URL.
     * Retourne un InputStream pour JasperReports, ou null si aucun logo n'est disponible.
     */
    private InputStream resolveLogo(String normalizedLogoBase64, String logoUrl, UUID tenantId) {
        if (normalizedLogoBase64 != null && !normalizedLogoBase64.isBlank()) {
            try {
                String base64Data = normalizedLogoBase64;
                if (base64Data.contains(",")) {
                    base64Data = base64Data.substring(base64Data.indexOf(',') + 1);
                }
                byte[] logoBytes = Base64.getDecoder().decode(base64Data);
                return new ByteArrayInputStream(logoBytes);
            } catch (Exception e) {
                log.warn("Impossible de décoder le logo Base64 normalisé pour le tenant {} : {}", tenantId, e.getMessage());
            }
        }
        if (logoUrl != null && !logoUrl.isBlank()) {
            try {
                return new java.net.URL(logoUrl).openStream();
            } catch (Exception e) {
                log.warn("Impossible de charger le logo depuis l'URL {} : {}", logoUrl, e.getMessage());
            }
        }
        return null;
    }

    /** Calcule la mention scolaire française à partir de la moyenne générale. */
    private String computeMention(BigDecimal average) {
        if (average == null) return "—";
        if (average.compareTo(MENTION_TB) >= 0) return "TRÈS BIEN";
        if (average.compareTo(MENTION_B)  >= 0) return "BIEN";
        if (average.compareTo(MENTION_AB) >= 0) return "ASSEZ BIEN";
        if (average.compareTo(MENTION_P)  >= 0) return "PASSABLE";
        return "INSUFFISANT";
    }

    /** Traduit le code du type d'établissement en libellé lisible. */
    private String resolveSchoolType(String typeCode) {
        if (typeCode == null) return "";
        return switch (typeCode.toUpperCase()) {
            case "PUBLIC"       -> "Établissement Public";
            case "PRIVATE"      -> "Établissement Privé";
            case "SEMI_PRIVATE" -> "Établissement Semi-Privé";
            default             -> typeCode;
        };
    }

    /** Retourne une chaîne vide si la valeur est null (évite les NullPointerException dans JasperReports). */
    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    /**
     * Génère une BufferedImage unie de 1x1 pixel correspondant à un code couleur HEX.
     * Utilisé comme source d'image pour colorer dynamiquement des formes dans le JRXML.
     */
    private BufferedImage generateColorImage(String hexColor) {
        String colorStr = (hexColor != null && hexColor.startsWith("#")) ? hexColor : "#1A237E";
        try {
            Color color = Color.decode(colorStr);
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, color.getRGB());
            return img;
        } catch (Exception e) {
            log.warn("Impossible de décoder la couleur HEX '{}', repli sur le bleu par défaut : {}", hexColor, e.getMessage());
            try {
                Color fallbackColor = Color.decode("#1A237E");
                BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                img.setRGB(0, 0, fallbackColor.getRGB());
                return img;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * Normalise n'importe quelle image encodée en Base64 (y compris WebP) en format PNG standard
     * pour assurer un décodage fluide et sans erreur dans JasperReports (Batik SVG crash fix).
     */
    private String normalizeLogoToPngBase64(String base64Input) {
        if (base64Input == null || base64Input.trim().isEmpty()) {
            return "";
        }
        try {
            String cleanBase64 = base64Input;
            if (cleanBase64.contains(",")) {
                cleanBase64 = cleanBase64.split(",")[1];
            }
            byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64.trim());
            
            // Tenter de lire l'image avec TwelveMonkeys ImageIO (supportant WebP)
            ByteArrayInputStream bais = new ByteArrayInputStream(decodedBytes);
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                log.warn("L'image fournie n'a pas pu être décodée par ImageIO.");
                return base64Input; // Retourner la chaîne d'origine en fallback
            }

            // Ré-encoder en PNG standard
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean success = ImageIO.write(image, "png", baos);
            if (!success) {
                log.warn("Impossible de ré-encoder l'image décodée au format PNG.");
                return base64Input;
            }

            byte[] pngBytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            log.warn("Erreur lors de la normalisation du logo Base64 en PNG : {}", e.getMessage());
            return base64Input; // Fallback
        }
    }
}
