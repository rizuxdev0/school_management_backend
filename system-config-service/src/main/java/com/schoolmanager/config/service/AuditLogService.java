package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.AuditLog;
import com.schoolmanager.config.repository.AuditLogRepository;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service métier pour la gestion du journal d'audit.
 *
 * <p>Les entrées sont créées de manière <strong>asynchrone</strong> pour ne pas
 * impacter les temps de réponse des requêtes principales. Le service extrait
 * automatiquement le contexte utilisateur (username, tenantId, IP) depuis
 * le SecurityContext et la requête HTTP.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Enregistre une action dans le journal d'audit.
     * Exécuté de manière asynchrone pour éviter tout impact sur les performances.
     *
     * @param action      Type d'action : CREATE, UPDATE, DELETE, LOGIN, EXPORT, IMPORT
     * @param entityType  Nom de l'entité métier affectée (ex: "Student", "Evaluation")
     * @param entityId    Identifiant de l'entité (UUID ou chaîne), peut être null
     * @param description Description lisible de l'action effectuée
     */
    @Async
    public void log(String action, String entityType, String entityId, String description) {
        try {
            UUID tenantId = null;
            String username = "system";

            // Extraction du contexte utilisateur depuis Spring Security
            try {
                UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
                username = principal.getUsername();
                String rawTenantId = principal.getTenantId();
                if (rawTenantId != null && !rawTenantId.isBlank()) {
                    tenantId = UUID.fromString(rawTenantId);
                }
            } catch (Exception ignored) {
                // Contexte de sécurité absent (appel système, batch, etc.)
            }

            // Extraction de l'adresse IP et du User-Agent depuis la requête HTTP
            String ipAddress = null;
            String userAgent = null;
            try {
                ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    ipAddress = extractClientIp(request);
                    userAgent = truncate(request.getHeader("User-Agent"), 300);
                }
            } catch (Exception ignored) {
                // Contexte de requête absent (appel asynchrone interne)
            }

            AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Ne jamais propager une exception d'audit vers le flux principal
            log.error("Erreur lors de l'écriture du log d'audit : {}", e.getMessage(), e);
        }
    }

    /**
     * Recherche paginée des logs d'audit avec filtres optionnels.
     */
    public Page<AuditLog> search(UUID tenantId, String action, String entityType,
                                  String username, ZonedDateTime dateFrom, ZonedDateTime dateTo,
                                  Pageable pageable) {
        return auditLogRepository.searchLogs(tenantId, action, entityType, username, dateFrom, dateTo, pageable);
    }

    /**
     * Recherche non-paginée pour l'export CSV.
     */
    public List<AuditLog> searchForExport(UUID tenantId, String action, String entityType,
                                           String username, ZonedDateTime dateFrom, ZonedDateTime dateTo) {
        return auditLogRepository.searchLogsForExport(tenantId, action, entityType, username, dateFrom, dateTo);
    }

    /**
     * Retourne les types d'entités distincts pour alimenter le dropdown de filtre frontend.
     */
    public List<String> getDistinctEntityTypes(UUID tenantId) {
        return auditLogRepository.findDistinctEntityTypes(tenantId);
    }

    // ------------------------------------------------------------------
    // Utilitaires privés
    // ------------------------------------------------------------------

    /**
     * Extrait l'adresse IP réelle du client en tenant compte des proxies
     * (headers X-Forwarded-For, X-Real-IP).
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For peut contenir plusieurs IPs : la première est le client réel
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** Tronque une chaîne à la longueur maximale spécifiée. */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
