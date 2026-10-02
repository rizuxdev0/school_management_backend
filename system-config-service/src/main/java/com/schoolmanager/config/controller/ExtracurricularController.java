package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.SchoolClub;
import com.schoolmanager.config.entity.ClubEnrollment;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.repository.SchoolClubRepository;
import com.schoolmanager.config.repository.ClubEnrollmentRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.schoolmanager.config.service.NotificationService;

@RestController
@RequestMapping("/api/v1/system/extracurricular")
@RequiredArgsConstructor
public class ExtracurricularController {

    private final SchoolClubRepository schoolClubRepository;
    private final ClubEnrollmentRepository clubEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    // --- CLUBS ---

    @GetMapping("/clubs/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('EXTRACURRICULAR_VIEW')")
    public ResponseEntity<List<SchoolClub>> getClubs(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(schoolClubRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/clubs")
    @PreAuthorize("hasAuthority('EXTRACURRICULAR_EDIT')")
    public ResponseEntity<SchoolClub> saveClub(@RequestBody SchoolClub club) {
        if (!SecurityUtils.isSuperAdmin()) {
            club.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(schoolClubRepository.save(club));
    }

    @DeleteMapping("/clubs/{id}")
    @PreAuthorize("hasAuthority('EXTRACURRICULAR_EDIT')")
    public ResponseEntity<Void> deleteClub(@PathVariable UUID id) {
        SchoolClub club = schoolClubRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        SecurityUtils.assertOwnership(club.getTenantId());
        schoolClubRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- ENROLLMENTS ---

    @GetMapping("/enrollments/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('EXTRACURRICULAR_VIEW')")
    public ResponseEntity<List<ClubEnrollment>> getEnrollments(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(clubEnrollmentRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/enrollments")
    @PreAuthorize("hasAuthority('EXTRACURRICULAR_EDIT')")
    public ResponseEntity<ClubEnrollment> saveEnrollment(@RequestBody ClubEnrollment enr) {
        if (!SecurityUtils.isSuperAdmin()) {
            enr.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (enr.getEnrollmentDate() == null) {
            enr.setEnrollmentDate(LocalDate.now());
        }
        if (enr.getStudent() != null && enr.getStudent().getId() != null) {
            Student s = studentRepository.findById(enr.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            enr.setStudent(s);
        }
        if (enr.getClub() != null && enr.getClub().getId() != null) {
            SchoolClub c = schoolClubRepository.findById(enr.getClub().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
            enr.setClub(c);
        }
        ClubEnrollment saved = clubEnrollmentRepository.save(enr);
        if (saved.getStudent() != null) {
            String title = "Inscription Club Activité";
            String textMsg = String.format("Votre enfant %s %s a été inscrit(e) au club '%s'.",
                    saved.getStudent().getFirstName(), saved.getStudent().getLastName(),
                    saved.getClub() != null ? saved.getClub().getName() : "Activité Périscolaire");
            notificationService.sendNotification(saved.getTenantId(), null, saved.getStudent().getParentPhone(), saved.getStudent().getEmail(), 
                    title, textMsg, "CLUB");
        }
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/enrollments/{id}")
    @PreAuthorize("hasAuthority('EXTRACURRICULAR_EDIT')")
    public ResponseEntity<Void> deleteEnrollment(@PathVariable UUID id) {
        ClubEnrollment enr = clubEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscription club introuvable"));
        SecurityUtils.assertOwnership(enr.getTenantId());
        clubEnrollmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
