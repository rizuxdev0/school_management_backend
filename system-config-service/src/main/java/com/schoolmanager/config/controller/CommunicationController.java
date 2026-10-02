package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.SchoolAnnouncement;
import com.schoolmanager.config.entity.InternalMessage;
import com.schoolmanager.config.repository.SchoolAnnouncementRepository;
import com.schoolmanager.config.repository.InternalMessageRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/system/communication")
@RequiredArgsConstructor
public class CommunicationController {

    private final SchoolAnnouncementRepository schoolAnnouncementRepository;
    private final InternalMessageRepository internalMessageRepository;

    // --- ANNOUNCEMENTS ---

    @GetMapping("/announcements/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('MESSAGING_VIEW') or hasRole('PARENT')")
    public ResponseEntity<List<SchoolAnnouncement>> getAnnouncements(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        List<SchoolAnnouncement> list = schoolAnnouncementRepository.findByTenantIdOrderByDatePublishedDesc(jwtTenantId);

        boolean isParent = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_PARENT".equals(a.getAuthority()));

        if (isParent) {
            list = list.stream()
                    .filter(ann -> "ALL".equalsIgnoreCase(ann.getTargetAudience()) || "PARENTS".equalsIgnoreCase(ann.getTargetAudience()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(list);
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasAuthority('MESSAGING_EDIT')")
    public ResponseEntity<SchoolAnnouncement> saveAnnouncement(@RequestBody SchoolAnnouncement ann) {
        if (!SecurityUtils.isSuperAdmin()) {
            ann.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (ann.getDatePublished() == null) {
            ann.setDatePublished(LocalDateTime.now());
        }
        return ResponseEntity.ok(schoolAnnouncementRepository.save(ann));
    }

    @DeleteMapping("/announcements/{id}")
    @PreAuthorize("hasAuthority('MESSAGING_EDIT')")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable UUID id) {
        SchoolAnnouncement ann = schoolAnnouncementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));
        SecurityUtils.assertOwnership(ann.getTenantId());
        schoolAnnouncementRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- MESSAGES ---

    @GetMapping("/messages/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('MESSAGING_VIEW') or hasRole('PARENT')")
    public ResponseEntity<List<InternalMessage>> getMessages(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(internalMessageRepository.findByTenantId(jwtTenantId));
    }

    @GetMapping("/messages/conversation/user1/{user1}/user2/{user2}")
    @PreAuthorize("hasAuthority('MESSAGING_VIEW') or hasRole('PARENT')")
    public ResponseEntity<List<InternalMessage>> getConversation(
            @PathVariable UUID user1,
            @PathVariable UUID user2) {
        UUID tenantId = SecurityUtils.getCurrentTenantId() != null
                ? SecurityUtils.getCurrentTenantId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
        return ResponseEntity.ok(internalMessageRepository.findConversation(tenantId, user1, user2));
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('MESSAGING_EDIT') or hasRole('PARENT')")
    public ResponseEntity<InternalMessage> sendMessage(@RequestBody InternalMessage msg) {
        if (!SecurityUtils.isSuperAdmin()) {
            msg.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        msg.setTimestamp(LocalDateTime.now());
        return ResponseEntity.ok(internalMessageRepository.save(msg));
    }
}
