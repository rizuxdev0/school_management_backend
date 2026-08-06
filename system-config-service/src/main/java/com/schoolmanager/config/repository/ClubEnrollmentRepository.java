package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.ClubEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClubEnrollmentRepository extends JpaRepository<ClubEnrollment, UUID> {
    List<ClubEnrollment> findByTenantId(UUID tenantId);
    List<ClubEnrollment> findByStudentId(UUID studentId);
    List<ClubEnrollment> findByClubId(UUID clubId);
}
