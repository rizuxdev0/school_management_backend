package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    List<Student> findByTenantId(UUID tenantId);
    Optional<Student> findByTenantIdAndRegistrationNumber(UUID tenantId, String registrationNumber);
}
