package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.TransportRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransportRouteRepository extends JpaRepository<TransportRoute, UUID> {
    List<TransportRoute> findByTenantId(UUID tenantId);
}
