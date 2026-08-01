package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Notification;
import com.schoolmanager.config.security.SecurityUtils;
import com.schoolmanager.config.security.UserPrincipal;
import com.schoolmanager.config.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion et la consultation des notifications en temps réel.
 */
@RestController
@RequestMapping("/api/v1/system/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications() {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        List<Notification> list = notificationService.getMyNotifications(
                tenantId,
                principal.getUserId(),
                principal.getPhoneNumber(),
                principal.getEmail()
        );
        return ResponseEntity.ok(list);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getMyUnreadCount() {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        long count = notificationService.getMyUnreadCount(
                tenantId,
                principal.getUserId(),
                principal.getPhoneNumber(),
                principal.getEmail()
        );
        return ResponseEntity.ok(count);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        notificationService.markAsRead(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        UserPrincipal principal = SecurityUtils.getCurrentPrincipal();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        notificationService.markAllAsRead(
                tenantId,
                principal.getUserId(),
                principal.getPhoneNumber(),
                principal.getEmail()
        );
        return ResponseEntity.noContent().build();
    }
}
