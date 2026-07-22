package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre les paiements ou versements de scolarité effectués par un élève.
 */
@Entity
@Table(name = "student_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid; // Somme versée

    @Column(name = "payment_date", nullable = false)
    @Builder.Default
    private LocalDate paymentDate = LocalDate.now();

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod; // CASH, MOBILE_MONEY, BANK_TRANSFER, CARD

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber; // Numéro de reçu auto-généré

    @Column(columnDefinition = "TEXT")
    private String notes;
}
