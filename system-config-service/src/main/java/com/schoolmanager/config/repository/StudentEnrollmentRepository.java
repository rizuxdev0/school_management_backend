package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, UUID> {
    List<StudentEnrollment> findByTenantId(UUID tenantId);
    List<StudentEnrollment> findByTenantIdAndAcademicYearId(UUID tenantId, UUID academicYearId);
    List<StudentEnrollment> findByClassroomIdAndAcademicYearId(UUID classroomId, UUID academicYearId);
    List<StudentEnrollment> findByClassroomId(UUID classroomId);
    Optional<StudentEnrollment> findByStudentIdAndAcademicYearId(UUID studentId, UUID academicYearId);
    List<StudentEnrollment> findByStudentId(UUID studentId);
}
