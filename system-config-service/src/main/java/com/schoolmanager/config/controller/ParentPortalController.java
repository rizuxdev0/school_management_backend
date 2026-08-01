package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour le Portail Parent.
 * Fournit aux parents d'élèves un accès sécurisé en lecture seule aux données
 * scolaires de leurs enfants (Notes, Assiduité, Emploi du Temps).
 *
 * <p>La validation anti-IDOR est basée sur le numéro de téléphone ou l'e-mail
 * stockés dans le jeton JWT du parent.</p>
 */
@RestController
@RequestMapping("/api/v1/system/parent")
@RequiredArgsConstructor
public class ParentPortalController {

    private final StudentRepository studentRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TimetableSlotRepository timetableSlotRepository;

    /**
     * Récupère la liste des enfants (élèves) associés au parent connecté.
     */
    @GetMapping("/students")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<Student>> getMyChildren() {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        String phone = principal.getPhoneNumber();
        String email = principal.getEmail();

        if ((phone == null || phone.isBlank()) && (email == null || email.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune coordonnée (téléphone/email) disponible dans votre profil parent.");
        }

        List<Student> children = studentRepository.findLinkedStudents(tenantId, phone, email);
        return ResponseEntity.ok(children);
    }

    /**
     * Récupère les notes d'un enfant spécifique.
     */
    @GetMapping("/students/{studentId}/grades")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<StudentGrade>> getChildGrades(@PathVariable UUID studentId) {
        assertParentOwnership(studentId);
        List<StudentGrade> grades = studentGradeRepository.findByStudentId(studentId);
        return ResponseEntity.ok(grades);
    }

    /**
     * Récupère les absences d'un enfant spécifique.
     */
    @GetMapping("/students/{studentId}/attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<Attendance>> getChildAttendance(@PathVariable UUID studentId) {
        assertParentOwnership(studentId);
        List<Attendance> attendanceList = attendanceRepository.findByStudentId(studentId);
        return ResponseEntity.ok(attendanceList);
    }

    /**
     * Récupère l'emploi du temps de la classe d'un enfant spécifique.
     */
    @GetMapping("/students/{studentId}/timetable")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<TimetableSlot>> getChildTimetable(@PathVariable UUID studentId) {
        assertParentOwnership(studentId);
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        List<StudentEnrollment> enrollments = studentEnrollmentRepository.findByStudentId(studentId);
        if (enrollments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cet enfant n'est inscrit dans aucune classe.");
        }

        // Récupère la classe de l'inscription la plus récente
        UUID classroomId = enrollments.get(enrollments.size() - 1).getClassroom().getId();
        List<TimetableSlot> slots = timetableSlotRepository.findByTenantIdAndClassroomIdOrderByDayOfWeekAscStartTimeAsc(tenantId, classroomId);
        return ResponseEntity.ok(slots);
    }

    // ------------------------------------------------------------------
    // Sécurité Anti-IDOR pour parents
    // ------------------------------------------------------------------

    /**
     * Valide que l'élève demandé est bien lié au parent connecté.
     * Lève une 403 Forbidden en cas de violation d'IDOR.
     */
    private void assertParentOwnership(UUID studentId) {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Student child = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable."));

        // Anti-IDOR inter-tenant
        if (!tenantId.equals(child.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès interdit.");
        }

        String phone = principal.getPhoneNumber();
        String email = principal.getEmail();

        boolean isPhoneMatch = phone != null && !phone.isBlank() && phone.equals(child.getParentPhone());
        boolean isEmailMatch = email != null && !email.isBlank() && email.equals(child.getEmail());

        if (!isPhoneMatch && !isEmailMatch) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : cet élève n'est pas associé à votre profil de parent.");
        }
    }
}
