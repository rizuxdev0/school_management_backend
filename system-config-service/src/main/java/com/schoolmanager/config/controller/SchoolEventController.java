package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.SchoolEvent;
import com.schoolmanager.config.repository.SchoolEventRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion du Calendrier Pédagogique et Annuel (School Calendar).
 * Impose le multi-tenant par isolation et applique les limites de quotas d'offres SaaS (planCode).
 */
@RestController
@RequestMapping("/api/v1/system/calendar/events")
@RequiredArgsConstructor
public class SchoolEventController {

    private final SchoolEventRepository schoolEventRepository;

    /**
     * Récupère tous les événements du calendrier de l'établissement connecté.
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<SchoolEvent>> getEventsByTenant(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(schoolEventRepository.findAllByTenantIdOrderByStartDateAsc(jwtTenantId));
    }

    /**
     * Crée ou met à jour un événement du calendrier.
     * Applique un contrôle de quota de 30 événements maximum pour l'offre STARTER.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<SchoolEvent> saveEvent(@RequestBody SchoolEvent event) {
        UUID tenantId = SecurityUtils.isSuperAdmin() ? event.getTenantId() : SecurityUtils.getCurrentTenantId();
        
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le tenantId est obligatoire.");
        }
        
        event.setTenantId(tenantId);

        // --- CONTRÔLE DE QUOTAS SAAS ---
        if (!SecurityUtils.isSuperAdmin()) {
            String plan = SecurityUtils.getCurrentPlanCode();
            // Si l'offre est l'offre de démarrage (STARTER)
            if ("STARTER".equalsIgnoreCase(plan)) {
                long currentCount = schoolEventRepository.countByTenantId(tenantId);
                // Si on tente d'insérer un nouvel événement (id est null) et qu'on a déjà 30 événements
                if (event.getId() == null && currentCount >= 30) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, 
                        "Limite de quota atteinte : Votre forfait STARTER ne permet pas d'enregistrer plus de 30 événements sur le calendrier annuel. Veuillez passer à un forfait supérieur."
                    );
                }
            }
        }

        // --- CONTRÔLE ANTI-IDOR SUR MODIFICATION ---
        if (event.getId() != null) {
            SchoolEvent existing = schoolEventRepository.findById(event.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Événement introuvable"));
            SecurityUtils.assertOwnership(existing.getTenantId());
        }

        return ResponseEntity.ok(schoolEventRepository.save(event));
    }

    /**
     * Supprime un événement du calendrier.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        SchoolEvent event = schoolEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Événement introuvable"));
        
        SecurityUtils.assertOwnership(event.getTenantId());
        schoolEventRepository.deleteById(id);
        
        return ResponseEntity.noContent().build();
    }
}
