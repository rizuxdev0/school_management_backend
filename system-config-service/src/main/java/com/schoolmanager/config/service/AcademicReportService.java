package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Room;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.entity.SystemSetting;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.RoomRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicReportService {

    private final StudentRepository studentRepository;
    private final ClassroomRepository classroomRepository;
    private final RoomRepository roomRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final com.schoolmanager.config.repository.StudentEnrollmentRepository studentEnrollmentRepository;
    private final com.schoolmanager.config.repository.AttendanceRepository attendanceRepository;
    private final com.schoolmanager.config.repository.AcademicYearRepository academicYearRepository;

    public byte[] generateStudentsListPdf(UUID tenantId) {
        List<Student> students = studentRepository.findByTenantId(tenantId);
        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);

        Map<String, Object> params = getBaseParams(setting);
        List<Map<String, Object>> rows = new ArrayList<>();

        if (students.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("registrationNumber", "-");
            emptyRow.put("fullName", "Aucun élève inscrit");
            emptyRow.put("dateOfBirth", "-");
            emptyRow.put("gender", "-");
            emptyRow.put("parentContact", "-");
            emptyRow.put("email", "-");
            rows.add(emptyRow);
        } else {
            for (Student s : students) {
                Map<String, Object> row = new HashMap<>();
                row.put("registrationNumber", nullSafe(s.getRegistrationNumber(), "-"));
                row.put("fullName", nullSafe(s.getLastName() + " " + s.getFirstName(), ""));
                
                String dobStr = "-";
                if (s.getDateOfBirth() != null) {
                    dobStr = s.getDateOfBirth().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
                row.put("dateOfBirth", dobStr);
                
                row.put("gender", nullSafe(s.getGender(), "-"));
                row.put("parentContact", nullSafe(s.getParentName(), "") + " (" + nullSafe(s.getParentPhone(), "") + ")");
                row.put("email", nullSafe(s.getEmail(), "-"));
                rows.add(row);
            }
        }

        return compileAndExport("reports/students_report.jrxml", params, rows);
    }

    public byte[] generateClassroomsListPdf(UUID tenantId) {
        List<Classroom> classrooms = classroomRepository.findByTenantId(tenantId);
        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);

        Map<String, Object> params = getBaseParams(setting);
        List<Map<String, Object>> rows = new ArrayList<>();

        if (classrooms.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("code", "-");
            emptyRow.put("name", "Aucune classe configurée");
            emptyRow.put("academicLevelName", "-");
            emptyRow.put("capacity", 0);
            emptyRow.put("status", "-");
            rows.add(emptyRow);
        } else {
            for (Classroom c : classrooms) {
                Map<String, Object> row = new HashMap<>();
                row.put("code", nullSafe(c.getCode(), "-"));
                row.put("name", nullSafe(c.getName(), ""));
                row.put("academicLevelName", c.getAcademicLevel() != null ? nullSafe(c.getAcademicLevel().getNameFr(), "-") : "-");
                row.put("capacity", c.getCapacity() != null ? c.getCapacity() : 30);
                row.put("status", c.getIsActive() != false ? "Actif" : "Inactif");
                rows.add(row);
            }
        }

        return compileAndExport("reports/classrooms_report.jrxml", params, rows);
    }

    public byte[] generateRoomsListPdf(UUID tenantId) {
        List<Room> rooms = roomRepository.findByTenantId(tenantId);
        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);

        Map<String, Object> params = getBaseParams(setting);
        List<Map<String, Object>> rows = new ArrayList<>();

        if (rooms.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("code", "-");
            emptyRow.put("name", "Aucune salle ou amphi");
            emptyRow.put("capacity", 0);
            emptyRow.put("roomType", "-");
            emptyRow.put("building", "-");
            emptyRow.put("status", "-");
            rows.add(emptyRow);
        } else {
            for (Room r : rooms) {
                Map<String, Object> row = new HashMap<>();
                row.put("code", nullSafe(r.getCode(), "-"));
                row.put("name", nullSafe(r.getName(), ""));
                row.put("capacity", r.getCapacity() != null ? r.getCapacity() : 40);
                row.put("roomType", translateRoomType(r.getRoomType()));
                row.put("building", nullSafe(r.getBuilding(), "Principal"));
                row.put("status", r.getIsActive() != false ? "Disponible" : "Maintenance");
                rows.add(row);
            }
        }

        return compileAndExport("reports/rooms_report.jrxml", params, rows);
    }

    public byte[] generateAttendanceListPdf(UUID tenantId, UUID classroomId, java.time.LocalDate date) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);

        Map<String, Object> params = getBaseParams(setting);
        params.put("classroomName", nullSafe(classroom.getName(), ""));
        String dateStr = date != null ? date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-";
        params.put("attendanceDate", dateStr);

        List<com.schoolmanager.config.entity.StudentEnrollment> enrollments = studentEnrollmentRepository.findByClassroomId(classroomId);
        List<com.schoolmanager.config.entity.Attendance> attendances = date != null ?
                attendanceRepository.findByClassroomIdAndAttendanceDate(classroomId, date) : Collections.emptyList();

        Map<UUID, com.schoolmanager.config.entity.Attendance> attMap = new HashMap<>();
        for (com.schoolmanager.config.entity.Attendance att : attendances) {
            if (att.getStudent() != null && att.getStudent().getId() != null) {
                attMap.put(att.getStudent().getId(), att);
            }
        }

        List<Student> students = enrollments.stream()
                .map(com.schoolmanager.config.entity.StudentEnrollment::getStudent)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Student::getLastName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Student::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        if (students.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("registrationNumber", "-");
            emptyRow.put("fullName", "Aucun élève inscrit dans cette classe");
            emptyRow.put("status", "-");
            emptyRow.put("remarks", "");
            rows.add(emptyRow);
        } else {
            for (Student s : students) {
                Map<String, Object> row = new HashMap<>();
                row.put("registrationNumber", nullSafe(s.getRegistrationNumber(), "-"));
                row.put("fullName", nullSafe(s.getLastName() + " " + s.getFirstName(), ""));
                com.schoolmanager.config.entity.Attendance att = attMap.get(s.getId());
                if (att != null) {
                    row.put("status", translateAttendanceStatus(att.getStatus()));
                    row.put("remarks", nullSafe(att.getRemarks(), ""));
                } else {
                    row.put("status", ".");
                    row.put("remarks", "");
                }
                rows.add(row);
            }
        }

        return compileAndExport("reports/attendance_report.jrxml", params, rows);
    }

    public byte[] generateAttendanceListRangePdf(UUID tenantId, UUID classroomId, LocalDate startDate, LocalDate endDate) {
        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);
        Map<String, Object> params = getBaseParams(setting);
        params.put("generatedDate", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        params.put("classroomName", nullSafe(classroom.getName(), ""));
        String dateStr = (startDate != null && endDate != null) ?
                "Du " + startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " au " + endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-";
        params.put("attendanceDate", dateStr);

        List<com.schoolmanager.config.entity.StudentEnrollment> enrollments = studentEnrollmentRepository.findByClassroomId(classroomId);
        List<com.schoolmanager.config.entity.Attendance> attendances = (startDate != null && endDate != null) ?
                attendanceRepository.findByClassroomIdAndAttendanceDateBetween(classroomId, startDate, endDate) : Collections.emptyList();

        Map<UUID, List<com.schoolmanager.config.entity.Attendance>> attMap = new HashMap<>();
        for (com.schoolmanager.config.entity.Attendance att : attendances) {
            if (att.getStudent() != null && att.getStudent().getId() != null) {
                attMap.computeIfAbsent(att.getStudent().getId(), k -> new ArrayList<>()).add(att);
            }
        }

        List<Student> students = enrollments.stream()
                .map(com.schoolmanager.config.entity.StudentEnrollment::getStudent)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Student::getLastName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Student::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        if (students.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("registrationNumber", "-");
            emptyRow.put("fullName", "Aucun élève inscrit dans cette classe");
            emptyRow.put("status", "-");
            emptyRow.put("remarks", "");
            rows.add(emptyRow);
        } else {
            for (Student s : students) {
                Map<String, Object> row = new HashMap<>();
                row.put("registrationNumber", nullSafe(s.getRegistrationNumber(), "-"));
                row.put("fullName", nullSafe(s.getLastName() + " " + s.getFirstName(), ""));
                List<com.schoolmanager.config.entity.Attendance> stdAtts = attMap.getOrDefault(s.getId(), Collections.emptyList());
                if (!stdAtts.isEmpty()) {
                    long presentCount = stdAtts.stream().filter(a -> "PRESENT".equalsIgnoreCase(a.getStatus())).count();
                    long absentCount = stdAtts.stream().filter(a -> "ABSENT".equalsIgnoreCase(a.getStatus())).count();
                    long lateCount = stdAtts.stream().filter(a -> "LATE".equalsIgnoreCase(a.getStatus())).count();
                    long excusedCount = stdAtts.stream().filter(a -> Boolean.TRUE.equals(a.getIsExcused())).count();

                    row.put("status", "Présent : " + presentCount + " | Absent : " + absentCount + " | Retard : " + lateCount);
                    row.put("remarks", excusedCount > 0 ? (excusedCount + " abs./ret. excusé(s)") : "Assiduité normale");
                } else {
                    row.put("status", "Aucun appel enregistré");
                    row.put("remarks", "-");
                }
                rows.add(row);
            }
        }

        return compileAndExport("reports/attendance_report.jrxml", params, rows);
    }

    public byte[] generateSchoolCertificatePdf(UUID tenantId, UUID studentId, String lang) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
        if (!student.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }

        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);
        Map<String, Object> params = getBaseParams(setting);

        // Libellé du signataire adapté à la langue et au nom du directeur s'il est renseigné
        String directorName = (setting != null && setting.getDirectorName() != null && !setting.getDirectorName().isBlank())
                ? setting.getDirectorName() : null;
        String templateName = "reports/school_certificate_fr.jrxml";
        String signatory;
        if ("en".equalsIgnoreCase(lang)) {
            templateName = "reports/school_certificate_en.jrxml";
            signatory = directorName != null ? directorName : "The General Director";
        } else {
            signatory = directorName != null ? directorName : "Le Directeur Général";
        }

        // Classe courante
        String classroomName = "Non inscrit";
        String academicYear = "N/A";
        
        // Chercher l'inscription active
        List<com.schoolmanager.config.entity.StudentEnrollment> enrollments = studentEnrollmentRepository.findByStudentId(studentId);
        if (!enrollments.isEmpty()) {
            com.schoolmanager.config.entity.StudentEnrollment active = enrollments.get(0);
            if (active.getClassroom() != null) {
                classroomName = active.getClassroom().getName();
            }
            if (active.getAcademicYearId() != null) {
                academicYear = academicYearRepository.findById(active.getAcademicYearId())
                        .map(com.schoolmanager.config.entity.AcademicYear::getCode)
                        .orElse("N/A");
            }
        }

        // Formater date de naissance
        String dobStr = "-";
        if (student.getDateOfBirth() != null) {
            if ("en".equalsIgnoreCase(lang)) {
                dobStr = student.getDateOfBirth().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH));
            } else {
                dobStr = student.getDateOfBirth().format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
            }
        }

        // Formater date du jour
        String todayStr;
        if ("en".equalsIgnoreCase(lang)) {
            todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH));
        } else {
            todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
        }

        params.put("studentName", student.getLastName() + " " + student.getFirstName());
        params.put("studentDob", dobStr);
        params.put("registrationNumber", nullSafe(student.getRegistrationNumber(), "-"));
        params.put("classroomName", classroomName);
        params.put("academicYear", academicYear);
        params.put("currentDate", todayStr);
        params.put("signatory", signatory);

        // Signature numérique du directeur : injectée depuis SystemSetting.principalSignatureBase64.
        // Le JRXML l'affiche si elle est présente, sinon replie sur le texte statique.
        String rawSignature = (setting != null) ? setting.getPrincipalSignatureBase64() : null;
        params.put("signatureBase64", (rawSignature != null && !rawSignature.isBlank()) ? rawSignature : "");

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        rows.add(row);

        return compileAndExport(templateName, params, rows);
    }

    private String translateAttendanceStatus(String status) {
        if (status == null) return ".";
        switch (status.toUpperCase()) {
            case "PRESENT": return "Présent(e)";
            case "ABSENT": return "Absent(e)";
            case "LATE": return "Retard";
            case "EXCUSED": return "Excusé(e)";
            default: return ".";
        }
    }

    private Map<String, Object> getBaseParams(SystemSetting setting) {
        Map<String, Object> params = new HashMap<>();
        if (setting != null) {
            params.put("institutionName", nullSafe(setting.getInstitutionName(), "Établissement Scolaire"));
            params.put("institutionType", nullSafe(setting.getInstitutionType(), "Enseignement Général & Technique"));
            params.put("institutionAddress", nullSafe(setting.getAddress(), ""));
            params.put("institutionPhone", nullSafe(setting.getPhone(), ""));
            params.put("institutionEmail", nullSafe(setting.getContactEmail(), ""));
            params.put("motto", nullSafe(setting.getMotto(), ""));
            // Normalisation de base64 si présent
            String rawLogo = setting.getLogoBase64();
            params.put("logoBase64", (rawLogo != null && !rawLogo.isBlank()) ? rawLogo : "");
        } else {
            params.put("institutionName", "Établissement Scolaire");
            params.put("institutionType", "Enseignement Général & Technique");
            params.put("institutionAddress", "");
            params.put("institutionPhone", "");
            params.put("institutionEmail", "");
            params.put("motto", "");
            params.put("logoBase64", "");
        }
        params.put("generatedDate", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        return params;
    }

    private byte[] compileAndExport(String templatePath, Map<String, Object> params, List<Map<String, Object>> rows) {
        JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource((Collection) rows);
        try {
            InputStream jrxmlStream = new ClassPathResource(templatePath).getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, params, dataSource);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(print, outputStream);
            return outputStream.toByteArray();
        } catch (JRException e) {
            log.error("Erreur JasperReports pour {} : {}", templatePath, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur génération PDF : " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur inattendue génération PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur génération PDF : " + e.getMessage());
        }
    }

    private String translateRoomType(String type) {
        if (type == null) return "Salle standard";
        switch (type.toUpperCase()) {
            case "CLASSROOM": return "Salle de classe standard";
            case "AMPHITHEATER": return "Amphithéâtre";
            case "LABORATORY": return "Laboratoire de sciences / info";
            case "LIBRARY": return "Bibliothèque / CDI";
            case "SPORTS": return "Terrain de sport / Gymnase";
            default: return "Autre espace";
        }
    }

    private String nullSafe(String val, String def) {
        if (val == null || val.trim().isEmpty()) {
            return def;
        }
        try {
            byte[] bytes = val.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return val;
        }
    }
}
