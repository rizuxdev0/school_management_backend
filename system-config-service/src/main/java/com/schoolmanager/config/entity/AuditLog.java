package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Entité JPA représentant une entrée du journal d'audit.
 * Chaque action sensible (création, modification, suppression) effectuée
 * par un utilisateur est enregistrée automatiquement via l'intercepteur AOP
 * {@link com.schoolmanager.config.security.AuditAspect}.
 *
 * <p>Les entrées sont isolées par tenant (multi-tenant SaaS) et indexées
 * par timestamp pour des requêtes chronologiques performantes.</p>
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_tenant_timestamp", columnList = "tenant_id, timestamp DESC"),
    @Index(name = "idx_audit_entity_type", columnList = "tenant_id, entity_type"),
    @Index(name = "idx_audit_username", columnList = "tenant_id, username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identifiant de l'établissement (isolation multi-tenant). Null pour le Super Admin. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Nom d'utilisateur ayant effectué l'action. */
    @Column(name = "username", nullable = false, length = 80)
    private String username;

    /**
     * Type d'action effectuée.
     * Valeurs : CREATE, UPDATE, DELETE, LOGIN, EXPORT, IMPORT, OTHER
     */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /**
     * Type d'entité métier affectée (nom simplifié de la classe).
     * Ex : "Student", "StudentPayment", "Evaluation", "User"
     */
    @Column(name = "entity_type", length = 80)
    private String entityType;

    /** Identifiant de l'entité affectée (UUID ou identifiant métier). */
    @Column(name = "entity_id", length = 100)
    private String entityId;

    /** Description lisible de l'action (ex: "Création de l'élève KOFFI Aya"). */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Adresse IP du client ayant effectué l'action. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** User-Agent du navigateur (pour le diagnostic). */
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    /** Horodatage de l'action, généré automatiquement à la création. */
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private ZonedDateTime timestamp;
}
