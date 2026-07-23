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
    private final com.schoolmanager.auth.repository.UserRepository userRepository;
    private final com.schoolmanager.auth.repository.SubscriptionInvoiceRepository subscriptionInvoiceRepository;

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

    @PutMapping("/plans/{id}")
    public ResponseEntity<SubscriptionPlan> updatePlan(@PathVariable UUID id, @RequestBody SubscriptionPlan planDetails) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan d'abonnement introuvable"));
        plan.setNameFr(planDetails.getNameFr());
        plan.setNameEn(planDetails.getNameEn());
        plan.setPriceMonthly(planDetails.getPriceMonthly());
        plan.setPriceYearly(planDetails.getPriceYearly());
        plan.setMaxStudents(planDetails.getMaxStudents());
        plan.setMaxStaff(planDetails.getMaxStaff());
        plan.setMaxClassrooms(planDetails.getMaxClassrooms());
        plan.setMaxBooks(planDetails.getMaxBooks());
        plan.setIsActive(planDetails.getIsActive());
        if (planDetails.getIncludedModules() != null) {
            plan.setIncludedModules(planDetails.getIncludedModules());
        }
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
        if (limitsDto.getPlanCode() != null) {
            tenant.setPlanCode(limitsDto.getPlanCode());
            subscriptionPlanRepository.findByCode(limitsDto.getPlanCode()).ifPresent(plan -> {
                tenant.setEnabledModules(new java.util.HashSet<>(plan.getIncludedModules()));
                // Align limits with plan defaults
                if (limitsDto.getMaxStudents() == null) {
                    tenant.setMaxStudents(plan.getMaxStudents());
                } else {
                    tenant.setMaxStudents(limitsDto.getMaxStudents());
                }
                if (limitsDto.getMaxStaff() == null) {
                    tenant.setMaxStaff(plan.getMaxStaff());
                } else {
                    tenant.setMaxStaff(limitsDto.getMaxStaff());
                }
                if (limitsDto.getMaxClassrooms() == null) {
                    tenant.setMaxClassrooms(plan.getMaxClassrooms());
                } else {
                    tenant.setMaxClassrooms(limitsDto.getMaxClassrooms());
                }
                if (limitsDto.getMaxBooks() == null) {
                    tenant.setMaxBooks(plan.getMaxBooks());
                } else {
                    tenant.setMaxBooks(limitsDto.getMaxBooks());
                }
            });
        } else {
            // No plan change, just apply limits if they are provided
            if (limitsDto.getMaxStudents() != null) tenant.setMaxStudents(limitsDto.getMaxStudents());
            if (limitsDto.getMaxStaff() != null) tenant.setMaxStaff(limitsDto.getMaxStaff());
            if (limitsDto.getMaxClassrooms() != null) tenant.setMaxClassrooms(limitsDto.getMaxClassrooms());
            if (limitsDto.getMaxBooks() != null) tenant.setMaxBooks(limitsDto.getMaxBooks());
        }
        if (limitsDto.getSubscriptionExpiresAt() != null) {
            tenant.setSubscriptionExpiresAt(limitsDto.getSubscriptionExpiresAt());
        }
        return ResponseEntity.ok(tenantRepository.save(tenant));
    }

    @PostMapping("/tenants/{tenantId}/renew")
    public ResponseEntity<Tenant> renewTenantSubscription(@PathVariable java.util.UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant introuvable"));
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime currentExpiry = tenant.getSubscriptionExpiresAt();
        if (currentExpiry == null || currentExpiry.isBefore(now)) {
            tenant.setSubscriptionExpiresAt(now.plusYears(1));
        } else {
            tenant.setSubscriptionExpiresAt(currentExpiry.plusYears(1));
        }
        return ResponseEntity.ok(tenantRepository.save(tenant));
    }

    @GetMapping("/database/export/global")
    public ResponseEntity<java.util.Map<String, Object>> exportGlobalData() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("tenants", tenantRepository.findAll());
        data.put("users", userRepository.findAll());
        data.put("plans", subscriptionPlanRepository.findAll());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/database/export/tenant/{tenantId}")
    public ResponseEntity<java.util.Map<String, Object>> exportTenantData(@PathVariable java.util.UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant introuvable"));
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("tenant", tenant);
        data.put("users", userRepository.findAll().stream()
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(tenantId))
                .toList());
        return ResponseEntity.ok(data);
    }

    @PostMapping("/database/reset")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> resetDatabase() {
        // 1. Supprimer toutes les factures
        subscriptionInvoiceRepository.deleteAll();

        // 2. Supprimer tous les utilisateurs sauf 'superadmin'
        List<com.schoolmanager.auth.entity.User> allUsers = userRepository.findAll();
        for (com.schoolmanager.auth.entity.User u : allUsers) {
            if (!"superadmin".equalsIgnoreCase(u.getUsername())) {
                userRepository.delete(u);
            }
        }

        // 3. Supprimer tous les établissements (tenants)
        tenantRepository.deleteAll();

        return ResponseEntity.ok().build();
    }
}
