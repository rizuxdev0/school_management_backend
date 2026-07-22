package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Représente une matière enseignée (ex: Mathématiques, Français, Histoire)
 * avec son coefficient par défaut au sein d'un établissement (SaaS).
 */
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String code; // ex: MATH, PHYS, HIST

    @Column(name = "name_fr", nullable = false, length = 100)
    private String nameFr;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal coefficient = new BigDecimal("1.00");

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}
