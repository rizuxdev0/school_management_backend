package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.HonorsAppreciationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HonorsAppreciationRuleRepository extends JpaRepository<HonorsAppreciationRule, UUID> {

    List<HonorsAppreciationRule> findByTenantIdOrderByMinScoreDesc(UUID tenantId);

    List<HonorsAppreciationRule> findByTenantIdOrderByDisplayOrderAsc(UUID tenantId);

    void deleteByTenantId(UUID tenantId);
}
