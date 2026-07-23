package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représente la convocation d'un élève à une session d'examens donnée.
 */
@Entity
@Table(name = "exam_convocations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "exam_session_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamConvocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exam_session_id", nullable = false)
    private ExamSession examSession;

    @Column(name = "exam_room", length = 50)
    private String examRoom; // Salle d'examen (ex: Amphi A, Salle 4)

    @Column(name = "desk_number", length = 30)
    private String deskNumber; // Numéro de table/place de l'élève

    @Column(name = "convocation_date")
    private LocalDate convocationDate;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
