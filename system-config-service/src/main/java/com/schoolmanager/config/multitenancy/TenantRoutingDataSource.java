package com.schoolmanager.config.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DataSource dynamique basée sur AbstractRoutingDataSource pour le routage transparent
 * vers la base de données PostgreSQL dédiée de chaque souscripteur (Database-per-Tenant).
 */
@Slf4j
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> dynamicTargetDataSources = new ConcurrentHashMap<>();
    private TenantDataSourceProvider dataSourceProvider;

    public void setDataSourceProvider(TenantDataSourceProvider provider) {
        this.dataSourceProvider = provider;
    }

    public void registerTenantDataSource(String tenantKey, DataSource dataSource) {
        dynamicTargetDataSources.put(tenantKey, dataSource);
        setTargetDataSources(new ConcurrentHashMap<>(dynamicTargetDataSources));
        afterPropertiesSet();
        log.info("Pool de connexions enregistré pour la base tenant : '{}'", tenantKey);
    }

    public boolean hasTenantDataSource(String tenantKey) {
        return dynamicTargetDataSources.containsKey(tenantKey);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String currentTenant = TenantContext.getCurrentTenant();

        // Si le tenant n'est pas le DEFAULT et n'est pas encore dans le pool dynamique,
        // on tente de l'instancier dynamiquement à la volée via le provider
        if (dataSourceProvider != null && !TenantContext.DEFAULT_TENANT.equals(currentTenant)
                && !dynamicTargetDataSources.containsKey(currentTenant)) {
            log.info("Résolution dynamique à la volée du DataSource pour : '{}'", currentTenant);
            DataSource ds = dataSourceProvider.getOrCreateTenantDataSource(currentTenant);
            if (ds != null) {
                registerTenantDataSource(currentTenant, ds);
            }
        }

        return currentTenant;
    }

    public Map<Object, Object> getDynamicTargetDataSources() {
        return dynamicTargetDataSources;
    }
}
