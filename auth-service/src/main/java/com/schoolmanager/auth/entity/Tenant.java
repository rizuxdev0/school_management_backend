package com.schoolmanager.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité représentant un Établissement Scolaire / Campus / Groupe Éducatif dans le système SaaS Multi-Tenant.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "domain_name", length = 100)
    private String domainName;

    @Column(name = "plan_code", length = 50)
    private String planCode; // ex: STARTER, PRO, ENTERPRISE, CUSTOM

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "max_staff")
    private Integer maxStaff;

    @Column(name = "subscription_expires_at")
    private java.time.ZonedDateTime subscriptionExpiresAt;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "institution_type", length = 50)
    private String institutionType;

    @Column(name = "system_preset", length = 30)
    private String systemPreset;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "currency_symbol", length = 10)
    private String currencySymbol;

    @Column(name = "currency_name_fr", length = 50)
    private String currencyNameFr;

    @Column(name = "default_language", length = 10)
    private String defaultLanguage;

    @Column(name = "max_classrooms")
    private Integer maxClassrooms;

    @Column(name = "max_books")
    private Integer maxBooks;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "tenant_enabled_modules",
        joinColumns = @JoinColumn(name = "tenant_id"),
        inverseJoinColumns = @JoinColumn(name = "module_id")
    )
    @Builder.Default
    private Set<FeatureModule> enabledModules = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
