package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "grading_systems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder.Default
    @Column(name = "max_score", precision = 5, scale = 2)
    private BigDecimal maxScore = new BigDecimal("20.00");

    @Builder.Default
    @Column(name = "passing_score", precision = 5, scale = 2)
    private BigDecimal passingScore = new BigDecimal("10.00");

    @Column(name = "grading_type", nullable = false, length = 20)
    private String gradingType; // NUMERIC_20, NUMERIC_100, LETTER_AF, GPA

    @Builder.Default
    @Column(name = "is_default")
    private Boolean isDefault = false;
}
