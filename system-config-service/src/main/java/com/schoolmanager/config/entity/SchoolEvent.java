package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entité représentant un événement ou jalon du calendrier scolaire.
 * Géré de manière configurable par établissement (multi-tenant).
 */
@Entity
@Table(name = "school_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
 
    @Column(name = "title_fr", nullable = false, length = 200)
    private String titleFr;

    @Column(name = "title_en", nullable = false, length = 200)
    private String titleEn;

    @Column(name = "description_fr", columnDefinition = "TEXT")
    private String descriptionFr;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Catégorie d'événement :
     * ACADEMIC (ex: rentrée, début cours), EXAM (examens/compos), HOLIDAY (congés, vacances), ADMINISTRATIVE, OTHER
     */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /**
     * Statut de l'événement :
     * PLANNED (Prévu), IN_PROGRESS (En cours), COMPLETED (Achevé)
     */
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PLANNED";

    @Builder.Default
    @Column(name = "is_mandatory")
    private Boolean isMandatory = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
