package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Représente la note obtenue par un élève lors d'une évaluation spécifique.
 */
@Entity
@Table(name = "student_grades", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "evaluation_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score; // Note attribuée à l'élève (ex: 15.5)

    @Column(length = 200)
    private String remarks; // Remarques éventuelles de l'enseignant (ex: Excellent, À encourager)
}
