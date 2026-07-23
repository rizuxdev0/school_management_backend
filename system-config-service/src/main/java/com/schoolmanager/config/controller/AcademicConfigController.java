package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.AcademicCycle;
import com.schoolmanager.config.entity.AcademicLevel;
import com.schoolmanager.config.entity.AcademicPeriod;
import com.schoolmanager.config.entity.AcademicYear;
import com.schoolmanager.config.repository.AcademicCycleRepository;
import com.schoolmanager.config.repository.AcademicLevelRepository;
import com.schoolmanager.config.repository.AcademicPeriodRepository;
import com.schoolmanager.config.repository.AcademicYearRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la configuration académique (cycles, niveaux, années, périodes).
 * Sécurisé contre les attaques IDOR via SecurityUtils.getTenantIdToUse.
 */
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
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<AcademicCycle>> getCyclesByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(academicCycleRepository.findByTenantIdOrderBySequenceOrderAsc(jwtTenantId));
    }

    @PostMapping("/cycles")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<AcademicCycle> createCycle(@RequestBody AcademicCycle cycle) {
        if (!SecurityUtils.isSuperAdmin()) {
            cycle.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(academicCycleRepository.save(cycle));
    }

    @DeleteMapping("/cycles/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteCycle(@PathVariable UUID id) {
        AcademicCycle cycle = academicCycleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cycle introuvable"));
        SecurityUtils.assertOwnership(cycle.getTenantId());
        academicCycleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- LEVELS ---

    @GetMapping("/levels/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<AcademicLevel>> getLevelsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(academicLevelRepository.findByTenantIdOrderBySequenceOrderAsc(jwtTenantId));
    }

    @PostMapping("/levels")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<AcademicLevel> createLevel(@RequestBody AcademicLevel level) {
        if (!SecurityUtils.isSuperAdmin()) {
            level.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(academicLevelRepository.save(level));
    }

    @DeleteMapping("/levels/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteLevel(@PathVariable UUID id) {
        AcademicLevel level = academicLevelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Niveau introuvable"));
        SecurityUtils.assertOwnership(level.getTenantId());
        academicLevelRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- YEARS ---

    @GetMapping("/years/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<AcademicYear>> getYearsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(academicYearRepository.findByTenantIdOrderByStartDateDesc(jwtTenantId));
    }

    @PostMapping("/years")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<AcademicYear> saveYear(@RequestBody AcademicYear year) {
        if (!SecurityUtils.isSuperAdmin()) {
            year.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(academicYearRepository.save(year));
    }

    @DeleteMapping("/years/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteYear(@PathVariable UUID id) {
        AcademicYear year = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Année académique introuvable"));
        SecurityUtils.assertOwnership(year.getTenantId());
        academicYearRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/years/{id}/activate")
    @Transactional
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<AcademicYear> activateYear(@PathVariable UUID id) {
        AcademicYear targetYear = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Année académique non trouvée"));

        SecurityUtils.assertOwnership(targetYear.getTenantId());

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
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<AcademicPeriod>> getPeriodsByYear(@PathVariable UUID yearId) {
        return ResponseEntity.ok(academicPeriodRepository.findByAcademicYearId(yearId));
    }

    @PostMapping("/periods")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<AcademicPeriod> savePeriod(@RequestBody AcademicPeriod period) {
        if (!SecurityUtils.isSuperAdmin()) {
            period.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(academicPeriodRepository.save(period));
    }

    @DeleteMapping("/periods/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deletePeriod(@PathVariable UUID id) {
        AcademicPeriod period = academicPeriodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Période introuvable"));
        SecurityUtils.assertOwnership(period.getTenantId());
        academicPeriodRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
