package com.schoolmanager.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Catalogue des modules métiers (ACADEMIC, EVALUATION, FINANCE, etc.) vendus sous forme de lots/packs SaaS.
 */
@Entity
@Table(name = "feature_modules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code; // e.g. ACADEMIC, EVALUATION, ATTENDANCE, FINANCE, EXAMS, LIBRARY, CANTEEN, TRANSPORT, HR, MEDICAL, DISCIPLINE

    @Column(name = "name_fr", nullable = false, length = 100)
    private String nameFr;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "description_fr", columnDefinition = "TEXT")
    private String descriptionFr;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;
}
