package com.schoolmanager.config.controller;

import com.schoolmanager.config.dto.SetupWizardDto;
import com.schoolmanager.config.entity.SystemSetting;
import com.schoolmanager.config.repository.SystemSettingRepository;
import com.schoolmanager.config.service.SetupWizardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingRepository systemSettingRepository;
    private final SetupWizardService setupWizardService;

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<SystemSetting> getSettingByTenant(@PathVariable UUID tenantId) {
        return systemSettingRepository.findByTenantId(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tenant/{tenantId}/is-initialized")
    public ResponseEntity<Map<String, Boolean>> isTenantInitialized(@PathVariable UUID tenantId) {
        boolean isInitialized = systemSettingRepository.findByTenantId(tenantId).isPresent();
        return ResponseEntity.ok(Map.of("initialized", isInitialized));
    }

    @PostMapping("/setup/wizard")
    public ResponseEntity<Map<String, String>> setupInstitution(@RequestBody SetupWizardDto dto) {
        setupWizardService.setupInstitution(dto);
        return ResponseEntity.ok(Map.of("message", "Configuration initiale réussie"));
    }

    @PostMapping
    public ResponseEntity<SystemSetting> saveOrUpdateSetting(@RequestBody SystemSetting setting) {
        return ResponseEntity.ok(systemSettingRepository.save(setting));
    }
}
