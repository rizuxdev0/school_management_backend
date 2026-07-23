package com.schoolmanager.config.controller;

import com.schoolmanager.config.dto.SetupWizardDto;
import com.schoolmanager.config.entity.SystemSetting;
import com.schoolmanager.config.repository.SystemSettingRepository;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.service.SetupWizardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur REST pour les paramètres système et le wizard de configuration initiale.
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingRepository systemSettingRepository;
    private final SetupWizardService setupWizardService;

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW') or hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<SystemSetting> getSettingByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return systemSettingRepository.findByTenantId(jwtTenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tenant/{tenantId}/is-initialized")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW') or hasAuthority('FINANCE_VIEW')")
    public ResponseEntity<Map<String, Boolean>> isTenantInitialized(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        boolean isInitialized = systemSettingRepository.findByTenantId(jwtTenantId).isPresent();
        return ResponseEntity.ok(Map.of("initialized", isInitialized));
    }

    @PostMapping("/setup/wizard")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT') or hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Map<String, String>> setupInstitution(@RequestBody SetupWizardDto dto) {
        if (!SecurityUtils.isSuperAdmin()) {
            dto.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        setupWizardService.setupInstitution(dto);
        return ResponseEntity.ok(Map.of("message", "Configuration initiale réussie"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT') or hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<SystemSetting> saveOrUpdateSetting(@RequestBody SystemSetting setting) {
        if (!SecurityUtils.isSuperAdmin()) {
            setting.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(systemSettingRepository.save(setting));
    }
}
