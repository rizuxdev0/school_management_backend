package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour les entrées du journal d'audit.
 * Fournit des requêtes paginées avec filtres multiples pour la consultation
 * et l'export CSV.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Recherche paginée et filtrée des logs d'audit pour un tenant donné.
     * Tous les filtres sont optionnels (null = ignoré).
     *
     * @param tenantId   UUID du tenant (obligatoire)
     * @param action     Filtre par type d'action (CREATE, UPDATE, DELETE…) ou null
     * @param entityType Filtre par type d'entité (Student, Payment…) ou null
     * @param username   Filtre par nom d'utilisateur ou null
     * @param dateFrom   Date de début (incluse) ou null
     * @param dateTo     Date de fin (incluse) ou null
     * @param pageable   Paramètres de pagination et tri
     * @return Page de résultats filtrés
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.tenantId = :tenantId
          AND (cast(:action as String) IS NULL OR a.action = cast(:action as String))
          AND (cast(:entityType as String) IS NULL OR a.entityType = cast(:entityType as String))
          AND (cast(:username as String) IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', cast(:username as String), '%')))
          AND (cast(:dateFrom as Instant) IS NULL OR a.timestamp >= :dateFrom)
          AND (cast(:dateTo as Instant) IS NULL OR a.timestamp <= :dateTo)
        ORDER BY a.timestamp DESC
    """)
    Page<AuditLog> searchLogs(
        @Param("tenantId") UUID tenantId,
        @Param("action") String action,
        @Param("entityType") String entityType,
        @Param("username") String username,
        @Param("dateFrom") ZonedDateTime dateFrom,
        @Param("dateTo") ZonedDateTime dateTo,
        Pageable pageable
    );

    /**
     * Variante non-paginée pour l'export CSV complet.
     * Mêmes filtres que {@link #searchLogs} mais retourne une liste brute.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.tenantId = :tenantId
          AND (cast(:action as String) IS NULL OR a.action = cast(:action as String))
          AND (cast(:entityType as String) IS NULL OR a.entityType = cast(:entityType as String))
          AND (cast(:username as String) IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', cast(:username as String), '%')))
          AND (cast(:dateFrom as Instant) IS NULL OR a.timestamp >= :dateFrom)
          AND (cast(:dateTo as Instant) IS NULL OR a.timestamp <= :dateTo)
        ORDER BY a.timestamp DESC
    """)
    List<AuditLog> searchLogsForExport(
        @Param("tenantId") UUID tenantId,
        @Param("action") String action,
        @Param("entityType") String entityType,
        @Param("username") String username,
        @Param("dateFrom") ZonedDateTime dateFrom,
        @Param("dateTo") ZonedDateTime dateTo
    );

    /**
     * Récupère les types d'entités distincts pour alimenter le dropdown de filtre
     * dans l'interface frontend.
     */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a WHERE a.tenantId = :tenantId AND a.entityType IS NOT NULL ORDER BY a.entityType")
    List<String> findDistinctEntityTypes(@Param("tenantId") UUID tenantId);
}
