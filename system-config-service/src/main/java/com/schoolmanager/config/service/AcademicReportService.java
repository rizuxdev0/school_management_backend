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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicReportService {

    private final StudentRepository studentRepository;
    private final ClassroomRepository classroomRepository;
    private final RoomRepository roomRepository;
    private final SystemSettingRepository systemSettingRepository;

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

    private Map<String, Object> getBaseParams(SystemSetting setting) {
        Map<String, Object> params = new HashMap<>();
        if (setting != null) {
            params.put("institutionName", nullSafe(setting.getInstitutionName(), "Établissement Scolaire"));
            params.put("institutionType", nullSafe(setting.getInstitutionType(), "Enseignement Général & Technique"));
            params.put("institutionAddress", nullSafe(setting.getAddress(), ""));
            params.put("institutionPhone", nullSafe(setting.getPhone(), ""));
            params.put("institutionEmail", nullSafe(setting.getContactEmail(), ""));
            params.put("motto", nullSafe(setting.getMotto(), ""));
        } else {
            params.put("institutionName", "Établissement Scolaire");
            params.put("institutionType", "Enseignement Général & Technique");
            params.put("institutionAddress", "");
            params.put("institutionPhone", "");
            params.put("institutionEmail", "");
            params.put("motto", "");
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
