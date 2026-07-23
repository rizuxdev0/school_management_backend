package com.schoolmanager.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Tenant tenant;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "invoice_date", nullable = false)
    private ZonedDateTime invoiceDate;

    @Column(name = "due_date")
    private ZonedDateTime dueDate;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus; // PAID, UNPAID, OVERDUE

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod; // CARD, TRANSFER, CASH, MOBILE_MONEY
}
