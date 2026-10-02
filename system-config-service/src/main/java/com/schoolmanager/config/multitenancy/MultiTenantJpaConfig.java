package com.schoolmanager.config.multitenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Spring Data JPA et DataSource multi-tenant (Database-per-Tenant).
 */
@Slf4j
@Configuration
public class MultiTenantJpaConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/school_system_db}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username:${DB_USER:postgres}}")
    private String dbUser;

    @Value("${spring.datasource.password:${DB_PASSWORD:admin}}")
    private String dbPassword;

    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:5432}")
    private String dbPort;

    /**
     * DataSource par défaut (utilisé pour les opérations globales / hors-contexte tenant ou startup).
     */
    @Bean(name = "defaultDataSource")
    public DataSource defaultDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(defaultJdbcUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("HikariPool-DefaultSystem");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        return new HikariDataSource(config);
    }

    /**
     * DataSource primaire exposé à Spring Boot & Hibernate, avec routage dynamique par tenant.
     */
    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(DataSource defaultDataSource, TenantDataSourceProvider dataSourceProvider) {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setDataSourceProvider(dataSourceProvider);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(TenantContext.DEFAULT_TENANT, defaultDataSource);
        targetDataSources.put("school_system_db", defaultDataSource);

        // Pré-chargement de la base démo pour accessibilité instantanée
        try {
            DataSource demoDs = dataSourceProvider.getOrCreateTenantDataSource("school_tenant_demo");
            if (demoDs != null) {
                targetDataSources.put("school_tenant_demo", demoDs);
                targetDataSources.put("school_tenant_demo_school", demoDs);
            }
        } catch (Exception e) {
            log.warn("Pré-chargement du DataSource Démo différé : {}", e.getMessage());
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();

        log.info("Multi-Tenant Dynamic Routing DataSource initialisé avec succès !");
        return routingDataSource;
    }
}
