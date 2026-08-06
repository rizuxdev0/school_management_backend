package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.CateringSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CateringSubscriptionRepository extends JpaRepository<CateringSubscription, UUID> {
    List<CateringSubscription> findByTenantId(UUID tenantId);
    List<CateringSubscription> findByStudentId(UUID studentId);
}
