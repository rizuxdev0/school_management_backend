package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.DisciplinaryIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisciplinaryIncidentRepository extends JpaRepository<DisciplinaryIncident, UUID> {
    List<DisciplinaryIncident> findByTenantId(UUID tenantId);
    List<DisciplinaryIncident> findByStudentId(UUID studentId);
}
