package com.schoolmanager.config.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Paramètres système de l'établissement scolaire (configuration SaaS par tenant).
 * Contient l'identité visuelle complète nécessaire à la génération des bulletins PDF.
 */
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

    // ==================== IDENTITÉ DE L'ÉTABLISSEMENT ====================

    @Column(name = "institution_name", nullable = false, length = 200)
    private String institutionName;

    @Column(name = "institution_type", nullable = false, length = 50)
    private String institutionType; // PUBLIC, PRIVATE, SEMI_PRIVATE

    /** URL du logo (fallback si logoBase64 absent). */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /**
     * Logo encodé en Base64 pour l'utilisation dans les rapports PDF JasperReports.
     * Priorité sur logoUrl. Stocké en TEXT pour la compatibilité PostgreSQL.
     * Format : "data:image/png;base64,iVBORw0KGgo..."
     */
    @Column(name = "logo_base64", columnDefinition = "TEXT")
    private String logoBase64;

    // ==================== COORDONNÉES ====================

    /** Adresse postale complète de l'établissement (ex: "12 rue des Acacias, Abidjan"). */
    @Column(name = "address", length = 500)
    private String address;

    /** Numéro de téléphone principal de l'établissement. */
    @Column(name = "phone", length = 255)
    private String phone;

    /** Email de contact officiel de l'établissement. */
    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /** Site web de l'établissement. */
    @Column(name = "website", length = 200)
    private String website;

    // ==================== IDENTITÉ VISUELLE BULLETINS ====================

    /** Devise ou slogan affiché dans le pied de page des bulletins. Ex: "Pour l'excellence académique". */
    @Column(name = "motto", length = 300)
    private String motto;

    /**
     * Texte du filigrane affiché en fond de page des bulletins PDF.
     * Si null ou vide, le nom de l'établissement est utilisé par défaut.
     * Ex: "CONFIDENTIEL", "DOCUMENT OFFICIEL"
     */
    @Column(name = "watermark_text", length = 100)
    private String watermarkText;

    /** Couleur principale de l'établissement au format hexadécimal (#RRGGBB). Ex: "#0066CC". */
    @Column(name = "primary_color", length = 10)
    private String primaryColor;

    /**
     * Template de bulletin PDF sélectionné par l'établissement.
     * Valeurs possibles :
     *   CLASSIC      — Tableau institutionnel multi-groupes (style algérien)
     *   STRUCTURED   — Colonnes avec nom prof, signature et distinctions (style togolais)
     *   PROFESSIONAL — En-tête coloré, barres de progression, badges (design premium)
     *   MINIMAL      — Design épuré et moderne à typographie fine
     * Valeur par défaut : PROFESSIONAL
     */
    @Builder.Default
    @Column(name = "bulletin_template", length = 30)
    private String bulletinTemplate = "PROFESSIONAL";

    /**
     * Orientation du bulletin PDF sélectionné par l'établissement.
     * Valeurs possibles : PORTRAIT, LANDSCAPE
     * Valeur par défaut : PORTRAIT
     */
    @Builder.Default
    @Column(name = "bulletin_orientation", length = 20)
    private String bulletinOrientation = "PORTRAIT";

    // ==================== LOCALISATION & PARAMÈTRES ====================

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

    // ==================== CONFIGURATIONS RH NOMENCLATURES ====================
    @Builder.Default
    @Column(name = "contract_types", length = 1000)
    private String contractTypes = "Permanent,Vacataire,Contractuel";

    @Builder.Default
    @Column(name = "degrees", length = 1000)
    private String degrees = "Licence,Master,Doctorat";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
