package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "catering_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CateringReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Builder.Default
    @Column(length = 20)
    private String status = "RESERVED"; // RESERVED, CONSUMED, CANCELLED
}
