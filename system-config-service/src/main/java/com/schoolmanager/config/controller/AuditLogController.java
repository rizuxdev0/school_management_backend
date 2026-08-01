package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.AuditLog;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.service.AuditLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur REST pour la consultation et l'export du journal d'audit.
 * Accessible aux utilisateurs ayant la permission ACADEMIC_VIEW (administrateurs).
 * Le Super Admin peut consulter les logs de n'importe quel tenant.
 */
@RestController
@RequestMapping("/api/v1/system/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Recherche paginée des logs d'audit avec filtres optionnels.
     *
     * @param tenantId   UUID du tenant
     * @param action     Filtre par type d'action (CREATE, UPDATE, DELETE) — optionnel
     * @param entityType Filtre par type d'entité (Student, Payment…) — optionnel
     * @param username   Filtre partiel par nom d'utilisateur — optionnel
     * @param dateFrom   Date de début (ISO 8601) — optionnel
     * @param dateTo     Date de fin (ISO 8601) — optionnel
     * @param page       Numéro de page (0-indexed, défaut 0)
     * @param size       Taille de la page (défaut 25, max 100)
     * @return Page de résultats triés par timestamp DESC
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        // Limiter la taille de page à 100 pour éviter les abus
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize);
        Page<AuditLog> results = auditLogService.search(jwtTenantId, action, entityType, username, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(results);
    }

    /**
     * Export CSV des logs d'audit filtrés.
     * Retourne un fichier CSV téléchargeable directement depuis le navigateur.
     */
    @GetMapping("/tenant/{tenantId}/export")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public void exportCsv(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTo,
            HttpServletResponse response
    ) throws Exception {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);

        List<AuditLog> logs = auditLogService.searchForExport(jwtTenantId, action, entityType, username, dateFrom, dateTo);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=audit_logs.csv");

        // Utiliser uniquement l'OutputStream pour éviter les conflits Servlet
        java.io.OutputStream os = response.getOutputStream();
        os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM UTF-8

        PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        // En-tête CSV
        writer.println("Date;Utilisateur;Action;Type Entité;ID Entité;Description;IP");

        // Données
        for (AuditLog log : logs) {
            writer.printf("%s;%s;%s;%s;%s;%s;%s%n",
                log.getTimestamp() != null ? log.getTimestamp().format(fmt) : "",
                escapeCsv(log.getUsername()),
                escapeCsv(log.getAction()),
                escapeCsv(log.getEntityType()),
                escapeCsv(log.getEntityId()),
                escapeCsv(log.getDescription()),
                escapeCsv(log.getIpAddress())
            );
        }

        writer.flush();
    }

    /**
     * Retourne les types d'entités distincts pour alimenter le dropdown de filtre frontend.
     */
    @GetMapping("/tenant/{tenantId}/entity-types")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<String>> getEntityTypes(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(auditLogService.getDistinctEntityTypes(jwtTenantId));
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    /** Échappe les caractères spéciaux CSV (guillemets, point-virgule). */
    private String escapeCsv(String value) {
        if (value == null) return "";
        // Si la valeur contient un séparateur ou des guillemets, l'entourer de guillemets
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
