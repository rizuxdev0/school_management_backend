package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transport_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "start_location", length = 150)
    private String startLocation;

    @Column(name = "end_location", length = 150)
    private String endLocation;

    @Column(name = "stops_json", length = 1000)
    private String stopsJson; // Liste des arrêts sous format JSON ou texte simple

    @Column(name = "monthly_fee", precision = 10, scale = 2)
    private BigDecimal monthlyFee;
}
