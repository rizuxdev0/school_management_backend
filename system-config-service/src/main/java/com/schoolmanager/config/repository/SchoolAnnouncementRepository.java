package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.SchoolAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolAnnouncementRepository extends JpaRepository<SchoolAnnouncement, UUID> {
    List<SchoolAnnouncement> findByTenantIdOrderByDatePublishedDesc(UUID tenantId);
}
