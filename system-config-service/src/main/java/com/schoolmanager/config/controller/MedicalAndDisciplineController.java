package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.DisciplinaryIncident;
import com.schoolmanager.config.entity.MedicalRecord;
import com.schoolmanager.config.repository.DisciplinaryIncidentRepository;
import com.schoolmanager.config.repository.MedicalRecordRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la santé/infirmerie et la vie scolaire/discipline.
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/medical-discipline")
@RequiredArgsConstructor
public class MedicalAndDisciplineController {

    private final MedicalRecordRepository medicalRecordRepository;
    private final DisciplinaryIncidentRepository disciplinaryIncidentRepository;

    // ==================== 1. SANTÉ & INFIRMERIE ====================

    @GetMapping("/medical/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('MEDICAL_VIEW')")
    public ResponseEntity<List<MedicalRecord>> getMedicalRecords(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(medicalRecordRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/medical")
    @PreAuthorize("hasAuthority('MEDICAL_EDIT')")
    public ResponseEntity<MedicalRecord> saveMedicalRecord(@RequestBody MedicalRecord record) {
        if (!SecurityUtils.isSuperAdmin()) {
            record.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(medicalRecordRepository.save(record));
    }

    @DeleteMapping("/medical/{id}")
    @PreAuthorize("hasAuthority('MEDICAL_EDIT')")
    public ResponseEntity<Void> deleteMedicalRecord(@PathVariable UUID id) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier médical introuvable"));
        SecurityUtils.assertOwnership(record.getTenantId());
        medicalRecordRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. VIE SCOLAIRE & DISCIPLINE ====================

    @GetMapping("/discipline/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('DISCIPLINE_VIEW')")
    public ResponseEntity<List<DisciplinaryIncident>> getDisciplinaryIncidents(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(disciplinaryIncidentRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/discipline")
    @PreAuthorize("hasAuthority('DISCIPLINE_EDIT')")
    public ResponseEntity<DisciplinaryIncident> saveDisciplinaryIncident(@RequestBody DisciplinaryIncident incident) {
        if (!SecurityUtils.isSuperAdmin()) {
            incident.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(disciplinaryIncidentRepository.save(incident));
    }

    @DeleteMapping("/discipline/{id}")
    @PreAuthorize("hasAuthority('DISCIPLINE_EDIT')")
    public ResponseEntity<Void> deleteDisciplinaryIncident(@PathVariable UUID id) {
        DisciplinaryIncident incident = disciplinaryIncidentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident disciplinaire introuvable"));
        SecurityUtils.assertOwnership(incident.getTenantId());
        disciplinaryIncidentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
