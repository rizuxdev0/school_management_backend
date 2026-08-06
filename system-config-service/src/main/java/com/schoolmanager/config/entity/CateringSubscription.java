package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "catering_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CateringSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName; // ex: Demi-pensionnaire, Pensionnaire, Externe

    @Column(name = "allergies_notes", length = 500)
    private String allergiesNotes;

    @Builder.Default
    @Column(length = 20)
    private String status = "ACTIVE"; // ACTIVE, SUSPENDED, EXPIRED
}
