package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Règle d'attribution des mentions d'honneur et appréciations du conseil de classe
 * personnalisable par chaque établissement (tenant) selon la moyenne générale (/20 ou barème équivalent).
 */
@Entity
@Table(name = "honors_appreciation_rules", indexes = {
        @Index(name = "idx_honors_rules_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HonorsAppreciationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank(message = "Le code de la mention est obligatoire")
    @Column(nullable = false, length = 50)
    private String code; // EXCELLENT, VERY_GOOD, GOOD, PASS, POOR, WARNING, CUSTOM

    @NotBlank(message = "L'intitulé en français est obligatoire")
    @Column(name = "mention_fr", nullable = false, length = 150)
    private String mentionFr; // ex: "Félicitations du Conseil"

    @Column(name = "mention_en", length = 150)
    private String mentionEn; // ex: "Congratulations of the Board"

    @NotNull(message = "La moyenne minimale est obligatoire")
    @DecimalMin(value = "0.00", message = "La note minimale ne peut être inférieure à 0")
    @DecimalMax(value = "20.00", message = "La note minimale ne peut dépasser 20")
    @Column(name = "min_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal minScore;

    @NotNull(message = "La moyenne maximale est obligatoire")
    @DecimalMin(value = "0.00", message = "La note maximale ne peut être inférieure à 0")
    @DecimalMax(value = "20.00", message = "La note maximale ne peut dépasser 20")
    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Builder.Default
    @Column(name = "color_code", length = 20)
    private String colorCode = "#10b981"; // Couleur du badge (ex: #10b981 vert, #3b82f6 bleu, #f59e0b jaune, #ef4444 rouge)

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 1;
}
