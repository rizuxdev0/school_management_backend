package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.AcademicYear;
import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.SystemSetting;
import com.schoolmanager.config.entity.TimetableSlot;
import com.schoolmanager.config.repository.AcademicYearRepository;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.SystemSettingRepository;
import com.schoolmanager.config.repository.TimetableSlotRepository;
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

/**
 * Service de génération de rapport PDF pour l'emploi du temps (Timetable)
 * d'une classe ou promotion à l'aide de JasperReports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableReportService {

    private static final String TIMETABLE_TEMPLATE = "reports/timetable_report.jrxml";

    private final TimetableSlotRepository timetableSlotRepository;
    private final ClassroomRepository classroomRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SystemSettingRepository systemSettingRepository;

    public byte[] generateClassroomTimetablePdf(UUID tenantId, UUID academicYearId, UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable : " + classroomId));

        String yearLabel = academicYearId.toString();
        Optional<AcademicYear> yearOpt = academicYearRepository.findById(academicYearId);
        if (yearOpt.isPresent()) {
            AcademicYear y = yearOpt.get();
            yearLabel = (y.getStartDate() != null && y.getEndDate() != null)
                    ? y.getStartDate().getYear() + "-" + y.getEndDate().getYear()
                    : (y.getCode() != null ? y.getCode() : academicYearId.toString());
        }

        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId).orElse(null);

        List<TimetableSlot> slots = timetableSlotRepository
                .findByTenantIdAndAcademicYearIdAndClassroomIdOrderByDayOfWeekAscStartTimeAsc(tenantId, academicYearId, classroomId);

        Map<String, Object> params = new HashMap<>();
        if (setting != null) {
            params.put("institutionName", nullSafe(setting.getInstitutionName(), "Établissement Scolaire"));
            params.put("institutionType", nullSafe(setting.getInstitutionType(), "Enseignement Général & Technique"));
            params.put("institutionAddress", nullSafe(setting.getAddress(), ""));
            params.put("institutionPhone", nullSafe(setting.getPhone(), ""));
            params.put("institutionEmail", nullSafe(setting.getContactEmail(), ""));
            params.put("motto", nullSafe(setting.getMotto(), ""));
            params.put("logoBase64", setting.getLogoBase64());
        } else {
            params.put("institutionName", "Établissement Scolaire");
            params.put("institutionType", "Enseignement Général & Technique");
            params.put("institutionAddress", "");
            params.put("institutionPhone", "");
            params.put("institutionEmail", "");
            params.put("motto", "");
            params.put("logoBase64", null);
        }

        params.put("className", nullSafe(classroom.getName(), "Classe"));
        params.put("academicYear", nullSafe(yearLabel, ""));
        params.put("generatedDate", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        List<Map<String, Object>> rows = new ArrayList<>();
        if (slots.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("dayOfWeekFr", "-");
            emptyRow.put("timeRange", "-");
            emptyRow.put("subjectName", "Aucun cours programmé");
            emptyRow.put("teacherName", "-");
            emptyRow.put("room", "-");
            emptyRow.put("sessionType", "");
            rows.add(emptyRow);
        } else {
            for (TimetableSlot slot : slots) {
                Map<String, Object> row = new HashMap<>();
                row.put("dayOfWeekFr", translateDayOfWeek(slot.getDayOfWeek()));
                row.put("timeRange", nullSafe(slot.getStartTime(), "") + " - " + nullSafe(slot.getEndTime(), ""));
                row.put("subjectName", nullSafe(slot.getSubjectNameFr(), ""));
                row.put("teacherName", nullSafe(slot.getTeacherName(), "-"));
                row.put("room", nullSafe(slot.getRoom(), "-"));
                row.put("sessionType", nullSafe(slot.getSessionType(), "COURS"));
                rows.add(row);
            }
        }

        JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource((Collection) rows);

        try {
            InputStream jrxmlStream = new ClassPathResource(TIMETABLE_TEMPLATE).getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, params, dataSource);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(print, outputStream);
            return outputStream.toByteArray();
        } catch (JRException e) {
            log.error("Erreur JasperReports emploi du temps PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur génération PDF emploi du temps : " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur inattendue génération PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur génération PDF emploi du temps : " + e.getMessage());
        }
    }

    private String translateDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null) return "LUNDI";
        switch (dayOfWeek.toUpperCase()) {
            case "MONDAY": return "LUNDI";
            case "TUESDAY": return "MARDI";
            case "WEDNESDAY": return "MERCREDI";
            case "THURSDAY": return "JEUDI";
            case "FRIDAY": return "VENDREDI";
            case "SATURDAY": return "SAMEDI";
            case "SUNDAY": return "DIMANCHE";
            default: return dayOfWeek;
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
