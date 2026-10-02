package com.schoolmanager.config.multitenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fournisseur et gestionnaire de cycles de vie des pools de connexions HikariCP
 * pour chaque base de données PostgreSQL isolée de souscripteur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDataSourceProvider {

    private final TenantDatabaseSchemaManager schemaManager;

    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:5432}")
    private String dbPort;

    @Value("${spring.datasource.username:${DB_USER:postgres}}")
    private String dbUser;

    @Value("${spring.datasource.password:${DB_PASSWORD:admin}}")
    private String dbPassword;

    private final Map<String, DataSource> cachedDataSources = new ConcurrentHashMap<>();

    /**
     * Récupère ou instancie dynamiquement un pool de connexions HikariCP pour un tenant donné.
     *
     * @param tenantKey Nom normalisé de la base (ex: school_tenant_demo)
     * @return Le DataSource HikariCP configuré et prêt
     */
    public DataSource getOrCreateTenantDataSource(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank() || TenantContext.DEFAULT_TENANT.equals(tenantKey)) {
            return null;
        }

        String cleanDbName = TenantContext.sanitizeTenantKey(tenantKey);

        return cachedDataSources.computeIfAbsent(cleanDbName, dbName -> {
            log.info("Création d'un nouveau pool de connexions pour la base dédiée : '{}'", dbName);

            // 1. S'assurer que la base PostgreSQL existe
            schemaManager.createDatabaseIfNotExists(dbName);

            // 2. Configuration HikariCP optimisée pour multi-tenant
            String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPassword);
            config.setDriverClassName("org.postgresql.Driver");
            config.setPoolName("HikariPool-" + dbName);

            // Limites raisonnables pour supporter des dizaines de bases sans épuiser PostgreSQL
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(60000); // 1 minute
            config.setMaxLifetime(1800000); // 30 minutes
            config.setConnectionTimeout(20000); // 20 secondes
            config.setValidationTimeout(5000);

            HikariDataSource ds = new HikariDataSource(config);

            // 3. Initialisation du schéma DDL et des seeders
            try {
                schemaManager.initializeTenantSchema(ds, dbName, null);
            } catch (Exception e) {
                log.error("Erreur d'initialisation du schéma pour la base tenant '{}' : {}", dbName, e.getMessage(), e);
            }

            return ds;
        });
    }

    /**
     * Provisionne explicitement une nouvelle base pour un souscripteur avec ses configurations par défaut.
     */
    public DataSource provisionNewTenantDatabase(TenantProvisioningRequest request) {
        String dbName = request.getDatabaseName();
        if (dbName == null || dbName.isBlank()) {
            dbName = "school_tenant_" + request.getTenantCode().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }
        dbName = TenantContext.sanitizeTenantKey(dbName);

        log.info("Provisionnement d'une nouvelle base de données dédiée pour le souscripteur '{}' -> '{}'", request.getTenantCode(), dbName);

        // 1. Créer la base
        schemaManager.createDatabaseIfNotExists(dbName);

        // 2. Créer le pool Hikari
        String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("HikariPool-" + dbName);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(20000);

        HikariDataSource ds = new HikariDataSource(config);

        // 3. Appliquer le schéma et les métadonnées spécifiques du souscripteur
        schemaManager.initializeTenantSchema(ds, dbName, request);

        cachedDataSources.put(dbName, ds);
        return ds;
    }
}
