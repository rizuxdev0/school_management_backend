package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.CateringReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CateringReservationRepository extends JpaRepository<CateringReservation, UUID> {
    List<CateringReservation> findByTenantId(UUID tenantId);
    List<CateringReservation> findByStudentId(UUID studentId);
}
