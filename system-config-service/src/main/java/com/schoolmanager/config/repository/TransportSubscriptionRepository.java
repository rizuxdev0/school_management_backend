package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.TransportSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransportSubscriptionRepository extends JpaRepository<TransportSubscription, UUID> {
    List<TransportSubscription> findByTenantId(UUID tenantId);
    List<TransportSubscription> findByStudentId(UUID studentId);
}
