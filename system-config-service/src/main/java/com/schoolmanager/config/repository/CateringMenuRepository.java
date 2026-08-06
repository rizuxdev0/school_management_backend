package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.CateringMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CateringMenuRepository extends JpaRepository<CateringMenu, UUID> {
    List<CateringMenu> findByTenantId(UUID tenantId);
}
