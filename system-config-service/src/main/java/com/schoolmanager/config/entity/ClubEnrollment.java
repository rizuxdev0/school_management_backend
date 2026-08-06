package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "club_enrollments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "club_id", nullable = false)
    private SchoolClub club;

    @Builder.Default
    @Column(name = "parent_consent_approved")
    private Boolean parentConsentApproved = false;

    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;
}
