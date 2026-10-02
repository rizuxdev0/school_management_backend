package com.schoolmanager.config.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Composant de bootstrap exécuté au démarrage de l'application.
 * S'assure que la base de données du souscripteur Démo (DEMO-SCHOOL) et les structures de base existent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDatabaseBootstrap {

    private final TenantDataSourceProvider dataSourceProvider;
    private final TenantDatabaseSchemaManager schemaManager;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Démarrage du bootstrap Multi-Tenant Database-per-Tenant...");

        try {
            // 1. Initialiser la base isolée du tenant Démo
            String demoDbName = "school_tenant_demo";
            log.info("Vérification / Initialisation de la base dédiée démo : '{}'", demoDbName);
            schemaManager.createDatabaseIfNotExists(demoDbName);
            DataSource demoDs = dataSourceProvider.getOrCreateTenantDataSource(demoDbName);
            if (demoDs != null) {
                log.info("Base de données dédiée Démo '{}' prête et opérationnelle !", demoDbName);
            }
        } catch (Exception e) {
            log.warn("Bootstrap du tenant Démo : {}", e.getMessage());
        }

        log.info("Bootstrap Multi-Tenant terminé avec succès.");
    }
}
