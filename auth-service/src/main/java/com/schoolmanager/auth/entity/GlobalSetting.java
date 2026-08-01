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

    @Column(name = "password_min_length", nullable = false, columnDefinition = "integer default 8")
    @Builder.Default
    private int passwordMinLength = 8;

    @Column(name = "password_require_uppercase", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean passwordRequireUppercase = true;

    @Column(name = "password_require_lowercase", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean passwordRequireLowercase = true;

    @Column(name = "password_require_number", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean passwordRequireNumber = true;

    @Column(name = "password_require_special", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean passwordRequireSpecial = true;
}
