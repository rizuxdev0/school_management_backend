package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.Attendance;
import com.schoolmanager.config.entity.Classroom;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.repository.AttendanceRepository;
import com.schoolmanager.config.repository.ClassroomRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service métier dédié à la gestion des présences et assiduités scolaires.
 * Gère l'isolation multi-tenant, la détection des absences/retards et la notification aux tuteurs.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final ClassroomRepository classroomRepository;
    private final AcademicReportService academicReportService;
    private final NotificationService notificationService;

    /**
     * Récupère la liste des présences d'une classe pour une date donnée.
     */
    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceByClassroomAndDate(UUID classroomId, LocalDate date) {
        return attendanceRepository.findByClassroomIdAndAttendanceDate(classroomId, date);
    }

    /**
     * Génère le rapport PDF d'appel d'une classe pour un jour donné.
     */
    @Transactional(readOnly = true)
    public byte[] generateAttendanceListPdf(UUID classroomId, LocalDate date) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return academicReportService.generateAttendanceListPdf(tenantId, classroomId, date);
    }

    /**
     * Génère le rapport PDF d'assiduité d'une classe sur une plage de dates.
     */
    @Transactional(readOnly = true)
    public byte[] generateAttendanceListRangePdf(UUID classroomId, LocalDate startDate, LocalDate endDate) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return academicReportService.generateAttendanceListRangePdf(tenantId, classroomId, startDate, endDate);
    }

    /**
     * Enregistre ou met à jour la liste des présences (appel) et notifie automatiquement les parents si retard ou absence.
     */
    @Transactional
    public List<Attendance> saveAttendanceList(List<Attendance> attendanceList) {
        List<Attendance> saved = new ArrayList<>();
        for (Attendance att : attendanceList) {
            if (!SecurityUtils.isSuperAdmin()) {
                att.setTenantId(SecurityUtils.getCurrentTenantId());
            }

            // Résolution de l'entité Student persistée
            if (att.getStudent() != null && att.getStudent().getId() != null) {
                Student s = studentRepository.findById(att.getStudent().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
                SecurityUtils.assertOwnership(s.getTenantId());
                att.setStudent(s);
            }

            // Résolution de l'entité Classroom persistée
            if (att.getClassroom() != null && att.getClassroom().getId() != null) {
                Classroom c = classroomRepository.findById(att.getClassroom().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
                SecurityUtils.assertOwnership(c.getTenantId());
                att.setClassroom(c);
            }

            Optional<Attendance> existing = attendanceRepository.findByStudentIdAndAttendanceDate(
                    att.getStudent() != null ? att.getStudent().getId() : null,
                    att.getAttendanceDate()
            );

            Attendance savedAtt;
            if (existing.isPresent()) {
                Attendance e = existing.get();
                e.setStatus(att.getStatus());
                e.setIsExcused(att.getIsExcused());
                e.setRemarks(att.getRemarks());
                savedAtt = attendanceRepository.save(e);
            } else {
                savedAtt = attendanceRepository.save(att);
            }
            saved.add(savedAtt);

            // Notification automatique en cas d'absence ou retard
            if (savedAtt.getStudent() != null && ("ABSENT".equals(savedAtt.getStatus()) || "LATE".equals(savedAtt.getStatus()))) {
                Student s = savedAtt.getStudent();
                String statusLabel = "ABSENT".equals(savedAtt.getStatus()) ? "Absence" : "Retard";
                String textMsg = String.format("Votre enfant %s %s a été signalé %s le %s.",
                        s.getFirstName(), s.getLastName(),
                        "ABSENT".equals(savedAtt.getStatus()) ? "absent(e)" : "en retard",
                        savedAtt.getAttendanceDate().toString());
                notificationService.sendNotification(s.getTenantId(), null, s.getParentPhone(), s.getEmail(),
                        statusLabel + " de votre enfant", textMsg, "ATTENDANCE");
            }
        }
        return saved;
    }
}
