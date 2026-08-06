package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "license_number", nullable = false, length = 100)
    private String licenseNumber;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;
}
