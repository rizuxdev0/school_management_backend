package com.schoolmanager.auth.security;

import com.schoolmanager.auth.entity.User;
import com.schoolmanager.auth.repository.UserRepository;
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

/**
 * Filtre d'interception et de validation des tokens JWT pour authentifier les requêtes Spring Security.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final com.schoolmanager.auth.repository.TenantRepository tenantRepository;

    // Cache thread-safe pour le statut des tenants (limite l'accès base à 1 requête / minute par tenant)
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
                io.jsonwebtoken.Claims claims = jwtUtils.getClaimsFromJwtToken(jwt);
                String username = claims.getSubject();

                Boolean isSuperAdmin = claims.get("isSuperAdmin", Boolean.class);
                String planCode = claims.get("planCode", String.class);
                String rawTenantId = claims.get("tenantId", String.class);

                // --- VÉRIFICATION DYNAMIQUE DU STATUT DU TENANT (SAAS SECURITY CONTROL) ---
                if (!Boolean.TRUE.equals(isSuperAdmin) && rawTenantId != null && !rawTenantId.isBlank()) {
                    java.util.UUID tenantId = java.util.UUID.fromString(rawTenantId);
                    
                    // Lecture / Mise à jour du cache
                    long now = System.currentTimeMillis();
                    TenantCacheEntry cached = tenantCache.get(tenantId);
                    
                    if (cached == null || (now - cached.getCachedAt() > CACHE_DURATION_MS)) {
                        com.schoolmanager.auth.entity.Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                        if (tenant == null) {
                            rejectRequest(response, "TENANT_NOT_FOUND");
                            return;
                        }
                        cached = new TenantCacheEntry(tenant.getIsActive(), tenant.getPlanCode(), now);
                        tenantCache.put(tenantId, cached);
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

                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null && Boolean.TRUE.equals(user.getIsActive()) && Boolean.TRUE.equals(user.getIsAccountNonLocked())) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
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
                    }
                    if (user.getRoles() != null) {
                        user.getRoles().forEach(role -> {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
                            if (role.getPermissions() != null) {
                                role.getPermissions().forEach(permission -> {
                                    authorities.add(new SimpleGrantedAuthority(permission.getCode()));
                                });
                            }
                        });
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Impossible de définir l'authentification de l'utilisateur : {}", e.getMessage());
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
