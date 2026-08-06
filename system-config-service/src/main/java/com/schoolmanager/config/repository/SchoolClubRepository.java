package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.SchoolClub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolClubRepository extends JpaRepository<SchoolClub, UUID> {
    List<SchoolClub> findByTenantId(UUID tenantId);
}
