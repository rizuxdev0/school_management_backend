package com.schoolmanager.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant la configuration globale de la plateforme SaaS.
 * Utilisé pour le mode maintenance et les annonces globales.
 */
@Entity
@Table(name = "global_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSetting {

    @Id
    private UUID id;

    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode;

    @Column(name = "announcement_text", length = 1000)
    private String announcementText;

    @Column(name = "announcement_end")
    private LocalDateTime announcementEnd;
}
