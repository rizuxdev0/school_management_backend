package com.schoolmanager.config.service;

import com.schoolmanager.config.dto.ImportResultDto;
import com.schoolmanager.config.entity.AcademicLevel;
import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Room;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.repository.AcademicLevelRepository;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.RoomRepository;
import com.schoolmanager.config.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service pour la génération d'exemplaires à télécharger (Excel .xlsx)
 * et l'importation de fichiers Excel (.xlsx/.xls) ou CSV pour Classes, Élèves, Salles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicImportExportService {

    private final ClassroomRepository classroomRepository;
    private final AcademicLevelRepository academicLevelRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    // =========================================================================================
    // 1. CLASSES & PROMOS (CLASSROOMS)
    // =========================================================================================

    public byte[] generateClassroomTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Classes & Promos");
            createHeaderRow(workbook, sheet, new String[]{
                    "Code (*)", "Nom de la Classe (*)", "Code Niveau Académique", "Capacité Max"
            });

            // Lignes d'exemple
            createDataRow(sheet, 1, new String[]{"CP-A", "Cours Préparatoire - Classe A", "PRIM", "30"});
            createDataRow(sheet, 2, new String[]{"6EME-A", "Sixième A", "COLL", "35"});
            createDataRow(sheet, 3, new String[]{"L1-INFO", "Licence 1 Informatique", "UNIV", "50"});

            autoSizeColumns(sheet, 4);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'exemplaire Excel Classes", e);
            throw new RuntimeException("Erreur de génération de l'exemplaire Excel : " + e.getMessage());
        }
    }

    @Transactional
    public ImportResultDto importClassroomsExcel(UUID tenantId, InputStream inputStream, String filename) {
        ImportResultDto result = new ImportResultDto();
        List<AcademicLevel> levels = academicLevelRepository.findByTenantIdOrderBySequenceOrderAsc(tenantId);
        AcademicLevel defaultLevel = levels.isEmpty() ? null : levels.get(0);

        List<String[]> rows = parseFileToRows(inputStream, filename);
        result.setTotalRows(rows.size());

        int rowNum = 1;
        for (String[] row : rows) {
            rowNum++;
            try {
                String code = getCell(row, 0);
                String name = getCell(row, 1);
                String levelCode = getCell(row, 2);
                String capacityStr = getCell(row, 3);

                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : Les colonnes 'Code' et 'Nom' sont obligatoires.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                if (classroomRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code.trim())) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : La classe avec le code '" + code.trim() + "' existe déjà.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                AcademicLevel level = matchAcademicLevel(levels, levelCode, defaultLevel);
                if (level == null) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : Aucun niveau académique configuré dans l'établissement.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                int capacity = 30;
                if (StringUtils.hasText(capacityStr)) {
                    try {
                        capacity = Integer.parseInt(capacityStr.trim());
                    } catch (NumberFormatException ignored) {}
                }

                Classroom classroom = Classroom.builder()
                        .tenantId(tenantId)
                        .code(code.trim().toUpperCase())
                        .name(name.trim())
                        .academicLevel(level)
                        .capacity(capacity)
                        .isActive(true)
                        .build();

                classroomRepository.save(classroom);
                result.setImportedCount(result.getImportedCount() + 1);
            } catch (Exception e) {
                result.getErrorMessages().add("Ligne " + rowNum + " : Erreur d'import (" + e.getMessage() + ")");
                result.setErrorCount(result.getErrorCount() + 1);
            }
        }
        return result;
    }

    // =========================================================================================
    // 2. FICHES ÉLÈVES (STUDENTS)
    // =========================================================================================

    public byte[] generateStudentTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Fiches Élèves");
            createHeaderRow(workbook, sheet, new String[]{
                    "Matricule", "Prénom (*)", "Nom (*)", "Date Naissance (YYYY-MM-DD) (*)",
                    "Genre (M/F) (*)", "Email", "Téléphone", "Nom Parent/Tuteur", "Téléphone Parent"
            });

            createDataRow(sheet, 1, new String[]{
                    "STU-2026-001", "Koffi", "AMENYO", "2012-05-14", "MALE",
                    "koffi.amenyo@email.com", "+228 90 00 00 01", "Jean AMENYO", "+228 90 00 00 02"
            });
            createDataRow(sheet, 2, new String[]{
                    "STU-2026-002", "Awa", "DIOP", "2013-09-21", "FEMALE",
                    "awa.diop@email.com", "+221 77 00 00 01", "Fatou DIOP", "+221 77 00 00 02"
            });

            autoSizeColumns(sheet, 9);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'exemplaire Excel Élèves", e);
            throw new RuntimeException("Erreur de génération de l'exemplaire Excel : " + e.getMessage());
        }
    }

    @Transactional
    public ImportResultDto importStudentsExcel(UUID tenantId, InputStream inputStream, String filename) {
        ImportResultDto result = new ImportResultDto();
        List<String[]> rows = parseFileToRows(inputStream, filename);
        result.setTotalRows(rows.size());

        int rowNum = 1;
        for (String[] row : rows) {
            rowNum++;
            try {
                String regNum = getCell(row, 0);
                String firstName = getCell(row, 1);
                String lastName = getCell(row, 2);
                String dobStr = getCell(row, 3);
                String genderStr = getCell(row, 4);
                String email = getCell(row, 5);
                String phone = getCell(row, 6);
                String parentName = getCell(row, 7);
                String parentPhone = getCell(row, 8);

                if (!StringUtils.hasText(firstName) || !StringUtils.hasText(lastName) || !StringUtils.hasText(dobStr)) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : Prénom, Nom et Date de Naissance sont obligatoires.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                if (StringUtils.hasText(regNum) && studentRepository.findByTenantIdAndRegistrationNumber(tenantId, regNum.trim()).isPresent()) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : Le matricule '" + regNum.trim() + "' est déjà attribué.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                if (!StringUtils.hasText(regNum)) {
                    regNum = "STU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }

                LocalDate dateOfBirth = parseDate(dobStr);
                if (dateOfBirth == null) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : Format de date de naissance invalide ('" + dobStr + "'). Utilisez YYYY-MM-DD.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                String gender = parseGender(genderStr);

                Student student = Student.builder()
                        .tenantId(tenantId)
                        .registrationNumber(regNum.trim())
                        .firstName(firstName.trim())
                        .lastName(lastName.trim())
                        .dateOfBirth(dateOfBirth)
                        .gender(gender)
                        .email(StringUtils.hasText(email) ? email.trim() : null)
                        .phoneNumber(StringUtils.hasText(phone) ? phone.trim() : null)
                        .parentName(StringUtils.hasText(parentName) ? parentName.trim() : null)
                        .parentPhone(StringUtils.hasText(parentPhone) ? parentPhone.trim() : null)
                        .isActive(true)
                        .build();

                studentRepository.save(student);
                result.setImportedCount(result.getImportedCount() + 1);
            } catch (Exception e) {
                result.getErrorMessages().add("Ligne " + rowNum + " : Erreur d'import (" + e.getMessage() + ")");
                result.setErrorCount(result.getErrorCount() + 1);
            }
        }
        return result;
    }

    // =========================================================================================
    // 3. SALLES & AMPHIS (ROOMS)
    // =========================================================================================

    public byte[] generateRoomTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Salles & Amphis");
            createHeaderRow(workbook, sheet, new String[]{
                    "Code (*)", "Nom de la Salle/Amphi (*)", "Capacité", "Type de Salle", "Bâtiment / Localisation"
            });

            createDataRow(sheet, 1, new String[]{"S-101", "Salle 101 RDC", "35", "CLASSROOM", "Bâtiment Principal"});
            createDataRow(sheet, 2, new String[]{"AMPHI-A", "Amphithéâtre A (Central)", "200", "AMPHITHEATER", "Bâtiment Sciences"});
            createDataRow(sheet, 3, new String[]{"LAB-01", "Laboratoire Informatique 1", "30", "LABORATORY", "Aile Nord - 1er Étage"});

            autoSizeColumns(sheet, 5);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'exemplaire Excel Salles", e);
            throw new RuntimeException("Erreur de génération de l'exemplaire Excel : " + e.getMessage());
        }
    }

    @Transactional
    public ImportResultDto importRoomsExcel(UUID tenantId, InputStream inputStream, String filename) {
        ImportResultDto result = new ImportResultDto();
        List<String[]> rows = parseFileToRows(inputStream, filename);
        result.setTotalRows(rows.size());

        int rowNum = 1;
        for (String[] row : rows) {
            rowNum++;
            try {
                String code = getCell(row, 0);
                String name = getCell(row, 1);
                String capacityStr = getCell(row, 2);
                String roomTypeStr = getCell(row, 3);
                String building = getCell(row, 4);

                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : Le code et le nom de la salle sont obligatoires.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                if (roomRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code.trim())) {
                    result.getErrorMessages().add("Ligne " + rowNum + " : La salle avec le code '" + code.trim() + "' existe déjà.");
                    result.setErrorCount(result.getErrorCount() + 1);
                    continue;
                }

                int capacity = 40;
                if (StringUtils.hasText(capacityStr)) {
                    try {
                        capacity = Integer.parseInt(capacityStr.trim());
                    } catch (NumberFormatException ignored) {}
                }

                String roomType = parseRoomType(roomTypeStr);

                Room room = Room.builder()
                        .tenantId(tenantId)
                        .code(code.trim().toUpperCase())
                        .name(name.trim())
                        .capacity(capacity)
                        .roomType(roomType)
                        .building(StringUtils.hasText(building) ? building.trim() : null)
                        .isActive(true)
                        .build();

                roomRepository.save(room);
                result.setImportedCount(result.getImportedCount() + 1);
            } catch (Exception e) {
                result.getErrorMessages().add("Ligne " + rowNum + " : Erreur d'import (" + e.getMessage() + ")");
                result.setErrorCount(result.getErrorCount() + 1);
            }
        }
        return result;
    }

    // =========================================================================================
    // UTILITAIRES DE CREATION EXCEL & PARSING
    // =========================================================================================

    private void createHeaderRow(Workbook workbook, Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void createDataRow(Sheet sheet, int rowNum, String[] values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
        }
    }

    private void autoSizeColumns(Sheet sheet, int numCols) {
        for (int i = 0; i < numCols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private List<String[]> parseFileToRows(InputStream inputStream, String filename) {
        List<String[]> rows = new ArrayList<>();
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return parseCsvToRows(inputStream);
        } else {
            return parseExcelToRows(inputStream);
        }
    }

    private List<String[]> parseExcelToRows(InputStream inputStream) {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                int lastCellNum = row.getLastCellNum();
                if (lastCellNum <= 0) continue;

                String[] rowData = new String[lastCellNum];
                boolean isEmptyRow = true;
                for (int c = 0; c < lastCellNum; c++) {
                    Cell cell = row.getCell(c);
                    String val = getCellValueAsString(cell);
                    rowData[c] = val;
                    if (StringUtils.hasText(val)) {
                        isEmptyRow = false;
                    }
                }
                if (!isEmptyRow) {
                    rows.add(rowData);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la lecture du fichier Excel", e);
            throw new RuntimeException("Fichier Excel invalide ou corrompu : " + e.getMessage());
        }
        return rows;
    }

    private List<String[]> parseCsvToRows(InputStream inputStream) {
        List<String[]> rows = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setDelimiter(';').build())) {
            boolean firstRow = true;
            for (CSVRecord record : csvParser) {
                if (firstRow) {
                    firstRow = false;
                    continue; // Skip header
                }
                String[] rowData = new String[record.size()];
                boolean isEmptyRow = true;
                for (int i = 0; i < record.size(); i++) {
                    String val = record.get(i);
                    rowData[i] = val;
                    if (StringUtils.hasText(val)) {
                        isEmptyRow = false;
                    }
                }
                if (!isEmptyRow) {
                    rows.add(rowData);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la lecture du fichier CSV", e);
            throw new RuntimeException("Fichier CSV invalide : " + e.getMessage());
        }
        return rows;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.toString();
                } else {
                    double val = cell.getNumericCellValue();
                    if (val == (long) val) {
                        yield String.valueOf((long) val);
                    } else {
                        yield String.valueOf(val);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String getCell(String[] row, int idx) {
        if (row == null || idx >= row.length) return "";
        return row[idx] != null ? row[idx].trim() : "";
    }

    private AcademicLevel matchAcademicLevel(List<AcademicLevel> levels, String codeOrName, AcademicLevel defaultLevel) {
        if (!StringUtils.hasText(codeOrName)) {
            return defaultLevel;
        }
        String clean = codeOrName.trim().toUpperCase();
        for (AcademicLevel level : levels) {
            if (clean.equalsIgnoreCase(level.getCode()) ||
                (level.getNameFr() != null && clean.equalsIgnoreCase(level.getNameFr().trim())) ||
                (level.getNameEn() != null && clean.equalsIgnoreCase(level.getNameEn().trim()))) {
                return level;
            }
        }
        return defaultLevel;
    }

    private LocalDate parseDate(String dobStr) {
        if (!StringUtils.hasText(dobStr)) return null;
        String clean = dobStr.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(clean, formatter);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String parseGender(String genderStr) {
        if (!StringUtils.hasText(genderStr)) return "MALE";
        String clean = genderStr.trim().toUpperCase();
        if (clean.startsWith("F") || clean.contains("FEM") || clean.contains("FILLE")) {
            return "FEMALE";
        }
        return "MALE";
    }

    private String parseRoomType(String typeStr) {
        if (!StringUtils.hasText(typeStr)) return "CLASSROOM";
        String clean = typeStr.trim().toUpperCase();
        if (clean.contains("AMPHI")) return "AMPHITHEATER";
        if (clean.contains("LAB")) return "LABORATORY";
        if (clean.contains("LIBR") || clean.contains("BIBLIO")) return "LIBRARY";
        if (clean.contains("SPORT") || clean.contains("GYM")) return "SPORTS";
        return "CLASSROOM";
    }
}
