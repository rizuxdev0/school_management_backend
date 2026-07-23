package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.entity.FeatureModule;
import com.schoolmanager.auth.entity.SubscriptionPlan;
import com.schoolmanager.auth.entity.Tenant;
import com.schoolmanager.auth.repository.FeatureModuleRepository;
import com.schoolmanager.auth.repository.SubscriptionPlanRepository;
import com.schoolmanager.auth.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or #root.principal.isSuperAdmin == true")
public class SuperAdminController {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final FeatureModuleRepository featureModuleRepository;

    @GetMapping("/tenants")
    public ResponseEntity<List<Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantRepository.findAll());
    }

    @PostMapping("/tenants/{tenantId}/toggle-status")
    public ResponseEntity<Tenant> toggleTenantStatus(@PathVariable UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant introuvable"));
        tenant.setIsActive(!tenant.getIsActive());
        return ResponseEntity.ok(tenantRepository.save(tenant));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        return ResponseEntity.ok(subscriptionPlanRepository.findAll());
    }

    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlan> createPlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok(subscriptionPlanRepository.save(plan));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<FeatureModule>> getAllFeatureModules() {
        return ResponseEntity.ok(featureModuleRepository.findAll());
    }

    @PostMapping("/modules")
    public ResponseEntity<FeatureModule> createFeatureModule(@RequestBody FeatureModule module) {
        return ResponseEntity.ok(featureModuleRepository.save(module));
    }
    @PutMapping("/tenants/{tenantId}/limits")
    public ResponseEntity<Tenant> updateTenantLimits(
            @PathVariable UUID tenantId,
            @RequestBody Tenant limitsDto) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant introuvable"));
        tenant.setMaxStudents(limitsDto.getMaxStudents());
        tenant.setMaxStaff(limitsDto.getMaxStaff());
        tenant.setMaxClassrooms(limitsDto.getMaxClassrooms());
        tenant.setMaxBooks(limitsDto.getMaxBooks());
        if (limitsDto.getPlanCode() != null) {
            if (!limitsDto.getPlanCode().equals(tenant.getPlanCode())) {
                tenant.setPlanCode(limitsDto.getPlanCode());
                subscriptionPlanRepository.findByCode(limitsDto.getPlanCode()).ifPresent(plan -> {
                    tenant.setEnabledModules(new java.util.HashSet<>(plan.getIncludedModules()));
                });
            }
        }
        if (limitsDto.getSubscriptionExpiresAt() != null) {
            tenant.setSubscriptionExpiresAt(limitsDto.getSubscriptionExpiresAt());
        }
        return ResponseEntity.ok(tenantRepository.save(tenant));
    }
}
