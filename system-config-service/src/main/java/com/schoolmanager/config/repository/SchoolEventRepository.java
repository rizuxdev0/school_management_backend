package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.SchoolEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolEventRepository extends JpaRepository<SchoolEvent, UUID> {
    
    /** Récupère tous les événements du calendrier triés chronologiquement pour un tenant donné */
    List<SchoolEvent> findAllByTenantIdOrderByStartDateAsc(UUID tenantId);
    
    /** Compte le nombre d'événements pour le contrôle des quotas SaaS */
    long countByTenantId(UUID tenantId);
}
