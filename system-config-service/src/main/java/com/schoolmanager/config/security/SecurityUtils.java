package com.schoolmanager.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Utilitaire centralisé pour extraire les informations du token JWT
 * depuis le SecurityContext Spring Security.
 *
 * <p>Enforce l'isolation multi-tenant pour les utilisateurs réguliers,
 * et permet le bypass d'administration pour les comptes Super Admin.</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Retourne le {@link UserPrincipal} de l'utilisateur actuellement authentifié.
     *
     * @throws ResponseStatusException 401 si aucun utilisateur authentifié
     */
    public static UserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        }
        return (UserPrincipal) auth.getPrincipal();
    }

    /**
     * Retourne l'UUID du tenant extrait du JWT de l'utilisateur connecté.
     *
     * @throws ResponseStatusException 401 si aucun utilisateur authentifié
     * @throws ResponseStatusException 400 si le tenantId dans le token est absent ou malformé
     */
    public static UUID getCurrentTenantId() {
        String raw = getCurrentPrincipal().getTenantId();
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId absent du token");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId invalide dans le token");
        }
    }

    /**
     * Résout le tenantId à utiliser pour une requête.
     * 
     * <p>Pour un utilisateur normal, valide que le tenantId de l'URL correspond à son token JWT (anti-IDOR).
     * Pour un Super Admin, permet de requêter n'importe quel tenant passé dans le chemin.</p>
     *
     * @param pathTenantId Le tenantId extrait du chemin d'URL
     * @return Le tenantId validé et autorisé
     */
    public static UUID getTenantIdToUse(UUID pathTenantId) {
        UserPrincipal principal = getCurrentPrincipal();
        if (Boolean.TRUE.equals(principal.getIsSuperAdmin())) {
            if (pathTenantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le paramètre tenantId est requis pour le Super Admin");
            }
            return pathTenantId;
        }
        
        UUID jwtTenantId = getCurrentTenantId();
        if (pathTenantId != null && !jwtTenantId.equals(pathTenantId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Accès refusé : vous n'avez pas accès aux données de cet établissement"
            );
        }
        return jwtTenantId;
    }

    /**
     * Vérifie que l'entité demandée appartient bien au tenant connecté.
     * Lève une 403 Forbidden si l'appartenance ne correspond pas.
     * Les Super Admins sont exemptés.
     *
     * @param entityTenantId tenantId de l'entité récupérée en base
     */
    public static void assertOwnership(UUID entityTenantId) {
        if (isSuperAdmin()) {
            return;
        }
        UUID currentTenantId = getCurrentTenantId();
        if (!currentTenantId.equals(entityTenantId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Accès refusé : cette ressource n'appartient pas à votre établissement"
            );
        }
    }

    /**
     * Indique si l'utilisateur connecté est Super Admin.
     */
    public static boolean isSuperAdmin() {
        return Boolean.TRUE.equals(getCurrentPrincipal().getIsSuperAdmin());
    }
}
