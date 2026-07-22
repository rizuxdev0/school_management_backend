package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.StudentPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentPaymentRepository extends JpaRepository<StudentPayment, UUID> {
    List<StudentPayment> findByTenantId(UUID tenantId);
    List<StudentPayment> findByStudentId(UUID studentId);
    List<StudentPayment> findByTenantIdAndAcademicYearId(UUID tenantId, UUID academicYearId);
    List<StudentPayment> findByStudentIdAndAcademicYearId(UUID studentId, UUID academicYearId);
}
