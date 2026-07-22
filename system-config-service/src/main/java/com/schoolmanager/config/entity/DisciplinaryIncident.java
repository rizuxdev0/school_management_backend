package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représente un incident disciplinaire (retard répété, insolence, bagarre, tricherie)
 * commis par un élève, avec la sanction infligée (SaaS).
 */
@Entity
@Table(name = "disciplinary_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisciplinaryIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_type", nullable = false, length = 50)
    private String incidentType; // INSOLENCE, TRICHERIE, BAGARRE, ABSENCE_INJUSTIFIEE, DEGRADATION, AUTRE

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(length = 100)
    private String sanction; // AVERTISSEMENT, BLAME, EXCLUSION_TEMPORAIRE, RETENUE, CONSEIL_DISCIPLINE

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, RESOLVED, APPEALED
}
