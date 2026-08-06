package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.CateringMenu;
import com.schoolmanager.config.entity.CateringSubscription;
import com.schoolmanager.config.entity.CateringReservation;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.repository.CateringMenuRepository;
import com.schoolmanager.config.repository.CateringSubscriptionRepository;
import com.schoolmanager.config.repository.CateringReservationRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/catering")
@RequiredArgsConstructor
public class CateringController {

    private final CateringMenuRepository cateringMenuRepository;
    private final CateringSubscriptionRepository cateringSubscriptionRepository;
    private final CateringReservationRepository cateringReservationRepository;
    private final StudentRepository studentRepository;

    // --- MENUS ---

    @GetMapping("/menus/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('CATERING_VIEW')")
    public ResponseEntity<List<CateringMenu>> getMenus(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(cateringMenuRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/menus")
    @PreAuthorize("hasAuthority('CATERING_EDIT')")
    public ResponseEntity<CateringMenu> saveMenu(@RequestBody CateringMenu menu) {
        if (!SecurityUtils.isSuperAdmin()) {
            menu.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(cateringMenuRepository.save(menu));
    }

    @DeleteMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('CATERING_EDIT')")
    public ResponseEntity<Void> deleteMenu(@PathVariable UUID id) {
        CateringMenu menu = cateringMenuRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu introuvable"));
        SecurityUtils.assertOwnership(menu.getTenantId());
        cateringMenuRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- SUBSCRIPTIONS ---

    @GetMapping("/subscriptions/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('CATERING_VIEW')")
    public ResponseEntity<List<CateringSubscription>> getSubscriptions(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(cateringSubscriptionRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasAuthority('CATERING_EDIT')")
    public ResponseEntity<CateringSubscription> saveSubscription(@RequestBody CateringSubscription sub) {
        if (!SecurityUtils.isSuperAdmin()) {
            sub.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (sub.getStudent() != null && sub.getStudent().getId() != null) {
            Student s = studentRepository.findById(sub.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            sub.setStudent(s);
        }
        return ResponseEntity.ok(cateringSubscriptionRepository.save(sub));
    }

    @DeleteMapping("/subscriptions/{id}")
    @PreAuthorize("hasAuthority('CATERING_EDIT')")
    public ResponseEntity<Void> deleteSubscription(@PathVariable UUID id) {
        CateringSubscription sub = cateringSubscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abonnement introuvable"));
        SecurityUtils.assertOwnership(sub.getTenantId());
        cateringSubscriptionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- RESERVATIONS ---

    @GetMapping("/reservations/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('CATERING_VIEW')")
    public ResponseEntity<List<CateringReservation>> getReservations(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(cateringReservationRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAuthority('CATERING_EDIT')")
    public ResponseEntity<CateringReservation> saveReservation(@RequestBody CateringReservation res) {
        if (!SecurityUtils.isSuperAdmin()) {
            res.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (res.getStudent() != null && res.getStudent().getId() != null) {
            Student s = studentRepository.findById(res.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            res.setStudent(s);
        }
        return ResponseEntity.ok(cateringReservationRepository.save(res));
    }

    @DeleteMapping("/reservations/{id}")
    @PreAuthorize("hasAuthority('CATERING_EDIT')")
    public ResponseEntity<Void> deleteReservation(@PathVariable UUID id) {
        CateringReservation res = cateringReservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable"));
        SecurityUtils.assertOwnership(res.getTenantId());
        cateringReservationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
