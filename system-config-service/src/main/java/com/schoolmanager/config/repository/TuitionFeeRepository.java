package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.TuitionFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TuitionFeeRepository extends JpaRepository<TuitionFee, UUID> {
    List<TuitionFee> findByTenantId(UUID tenantId);
    List<TuitionFee> findByTenantIdAndAcademicYearId(UUID tenantId, UUID academicYearId);
    List<TuitionFee> findByAcademicLevelIdAndAcademicYearId(UUID academicLevelId, UUID academicYearId);
}
