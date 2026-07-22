package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {
    List<AcademicYear> findByTenantId(UUID tenantId);
    List<AcademicYear> findByTenantIdOrderByStartDateDesc(UUID tenantId);
}
