package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.AcademicLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AcademicLevelRepository extends JpaRepository<AcademicLevel, UUID> {
    List<AcademicLevel> findByTenantIdOrderBySequenceOrderAsc(UUID tenantId);
    List<AcademicLevel> findByCycleIdOrderBySequenceOrderAsc(UUID cycleId);
}
