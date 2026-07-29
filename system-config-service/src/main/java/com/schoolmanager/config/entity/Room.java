package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Représente une salle de classe, amphithéâtre, bibliothèque ou laboratoire (SaaS Multi-tenant).
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String code; // ex: S-101, AMPHI-A, LAB-INFO

    @Column(nullable = false, length = 150)
    private String name; // ex: Salle 101 (RDC), Amphithéâtre A (Central)

    @Column(nullable = false)
    @Builder.Default
    private Integer capacity = 40;

    @Column(name = "room_type", nullable = false, length = 50)
    @Builder.Default
    private String roomType = "CLASSROOM"; // CLASSROOM, AMPHITHEATER, LABORATORY, LIBRARY, SPORTS, OTHER

    @Column(length = 255)
    private String building; // ex: Bâtiment des Sciences, 1er étage

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}
