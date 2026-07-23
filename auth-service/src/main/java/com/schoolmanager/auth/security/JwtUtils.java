package com.schoolmanager.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication, UUID tenantId, String tenantCode, List<String> enabledModules, List<String> roles, List<String> permissions, boolean isSuperAdmin) {
        String username = authentication.getName();

        return Jwts.builder()
                .subject(username)
                .claims(Map.of(
                        "tenantId", tenantId != null ? tenantId.toString() : "",
                        "tenantCode", tenantCode != null ? tenantCode : "",
                        "enabledModules", enabledModules,
                        "roles", roles,
                        "permissions", permissions,
                        "isSuperAdmin", isSuperAdmin
                ))
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JTW Token validation error: {}", e.getMessage());
        }
        return false;
    }
}
