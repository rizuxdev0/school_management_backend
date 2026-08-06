package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Bus;
import com.schoolmanager.config.entity.Driver;
import com.schoolmanager.config.entity.TransportRoute;
import com.schoolmanager.config.entity.TransportSubscription;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.repository.BusRepository;
import com.schoolmanager.config.repository.DriverRepository;
import com.schoolmanager.config.repository.TransportRouteRepository;
import com.schoolmanager.config.repository.TransportSubscriptionRepository;
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
@RequestMapping("/api/v1/system/transport")
@RequiredArgsConstructor
public class TransportController {

    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final TransportRouteRepository transportRouteRepository;
    private final TransportSubscriptionRepository transportSubscriptionRepository;
    private final StudentRepository studentRepository;

    // --- BUSES ---

    @GetMapping("/buses/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public ResponseEntity<List<Bus>> getBuses(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(busRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/buses")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<Bus> saveBus(@RequestBody Bus bus) {
        if (!SecurityUtils.isSuperAdmin()) {
            bus.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(busRepository.save(bus));
    }

    @DeleteMapping("/buses/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<Void> deleteBus(@PathVariable UUID id) {
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus introuvable"));
        SecurityUtils.assertOwnership(bus.getTenantId());
        busRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- DRIVERS ---

    @GetMapping("/drivers/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public ResponseEntity<List<Driver>> getDrivers(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(driverRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/drivers")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<Driver> saveDriver(@RequestBody Driver driver) {
        if (!SecurityUtils.isSuperAdmin()) {
            driver.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(driverRepository.save(driver));
    }

    @DeleteMapping("/drivers/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<Void> deleteDriver(@PathVariable UUID id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chauffeur introuvable"));
        SecurityUtils.assertOwnership(driver.getTenantId());
        driverRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- ROUTES ---

    @GetMapping("/routes/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public ResponseEntity<List<TransportRoute>> getRoutes(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(transportRouteRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/routes")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<TransportRoute> saveRoute(@RequestBody TransportRoute route) {
        if (!SecurityUtils.isSuperAdmin()) {
            route.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(transportRouteRepository.save(route));
    }

    @DeleteMapping("/routes/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID id) {
        TransportRoute route = transportRouteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route introuvable"));
        SecurityUtils.assertOwnership(route.getTenantId());
        transportRouteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- SUBSCRIPTIONS ---

    @GetMapping("/subscriptions/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('TRANSPORT_VIEW')")
    public ResponseEntity<List<TransportSubscription>> getSubscriptions(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(transportSubscriptionRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<TransportSubscription> saveSubscription(@RequestBody TransportSubscription sub) {
        if (!SecurityUtils.isSuperAdmin()) {
            sub.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        // Resolve student
        if (sub.getStudent() != null && sub.getStudent().getId() != null) {
            Student s = studentRepository.findById(sub.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            sub.setStudent(s);
        }
        // Resolve route
        if (sub.getRoute() != null && sub.getRoute().getId() != null) {
            TransportRoute r = transportRouteRepository.findById(sub.getRoute().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trajet introuvable"));
            sub.setRoute(r);
        }
        return ResponseEntity.ok(transportSubscriptionRepository.save(sub));
    }

    @DeleteMapping("/subscriptions/{id}")
    @PreAuthorize("hasAuthority('TRANSPORT_EDIT')")
    public ResponseEntity<Void> deleteSubscription(@PathVariable UUID id) {
        TransportSubscription sub = transportSubscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abonnement introuvable"));
        SecurityUtils.assertOwnership(sub.getTenantId());
        transportSubscriptionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
