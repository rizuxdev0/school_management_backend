package com.schoolmanager.config.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    // Cache thread-safe pour le statut des tenants (limite l'accès réseau à 1 requête / minute par tenant)
    private static final java.util.Map<java.util.UUID, TenantCacheEntry> tenantCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 60000; // 60 secondes

    @lombok.Getter
    @RequiredArgsConstructor
    private static class TenantCacheEntry {
        private final boolean active;
        private final String planCode;
        private final long cachedAt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                Claims claims = jwtUtils.getClaimsFromJwtToken(jwt);
                String username = claims.getSubject();
                
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.get("roles");
                
                @SuppressWarnings("unchecked")
                List<String> permissions = (List<String>) claims.get("permissions");
                
                Boolean isSuperAdmin = claims.get("isSuperAdmin", Boolean.class);
                String planCode = claims.get("planCode", String.class);
                String rawTenantId = claims.get("tenantId", String.class);
                String email = claims.get("email", String.class);
                String phoneNumber = claims.get("phoneNumber", String.class);
                String rawUserId = claims.get("userId", String.class);
                java.util.UUID userId = (rawUserId != null && !rawUserId.isBlank()) ? java.util.UUID.fromString(rawUserId) : null;

                // --- VÉRIFICATION DYNAMIQUE DU STATUT DU TENANT (SAAS SECURITY CONTROL) ---
                if (!Boolean.TRUE.equals(isSuperAdmin) && rawTenantId != null && !rawTenantId.isBlank()) {
                    java.util.UUID tenantId = java.util.UUID.fromString(rawTenantId);
                    
                    // Lecture / Mise à jour du cache
                    long now = System.currentTimeMillis();
                    TenantCacheEntry cached = tenantCache.get(tenantId);
                    
                    if (cached == null || (now - cached.getCachedAt() > CACHE_DURATION_MS)) {
                        try {
                            String url = "http://localhost:8081/api/v1/auth/tenants/" + tenantId + "/status";
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> status = restTemplate.getForObject(url, java.util.Map.class);
                            if (status == null) {
                                rejectRequest(response, "TENANT_NOT_FOUND");
                                return;
                            }
                            boolean active = Boolean.TRUE.equals(status.get("isActive"));
                            String plan = (String) status.get("planCode");
                            cached = new TenantCacheEntry(active, plan, now);
                            tenantCache.put(tenantId, cached);
                        } catch (Exception e) {
                            log.error("Erreur de communication inter-services vers auth-service pour le statut du tenant: {}", e.getMessage());
                            if (cached == null) {
                                rejectRequest(response, "SECURITY_SERVICE_UNAVAILABLE");
                                return;
                            }
                        }
                    }

                    // 1. Validation de l'activation du compte
                    if (!cached.isActive()) {
                        rejectRequest(response, "TENANT_SUSPENDED");
                        return;
                    }

                    // 2. Validation de l'intégrité du plan d'abonnement
                    if (planCode != null && !planCode.equalsIgnoreCase(cached.getPlanCode())) {
                        rejectRequest(response, "PLAN_CHANGED");
                        return;
                    }
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (Boolean.TRUE.equals(isSuperAdmin)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                    authorities.add(new SimpleGrantedAuthority("ACADEMIC_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("ACADEMIC_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("EVALUATION_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("EVALUATION_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("ATTENDANCE_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("ATTENDANCE_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("FINANCE_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("FINANCE_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("HR_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("HR_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("MEDICAL_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("MEDICAL_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("DISCIPLINE_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("DISCIPLINE_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("EXAMS_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("EXAMS_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("LIBRARY_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("LIBRARY_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("TRANSPORT_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("TRANSPORT_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("CATERING_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("CATERING_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("MESSAGING_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("MESSAGING_EDIT"));
                    authorities.add(new SimpleGrantedAuthority("EXTRACURRICULAR_VIEW"));
                    authorities.add(new SimpleGrantedAuthority("EXTRACURRICULAR_EDIT"));
                }
                if (roles != null) {
                    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
                if (permissions != null) {
                    permissions.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
                }

                UserPrincipal principal = new UserPrincipal(username, rawTenantId, isSuperAdmin, planCode, email, phoneNumber, userId);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication in system-config: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void rejectRequest(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + reason + "\"}");
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
