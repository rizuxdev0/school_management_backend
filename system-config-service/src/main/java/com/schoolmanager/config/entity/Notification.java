package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Représente une notification système, académique ou administrative.
 * Supporte la liaison par utilisateur (userId) ou par coordonnée (recipientPhone, recipientEmail)
 * pour un fonctionnement découplé et compatible multi-bases.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Identifiant de l'utilisateur direct destinataire (Admin, Enseignant, etc.).
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Numéro de téléphone destinataire (pour les notifications destinées aux parents).
     */
    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    /**
     * E-mail destinataire (pour les notifications destinées aux élèves ou parents).
     */
    @Column(name = "recipient_email", length = 150)
    private String recipientEmail;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 30)
    private String type; // INFO, WARNING, GRADE, ATTENDANCE

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
