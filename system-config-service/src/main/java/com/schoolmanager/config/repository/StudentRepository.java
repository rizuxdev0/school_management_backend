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

    /** Cherche les élèves liés à un parent via son téléphone OU son email (Portail Parent). */
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Student s WHERE s.tenantId = :tenantId AND (:phone IS NOT NULL AND s.parentPhone = :phone OR :email IS NOT NULL AND s.email = :email)")
    List<Student> findLinkedStudents(
        @org.springframework.data.repository.query.Param("tenantId") UUID tenantId, 
        @org.springframework.data.repository.query.Param("phone") String phone, 
        @org.springframework.data.repository.query.Param("email") String email
    );

    /** Cherche les élèves d'un tenant dont le parentPhone correspond exactement (liaison directe parent→enfants). */
    List<Student> findByTenantIdAndParentPhone(UUID tenantId, String parentPhone);
}
