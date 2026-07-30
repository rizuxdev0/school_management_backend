package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.entity.GlobalSetting;
import com.schoolmanager.auth.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/settings")
@RequiredArgsConstructor
public class GlobalSettingController {

    private final GlobalSettingRepository globalSettingRepository;
    
    private static final UUID GLOBAL_SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @GetMapping("/global")
    public ResponseEntity<GlobalSetting> getGlobalSettings() {
        GlobalSetting setting = globalSettingRepository.findById(GLOBAL_SETTINGS_ID)
                .orElseGet(() -> {
                    GlobalSetting newSetting = GlobalSetting.builder()
                            .id(GLOBAL_SETTINGS_ID)
                            .maintenanceMode(false)
                            .announcementText(null)
                            .announcementEnd(null)
                            .build();
                    return globalSettingRepository.save(newSetting);
                });
        return ResponseEntity.ok(setting);
    }

    @PostMapping("/global")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<GlobalSetting> updateGlobalSettings(@RequestBody GlobalSetting updated) {
        GlobalSetting setting = globalSettingRepository.findById(GLOBAL_SETTINGS_ID)
                .orElse(GlobalSetting.builder().id(GLOBAL_SETTINGS_ID).build());
        
        setting.setMaintenanceMode(updated.isMaintenanceMode());
        setting.setAnnouncementText(updated.getAnnouncementText());
        setting.setAnnouncementEnd(updated.getAnnouncementEnd());
        
        return ResponseEntity.ok(globalSettingRepository.save(setting));
    }
}
