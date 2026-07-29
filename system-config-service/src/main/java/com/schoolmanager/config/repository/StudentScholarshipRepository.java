package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.StudentScholarship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentScholarshipRepository extends JpaRepository<StudentScholarship, UUID> {
    List<StudentScholarship> findByTenantId(UUID tenantId);
    List<StudentScholarship> findByStudentId(UUID studentId);
    List<StudentScholarship> findByTenantIdAndAcademicYearId(UUID tenantId, UUID academicYearId);
    List<StudentScholarship> findByStudentIdAndAcademicYearId(UUID studentId, UUID academicYearId);
}
