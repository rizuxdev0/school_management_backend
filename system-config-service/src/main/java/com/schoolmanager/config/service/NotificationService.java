package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.Notification;
import com.schoolmanager.config.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications(UUID tenantId, UUID userId, String phone, String email) {
        return notificationRepository.findMyNotifications(tenantId, userId, phone, email);
    }

    @Transactional(readOnly = true)
    public long getMyUnreadCount(UUID tenantId, UUID userId, String phone, String email) {
        return notificationRepository.countMyUnreadNotifications(tenantId, userId, phone, email);
    }

    @Transactional
    public void markAsRead(UUID tenantId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification introuvable"));
        if (notification.getTenantId().equals(tenantId)) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(UUID tenantId, UUID userId, String phone, String email) {
        notificationRepository.markAllMyAsRead(tenantId, userId, phone, email);
    }

    /**
     * Crée et envoie une notification générique ou ciblée.
     */
    @Transactional
    public void sendNotification(UUID tenantId, UUID userId, String phone, String email, String title, String message, String type) {
        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .userId(userId)
                .recipientPhone(phone)
                .recipientEmail(email)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }
}
