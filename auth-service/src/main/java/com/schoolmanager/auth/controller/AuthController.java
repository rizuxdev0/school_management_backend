package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.dto.*;
import com.schoolmanager.auth.entity.Tenant;
import com.schoolmanager.auth.entity.User;
import com.schoolmanager.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * Rafraîchit le token JWT de l'utilisateur actuellement connecté (session active).
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<JwtResponse> refreshToken(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.refreshCurrentUser(currentUser.getUsername()));
    }

    /**
     * Renouvelle l'accès via le Refresh Token lorsque l'Access Token JWT a expiré.
     */
    @PostMapping("/session/refresh")
    public ResponseEntity<JwtResponse> refreshSession(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.processRefreshToken(request));
    }

    /**
     * Déconnexion sécurisée : révoque le Refresh Token associé.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.revokeRefreshToken(request.getRefreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }

    /**
     * Demande d'initialisation de réinitialisation de mot de passe (Mot de passe oublié).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.requestForgotPassword(request));
    }

    /**
     * Validation du changement de mot de passe avec le token de sécurité.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PutMapping("/tenants/{tenantId}/branding")
    public ResponseEntity<Tenant> updateBranding(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, String> branding,
            @AuthenticationPrincipal User currentUser) {
        
        if (!Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) && 
            (currentUser.getTenant() == null || !currentUser.getTenant().getId().equals(tenantId))) {
            throw new AccessDeniedException("Accès non autorisé à ce tenant");
        }

        String logoUrl = branding.get("logoUrl");
        String primaryColor = branding.get("primaryColor");
        return ResponseEntity.ok(authService.updateTenantBranding(tenantId, logoUrl, primaryColor));
    }

    @GetMapping("/tenants/{tenantId}/invoices")
    public ResponseEntity<List<com.schoolmanager.auth.entity.SubscriptionInvoice>> getTenantInvoices(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal User currentUser) {
        
        if (!Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) && 
            (currentUser.getTenant() == null || !currentUser.getTenant().getId().equals(tenantId))) {
            throw new AccessDeniedException("Accès non autorisé aux factures de ce tenant");
        }

        return ResponseEntity.ok(subscriptionInvoiceRepository.findByTenantIdOrderByInvoiceDateDesc(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/status")
    public ResponseEntity<Map<String, Object>> getTenantStatus(@PathVariable UUID tenantId) {
        return authService.getTenantStatus(tenantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
