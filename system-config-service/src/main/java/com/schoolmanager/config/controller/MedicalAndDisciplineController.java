package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.DisciplinaryIncident;
import com.schoolmanager.config.entity.MedicalRecord;
import com.schoolmanager.config.repository.DisciplinaryIncidentRepository;
import com.schoolmanager.config.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/medical-discipline")
@RequiredArgsConstructor
public class MedicalAndDisciplineController {

    private final MedicalRecordRepository medicalRecordRepository;
    private final DisciplinaryIncidentRepository disciplinaryIncidentRepository;

    // ==================== 1. SANTÉ & INFIRMERIE ====================

    @GetMapping("/medical/tenant/{tenantId}")
    public ResponseEntity<List<MedicalRecord>> getMedicalRecords(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(medicalRecordRepository.findByTenantId(tenantId));
    }

    @PostMapping("/medical")
    public ResponseEntity<MedicalRecord> saveMedicalRecord(@RequestBody MedicalRecord record) {
        return ResponseEntity.ok(medicalRecordRepository.save(record));
    }

    @DeleteMapping("/medical/{id}")
    public ResponseEntity<Void> deleteMedicalRecord(@PathVariable UUID id) {
        medicalRecordRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. VIE SCOLAIRE & DISCIPLINE ====================

    @GetMapping("/discipline/tenant/{tenantId}")
    public ResponseEntity<List<DisciplinaryIncident>> getDisciplinaryIncidents(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(disciplinaryIncidentRepository.findByTenantId(tenantId));
    }

    @PostMapping("/discipline")
    public ResponseEntity<DisciplinaryIncident> saveDisciplinaryIncident(@RequestBody DisciplinaryIncident incident) {
        return ResponseEntity.ok(disciplinaryIncidentRepository.save(incident));
    }

    @DeleteMapping("/discipline/{id}")
    public ResponseEntity<Void> deleteDisciplinaryIncident(@PathVariable UUID id) {
        disciplinaryIncidentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
