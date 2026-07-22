package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    List<MedicalRecord> findByTenantId(UUID tenantId);
    List<MedicalRecord> findByStudentId(UUID studentId);
}
