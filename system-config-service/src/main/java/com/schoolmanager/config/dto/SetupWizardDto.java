package com.schoolmanager.config.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class SetupWizardDto {
    private UUID tenantId;
    
    // Étape 1 & 2 : Profil & Devises
    private String institutionName;
    private String institutionType;
    private String defaultLanguage;
    private String currencyCode; // e.g. EUR, XOF, USD
    private String currencySymbol;
    private String currencyNameFr;
    
    // Étape 3 : Presets de Cycles et Niveaux
    private String systemPreset; // "FRENCH", "ENGLISH", "CUSTOM"
    private List<CycleDto> customCycles;

    // Étape 4 : Année académique
    private String academicYearCode;
    private LocalDate yearStartDate;
    private LocalDate yearEndDate;
    private String periodType; // "TRIMESTER", "SEMESTER"

    @Data
    public static class CycleDto {
        private String code;
        private String nameFr;
        private String nameEn;
        private int sequenceOrder;
        private List<LevelDto> levels;
    }

    @Data
    public static class LevelDto {
        private String code;
        private String nameFr;
        private String nameEn;
        private int sequenceOrder;
    }
}
