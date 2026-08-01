package com.schoolmanager.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class TenantRegistrationDto {
    @NotBlank(message = "Le code de l'établissement est obligatoire")
    private String tenantCode;

    @NotBlank(message = "Le nom de l'établissement est obligatoire")
    private String tenantName;

    private String domainName;

    @NotBlank(message = "Le code du plan d'abonnement est obligatoire")
    private String planCode; // STARTER, PRO, ENTERPRISE, CUSTOM

    private List<String> customModuleCodes; // Si CUSTOM plan

    @NotBlank(message = "Le nom d'utilisateur admin est obligatoire")
    private String adminUsername;

    @NotBlank(message = "L'email admin est obligatoire")
    @Email
    private String adminEmail;

    @NotBlank(message = "Le mot de passe admin est obligatoire")
    private String adminPassword;

    @NotBlank(message = "Le prénom est obligatoire")
    private String adminFirstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String adminLastName;

    private String institutionType;
    private String systemPreset;
    private String currencyCode;
    private String currencySymbol;
    private String currencyNameFr;
    private String defaultLanguage;
}
