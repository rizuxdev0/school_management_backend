package com.schoolmanager.auth.service;

import com.schoolmanager.auth.entity.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsable de l'auto-provisionnement des bases de données dédiées par souscripteur (Database-per-Tenant).
 * Exécute l'ordre DDL PostgreSQL CREATE DATABASE et informe les microservices métiers pour l'initialisation du schéma.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDatabaseProvisioner {

    private final DataSource dataSource;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.services.system-config-url:http://localhost:8082}")
    private String systemConfigServiceUrl;

    /**
     * Crée la base de données PostgreSQL isolée pour le souscripteur si elle n'existe pas encore.
     *
     * @param databaseName Le nom normalisé de la base (ex: school_tenant_demo)
     */
    public boolean createDatabase(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            log.error("Nom de base de données vide ou nul fourni.");
            return false;
        }

        // Nettoyage de sécurité du nom de la base (anti SQL-injection pour les identifiants PostgreSQL)
        String sanitizedDbName = databaseName.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        try (Connection connection = dataSource.getConnection()) {
            // PostgreSQL interdit l'instruction CREATE DATABASE dans une transaction gérée
            connection.setAutoCommit(true);

            // 1. Vérifier si la base existe déjà
            String checkSql = "SELECT 1 FROM pg_database WHERE datname = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, sanitizedDbName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        log.info("La base de données dédiée '{}' existe déjà.", sanitizedDbName);
                        return true;
                    }
                }
            }

            // 2. Création de la base de données dédiée
            log.info("Création de la base de données dédiée PostgreSQL : '{}'...", sanitizedDbName);
            try (Statement createStmt = connection.createStatement()) {
                createStmt.execute("CREATE DATABASE \"" + sanitizedDbName + "\" ENCODING 'UTF8'");
                log.info("Base de données '{}' créée avec succès !", sanitizedDbName);
                return true;
            }

        } catch (Exception e) {
            log.error("Erreur lors de la création de la base de données PostgreSQL '{}' : {}", sanitizedDbName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Notifie system-config-service pour initialiser le schéma DDL et les données par défaut de l'établissement.
     *
     * @param tenant L'établissement créé
     */
    public void notifySystemConfigService(Tenant tenant) {
        try {
            String url = systemConfigServiceUrl + "/api/v1/system/maintenance/tenants/provision";
            Map<String, Object> payload = new HashMap<>();
            payload.put("tenantId", tenant.getId());
            payload.put("tenantCode", tenant.getCode());
            payload.put("tenantName", tenant.getName());
            payload.put("databaseName", tenant.getDatabaseName());
            payload.put("systemPreset", tenant.getSystemPreset());
            payload.put("currencyCode", tenant.getCurrencyCode());
            payload.put("currencySymbol", tenant.getCurrencySymbol());
            payload.put("currencyNameFr", tenant.getCurrencyNameFr());
            payload.put("defaultLanguage", tenant.getDefaultLanguage());

            restTemplate.postForLocation(url, payload);
            log.info("Notification d'initialisation de schéma transmise avec succès à system-config-service pour {}", tenant.getCode());
        } catch (Exception e) {
            log.warn("Impossible de contacter system-config-service pour le provisionnement immédiat (sera initialisé à la première requête) : {}", e.getMessage());
        }
    }
}
