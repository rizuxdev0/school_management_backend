package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entité JPA représentant un créneau d'emploi du temps (Timetable Slot)
 * pour une classe ou promotion au sein d'un établissement (multi-tenant SaaS).
 * 
 * Convient à tous les niveaux scolaires (Primaire, Collège, Lycée, Université/Campus)
 * avec support de cours magistraux, TD, TP et examens.
 */
@Entity
@Table(name = "timetable_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Identifiant de l'établissement (isolation stricte multi-tenant SaaS)
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Année académique concernée
     */
    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    /**
     * Classe / Promotion / Amphi concerné (ex: L1-A, 6ème B, CP)
     */
    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    /**
     * ID optionnel de la matière dans le catalogue Subject
     */
    @Column(name = "subject_id")
    private UUID subjectId;

    /**
     * Libellé en français de la matière / unité d'enseignement
     */
    @Column(name = "subject_name_fr", nullable = false, length = 150)
    private String subjectNameFr;

    /**
     * Libellé en anglais de la matière / unité d'enseignement
     */
    @Column(name = "subject_name_en", nullable = false, length = 150)
    private String subjectNameEn;

    /**
     * Nom de l'enseignant / professeur (ex: "Dr. KOFFI Mensah")
     */
    @Column(name = "teacher_name", length = 150)
    private String teacherName;

    /**
     * Salle ou Amphi (ex: "Salle 101", "Amphi A", "Labo Informatique 2")
     */
    @Column(name = "room", length = 100)
    private String room;

    /**
     * Jour de la semaine : MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
     */
    @Column(name = "day_of_week", nullable = false, length = 20)
    private String dayOfWeek;

    /**
     * Heure de début au format HH:mm (ex: "08:00")
     */
    @Column(name = "start_time", nullable = false, length = 10)
    private String startTime;

    /**
     * Heure de fin au format HH:mm (ex: "10:00")
     */
    @Column(name = "end_time", nullable = false, length = 10)
    private String endTime;

    /**
     * Type de séance : COURS, TD, TP, EXAMEN, AUTRE
     */
    @Column(name = "session_type", nullable = false, length = 30)
    @Builder.Default
    private String sessionType = "COURS";

    /**
     * Code couleur hexadécimal pour l'affichage visuel (ex: #3b82f6)
     */
    @Column(name = "color_code", length = 20)
    @Builder.Default
    private String colorCode = "#3b82f6";

    @Column(name = "notes", length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
