package com.schoolmanager.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO de rapport après un import Excel ou CSV de données de scolarité (Classes, Élèves, Salles).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDto {
    private int totalRows;
    private int importedCount;
    private int errorCount;
    @Builder.Default
    private List<String> errorMessages = new ArrayList<>();
}
