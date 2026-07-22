package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {
    List<Classroom> findByTenantId(UUID tenantId);
    List<Classroom> findByTenantIdAndAcademicLevelId(UUID tenantId, UUID academicLevelId);
}
