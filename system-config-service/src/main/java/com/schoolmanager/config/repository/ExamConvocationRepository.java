package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.ExamConvocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamConvocationRepository extends JpaRepository<ExamConvocation, UUID> {
    List<ExamConvocation> findByTenantId(UUID tenantId);
    List<ExamConvocation> findByExamSessionId(UUID examSessionId);
    Optional<ExamConvocation> findByStudentIdAndExamSessionId(UUID studentId, UUID examSessionId);
}
