package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "currencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 3)
    private String code; // EUR, USD, XOF, GBP, CAD

    @Column(nullable = false, length = 10)
    private String symbol; // €, $, F CFA, £

    @Column(name = "name_fr", nullable = false, length = 100)
    private String nameFr;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Builder.Default
    @Column(name = "decimal_digits")
    private Integer decimalDigits = 2;

    @Builder.Default
    @Column(name = "exchange_rate", nullable = false)
    private java.math.BigDecimal exchangeRate = java.math.BigDecimal.ONE;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "is_system_default")
    private Boolean isSystemDefault = false;
}
