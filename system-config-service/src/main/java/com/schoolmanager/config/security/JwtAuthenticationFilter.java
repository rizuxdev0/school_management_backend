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
                }
                if (roles != null) {
                    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
                if (permissions != null) {
                    permissions.forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
                }

                UserPrincipal principal = new UserPrincipal(username, claims.get("tenantId", String.class), isSuperAdmin);

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

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
