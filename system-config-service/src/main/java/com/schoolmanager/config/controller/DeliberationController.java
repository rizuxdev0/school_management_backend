package com.schoolmanager.config.controller;

import com.schoolmanager.config.service.DeliberationService;
import com.schoolmanager.config.service.DeliberationService.DeliberationSimulationDto;
import com.schoolmanager.config.service.DeliberationService.DeliberationRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/deliberations")
@RequiredArgsConstructor
public class DeliberationController {

    private final DeliberationService deliberationService;

    @GetMapping("/simulate/classroom/{classroomId}/year/{yearId}")
    @PreAuthorize("hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<DeliberationSimulationDto>> simulateDeliberation(
            @PathVariable UUID classroomId,
            @PathVariable UUID yearId) {
        return ResponseEntity.ok(deliberationService.simulateDeliberation(classroomId, yearId));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('ACADEMIC_EDIT')")
    public ResponseEntity<Void> executeDeliberation(@RequestBody DeliberationRequestDto request) {
        deliberationService.executeDeliberation(request);
        return ResponseEntity.ok().build();
    }
}
