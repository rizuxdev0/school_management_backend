package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {
    List<Evaluation> findByTenantId(UUID tenantId);
    List<Evaluation> findByClassroomIdAndAcademicPeriodId(UUID classroomId, UUID academicPeriodId);
    List<Evaluation> findByTenantIdAndAcademicPeriodId(UUID tenantId, UUID academicPeriodId);
}
