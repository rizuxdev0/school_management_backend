package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "buses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Column(nullable = false)
    private Integer capacity;

    @Column(length = 100)
    private String model;

    @Builder.Default
    @Column(length = 20)
    private String status = "ACTIVE"; // ACTIVE, MAINTENANCE
}
