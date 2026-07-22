package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Définit la structure des frais de scolarité par niveau académique
 * pour une année scolaire spécifique au sein d'un établissement (SaaS).
 */
@Entity
@Table(name = "tuition_fees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuitionFee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String name; // ex: Inscription, Scolarité Trimestrielle, Cantine

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // Montant exigé (ex: 250000.00)

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_level_id", nullable = false)
    private AcademicLevel academicLevel;
}
