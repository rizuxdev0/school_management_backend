package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enregistre les bourses d'études, prises en charge d'organismes et exonérations de scolarité
 * accordées à un élève pour une année scolaire donnée.
 * Intègre la protection SaaS par tenantId.
 */
@Entity
@Table(name = "student_scholarships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentScholarship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    /**
     * Type de prise en charge :
     * - SCHOLARSHIP (Bourse d'étude / Mérite / Sociale)
     * - SPONSORSHIP (Prise en charge tiers / Entreprise / Organisme)
     * - DISCOUNT (Exonération / Réduction direction / Fratrie)
     */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /**
     * Libellé explicite (ex: "Bourse d'Excellence L1", "Prise en charge Ministère")
     */
    @Column(name = "title", nullable = false, length = 150)
    private String title;

    /**
     * Mode de réduction :
     * - PERCENTAGE (%)
     * - FIXED_AMOUNT (Montant en FCFA / Devise locale)
     */
    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    /**
     * Valeur de la réduction (ex: 50.00 pour 50%, ou 100000.00 pour un montant fixe)
     */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /**
     * Nom du sponsor ou de l'organisme financeur (optionnel)
     */
    @Column(name = "sponsor_name", length = 100)
    private String sponsorName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
