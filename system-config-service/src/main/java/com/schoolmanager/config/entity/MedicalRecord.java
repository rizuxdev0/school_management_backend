package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représente une consultation ou un passage à l'infirmerie de l'établissement
 * pour un élève (SaaS).
 */
@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(length = 250)
    private String symptoms; // Symptômes constatés (ex: Maux de tête, Fièvre)

    @Column(length = 250)
    private String diagnosis; // Diagnostic suspecté ou avéré

    @Column(length = 250)
    private String treatment; // Traitement administré (ex: Paracétamol 500mg)

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature; // Température corporelle (ex: 38.5)

    @Column(name = "action_taken", length = 100)
    private String actionTaken; // Suite donnée (ex: Retour en classe, Repos infirmerie, Hôpital)
}
