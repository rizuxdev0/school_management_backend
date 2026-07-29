package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    List<Room> findByTenantId(UUID tenantId);
    List<Room> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
