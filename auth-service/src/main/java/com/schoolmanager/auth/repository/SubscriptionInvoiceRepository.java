package com.schoolmanager.auth.repository;

import com.schoolmanager.auth.entity.SubscriptionInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, UUID> {
    List<SubscriptionInvoice> findByTenantIdOrderByInvoiceDateDesc(UUID tenantId);
}
