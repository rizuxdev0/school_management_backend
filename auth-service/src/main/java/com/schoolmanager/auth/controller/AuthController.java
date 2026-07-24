package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.dto.AuthRequest;
import com.schoolmanager.auth.dto.JwtResponse;
import com.schoolmanager.auth.dto.TenantRegistrationDto;
import com.schoolmanager.auth.entity.Tenant;
import com.schoolmanager.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.schoolmanager.auth.repository.SubscriptionInvoiceRepository subscriptionInvoiceRepository;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register-tenant")
    public ResponseEntity<Tenant> registerTenant(@Valid @RequestBody TenantRegistrationDto dto) {
        return ResponseEntity.ok(authService.registerTenant(dto));
    }

    /**
     * Rafraîchit le token JWT de l'utilisateur actuellement connecté.
     * Recharge depuis la DB : modules activés, plan, quotas, rôles et permissions.
     * Aucune déconnexion requise — le frontend stocke le nouveau token et met à jour
     * le signal Angular immédiatement.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<JwtResponse> refreshToken(@org.springframework.security.core.annotation.AuthenticationPrincipal com.schoolmanager.auth.entity.User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.refreshCurrentUser(currentUser.getUsername()));
    }

    @PutMapping("/tenants/{tenantId}/branding")
    public ResponseEntity<Tenant> updateBranding(
            @PathVariable java.util.UUID tenantId,
            @RequestBody java.util.Map<String, String> branding,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.schoolmanager.auth.entity.User currentUser) {
        
        // Bloquer l'accès si l'utilisateur n'est ni Super Admin ni membre du tenant ciblé
        if (!Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) && 
            (currentUser.getTenant() == null || !currentUser.getTenant().getId().equals(tenantId))) {
            throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé à ce tenant");
        }

        String logoUrl = branding.get("logoUrl");
        String primaryColor = branding.get("primaryColor");
        return ResponseEntity.ok(authService.updateTenantBranding(tenantId, logoUrl, primaryColor));
    }

    @GetMapping("/tenants/{tenantId}/invoices")
    public ResponseEntity<java.util.List<com.schoolmanager.auth.entity.SubscriptionInvoice>> getTenantInvoices(
            @PathVariable java.util.UUID tenantId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.schoolmanager.auth.entity.User currentUser) {
        
        // Bloquer l'accès si l'utilisateur n'est ni Super Admin ni membre du tenant ciblé
        if (!Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) && 
            (currentUser.getTenant() == null || !currentUser.getTenant().getId().equals(tenantId))) {
            throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé aux factures de ce tenant");
        }

        return ResponseEntity.ok(subscriptionInvoiceRepository.findByTenantIdOrderByInvoiceDateDesc(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/status")
    public ResponseEntity<java.util.Map<String, Object>> getTenantStatus(@PathVariable java.util.UUID tenantId) {
        return authService.getTenantStatus(tenantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
