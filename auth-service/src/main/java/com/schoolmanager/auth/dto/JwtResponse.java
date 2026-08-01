package com.schoolmanager.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {
    private String token;
    @Builder.Default
    private String type = "Bearer";
    private UUID userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private UUID tenantId;
    private String tenantCode;
    /** Nom complet de l'établissement (ex: "INSTITUT POLYTECHNIQUE DEFITECH") */
    private String tenantName;
    private Boolean isSuperAdmin;
    private List<String> roles;
    private List<String> permissions;
    private List<String> enabledModules; // Modules souscrits par cet établissement (SaaS)
    private String planCode;
    private Integer maxStudents;
    private Integer maxStaff;
    private Integer maxClassrooms;
    private Integer maxBooks;
    private String logoUrl;
    private String primaryColor;
    private String institutionType;
    private String systemPreset;
    private String currencyCode;
    private String currencySymbol;
    private String currencyNameFr;
    private String defaultLanguage;
}
