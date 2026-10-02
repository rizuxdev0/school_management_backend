package com.schoolmanager.config.multitenancy;

import lombok.extern.slf4j.Slf4j;

/**
 * Gestionnaire de contexte thread-local pour l'isolation multi-tenant (Database-per-Tenant).
 * Stocke l'identifiant / nom de base de données du souscripteur pour le thread courant.
 */
@Slf4j
public final class TenantContext {

    public static final String DEFAULT_TENANT = "DEFAULT";
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * Définit le nom ou identifiant de la base de données du tenant pour le thread courant.
     *
     * @param tenantId Nom de la base ou code du souscripteur (ex: school_tenant_demo)
     */
    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT_TENANT.set(DEFAULT_TENANT);
        } else {
            String sanitized = sanitizeTenantKey(tenantId);
            CURRENT_TENANT.set(sanitized);
            log.trace("TenantContext positionné sur : {}", sanitized);
        }
    }

    /**
     * Retourne la clé du tenant courant ou DEFAULT si aucun n'est spécifié.
     */
    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return (tenant != null && !tenant.isBlank()) ? tenant : DEFAULT_TENANT;
    }

    /**
     * Nettoie le ThreadLocal pour éviter toute fuite de mémoire ou interférence entre requêtes.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
        log.trace("TenantContext nettoyé.");
    }

    /**
     * Normalise le nom de la base de données PostgreSQL pour le tenant.
     */
    public static String sanitizeTenantKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || DEFAULT_TENANT.equalsIgnoreCase(rawKey)) {
            return DEFAULT_TENANT;
        }
        String clean = rawKey.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (!clean.startsWith("school_tenant_") && !clean.equals("school_system_db")) {
            return "school_tenant_" + clean;
        }
        return clean;
    }
}
