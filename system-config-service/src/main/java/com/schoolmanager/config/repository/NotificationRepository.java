package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId AND (" +
           "(:userId IS NOT NULL AND n.userId = :userId) OR " +
           "(:phone IS NOT NULL AND n.recipientPhone = :phone) OR " +
           "(:email IS NOT NULL AND n.recipientEmail = :email)" +
           ") ORDER BY n.createdAt DESC")
    List<Notification> findMyNotifications(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("phone") String phone,
            @Param("email") String email
    );

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.tenantId = :tenantId AND n.isRead = false AND (" +
           "(:userId IS NOT NULL AND n.userId = :userId) OR " +
           "(:phone IS NOT NULL AND n.recipientPhone = :phone) OR " +
           "(:email IS NOT NULL AND n.recipientEmail = :email)" +
           ")")
    long countMyUnreadNotifications(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("phone") String phone,
            @Param("email") String email
    );

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.tenantId = :tenantId AND (" +
           "(:userId IS NOT NULL AND n.userId = :userId) OR " +
           "(:phone IS NOT NULL AND n.recipientPhone = :phone) OR " +
           "(:email IS NOT NULL AND n.recipientEmail = :email)" +
           ")")
    void markAllMyAsRead(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("phone") String phone,
            @Param("email") String email
    );
}
