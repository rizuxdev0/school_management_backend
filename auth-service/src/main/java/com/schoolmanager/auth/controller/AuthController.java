package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.dto.AuthRequest;
import com.schoolmanager.auth.dto.JwtResponse;
import com.schoolmanager.auth.dto.TenantRegistrationDto;
import com.schoolmanager.auth.entity.Tenant;
import com.schoolmanager.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PutMapping("/tenants/{tenantId}/branding")
    public ResponseEntity<Tenant> updateBranding(
            @PathVariable java.util.UUID tenantId,
            @RequestBody java.util.Map<String, String> branding) {
        String logoUrl = branding.get("logoUrl");
        String primaryColor = branding.get("primaryColor");
        return ResponseEntity.ok(authService.updateTenantBranding(tenantId, logoUrl, primaryColor));
    }

    @GetMapping("/tenants/{tenantId}/invoices")
    public ResponseEntity<java.util.List<com.schoolmanager.auth.entity.SubscriptionInvoice>> getTenantInvoices(
            @PathVariable java.util.UUID tenantId) {
        return ResponseEntity.ok(subscriptionInvoiceRepository.findByTenantIdOrderByInvoiceDateDesc(tenantId));
    }
}
