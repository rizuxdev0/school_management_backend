package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.AcademicCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AcademicCycleRepository extends JpaRepository<AcademicCycle, UUID> {
    List<AcademicCycle> findByTenantIdOrderBySequenceOrderAsc(UUID tenantId);
}
