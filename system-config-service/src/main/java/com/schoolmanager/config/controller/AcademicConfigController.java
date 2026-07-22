package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.AcademicCycle;
import com.schoolmanager.config.entity.AcademicLevel;
import com.schoolmanager.config.entity.AcademicPeriod;
import com.schoolmanager.config.entity.AcademicYear;
import com.schoolmanager.config.repository.AcademicCycleRepository;
import com.schoolmanager.config.repository.AcademicLevelRepository;
import com.schoolmanager.config.repository.AcademicPeriodRepository;
import com.schoolmanager.config.repository.AcademicYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/academic-config")
@RequiredArgsConstructor
public class AcademicConfigController {

    private final AcademicCycleRepository academicCycleRepository;
    private final AcademicLevelRepository academicLevelRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicPeriodRepository academicPeriodRepository;

    // --- CYCLES ---
    
    @GetMapping("/cycles/tenant/{tenantId}")
    public ResponseEntity<List<AcademicCycle>> getCyclesByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(academicCycleRepository.findByTenantIdOrderBySequenceOrderAsc(tenantId));
    }

    @PostMapping("/cycles")
    public ResponseEntity<AcademicCycle> createCycle(@RequestBody AcademicCycle cycle) {
        return ResponseEntity.ok(academicCycleRepository.save(cycle));
    }

    @DeleteMapping("/cycles/{id}")
    public ResponseEntity<Void> deleteCycle(@PathVariable UUID id) {
        academicCycleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- LEVELS ---

    @GetMapping("/levels/tenant/{tenantId}")
    public ResponseEntity<List<AcademicLevel>> getLevelsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(academicLevelRepository.findByTenantIdOrderBySequenceOrderAsc(tenantId));
    }

    @PostMapping("/levels")
    public ResponseEntity<AcademicLevel> createLevel(@RequestBody AcademicLevel level) {
        return ResponseEntity.ok(academicLevelRepository.save(level));
    }

    @DeleteMapping("/levels/{id}")
    public ResponseEntity<Void> deleteLevel(@PathVariable UUID id) {
        academicLevelRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- YEARS ---

    @GetMapping("/years/tenant/{tenantId}")
    public ResponseEntity<List<AcademicYear>> getYearsByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(academicYearRepository.findByTenantIdOrderByStartDateDesc(tenantId));
    }

    @PostMapping("/years")
    public ResponseEntity<AcademicYear> saveYear(@RequestBody AcademicYear year) {
        return ResponseEntity.ok(academicYearRepository.save(year));
    }

    @DeleteMapping("/years/{id}")
    public ResponseEntity<Void> deleteYear(@PathVariable UUID id) {
        academicYearRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/years/{id}/activate")
    @Transactional
    public ResponseEntity<AcademicYear> activateYear(@PathVariable UUID id) {
        AcademicYear targetYear = academicYearRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Année académique non trouvée"));
        
        List<AcademicYear> allYears = academicYearRepository.findByTenantId(targetYear.getTenantId());
        for (AcademicYear y : allYears) {
            boolean isCurrent = y.getId().equals(id);
            y.setIsCurrent(isCurrent);
            y.setStatus(isCurrent ? "ACTIVE" : "CLOSED");
        }
        academicYearRepository.saveAll(allYears);
        return ResponseEntity.ok(targetYear);
    }

    // --- PERIODS ---

    @GetMapping("/periods/year/{yearId}")
    public ResponseEntity<List<AcademicPeriod>> getPeriodsByYear(@PathVariable UUID yearId) {
        return ResponseEntity.ok(academicPeriodRepository.findByAcademicYearId(yearId));
    }

    @PostMapping("/periods")
    public ResponseEntity<AcademicPeriod> savePeriod(@RequestBody AcademicPeriod period) {
        return ResponseEntity.ok(academicPeriodRepository.save(period));
    }

    @DeleteMapping("/periods/{id}")
    public ResponseEntity<Void> deletePeriod(@PathVariable UUID id) {
        academicPeriodRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
