package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.AcademicPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {
    List<AcademicPeriod> findByAcademicYearId(UUID academicYearId);
    List<AcademicPeriod> findByTenantId(UUID tenantId);
}
