package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Représente une classe physique ou promotion (ex: CP-A, Terminale S1)
 * rattachée à un niveau académique pour un établissement (SaaS).
 */
@Entity
@Table(name = "classrooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String code; // ex: CP-A, CM2-B, TLE-S1

    @Column(nullable = false, length = 100)
    private String name; // ex: Cours Préparatoire - Classe A

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_level_id", nullable = false)
    private AcademicLevel academicLevel;

    @Column(nullable = false)
    @Builder.Default
    private Integer capacity = 30; // Capacité maximale de la classe

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}
