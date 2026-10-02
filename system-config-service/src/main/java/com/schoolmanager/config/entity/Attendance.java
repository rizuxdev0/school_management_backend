package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représente l'état de présence (présent, absent, retard) d'un élève
 * pour une date donnée au sein d'un établissement (SaaS).
 */
@Entity
@Table(name = "attendance", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "attendance_date"})
}, indexes = {
    @Index(name = "idx_attendance_class_date", columnList = "classroom_id, attendance_date"),
    @Index(name = "idx_attendance_tenant_date", columnList = "tenant_id, attendance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PRESENT"; // PRESENT, ABSENT, LATE

    @Column(name = "is_excused")
    @Builder.Default
    private Boolean isExcused = false; // Justifié ou non

    @Column(length = 200)
    private String remarks; // Motifs de retard ou d'absence
}
