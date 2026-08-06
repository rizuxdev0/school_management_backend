package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.InternalMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface InternalMessageRepository extends JpaRepository<InternalMessage, UUID> {
    List<InternalMessage> findByTenantId(UUID tenantId);

    @Query("SELECT m FROM InternalMessage m WHERE m.tenantId = :tenantId AND " +
           "((m.senderId = :user1 AND m.recipientId = :user2) OR (m.senderId = :user2 AND m.recipientId = :user1)) " +
           "ORDER BY m.timestamp ASC")
    List<InternalMessage> findConversation(
            @Param("tenantId") UUID tenantId,
            @Param("user1") UUID user1,
            @Param("user2") UUID user2);
}
