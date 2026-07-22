package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "institution_name", nullable = false, length = 200)
    private String institutionName;

    @Column(name = "institution_type", nullable = false, length = 50)
    private String institutionType; // PUBLIC, PRIVATE, SEMI_PRIVATE

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Builder.Default
    @Column(name = "default_language", length = 5)
    private String defaultLanguage = "fr";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "main_currency_id")
    private Currency mainCurrency;

    @Builder.Default
    @Column(name = "time_zone", length = 50)
    private String timeZone = "UTC";

    @Builder.Default
    @Column(name = "date_format", length = 20)
    private String dateFormat = "DD/MM/YYYY";

    @Builder.Default
    @Column(name = "enable_sms_notifications")
    private Boolean enableSmsNotifications = false;

    @Builder.Default
    @Column(name = "enable_email_notifications")
    private Boolean enableEmailNotifications = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
