package com.schoolmanager.config.multitenancy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO pour la demande de provisionnement de base de données dédiée d'un souscripteur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProvisioningRequest {
    private UUID tenantId;
    private String tenantCode;
    private String tenantName;
    private String databaseName;
    private String institutionType;
    private String systemPreset;
    private String currencyCode;
    private String currencySymbol;
    private String currencyNameFr;
    private String defaultLanguage;
}
