package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.entity.Role;
import com.schoolmanager.auth.entity.Tenant;
import com.schoolmanager.auth.entity.User;
import com.schoolmanager.auth.repository.RoleRepository;
import com.schoolmanager.auth.repository.TenantRepository;
import com.schoolmanager.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion administrative des utilisateurs et des professeurs.
 */
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== LISTER LES UTILISATEURS ====================

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<User>> getUsersByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(userRepository.findByTenantId(tenantId));
    }

    @GetMapping("/tenant/{tenantId}/role/{roleCode}")
    public ResponseEntity<List<User>> getUsersByRole(
            @PathVariable UUID tenantId,
            @PathVariable String roleCode) {
        return ResponseEntity.ok(userRepository.findByTenantIdAndRolesCode(tenantId, roleCode));
    }

    // ==================== SAUVEGARDER / MODIFIER UN UTILISATEUR ====================

    @PostMapping
    public ResponseEntity<User> saveUser(@RequestBody User userRequest) {
        // En cas de modification d'un utilisateur existant
        if (userRequest.getId() != null) {
            User existing = userRepository.findById(userRequest.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

            existing.setFirstName(userRequest.getFirstName());
            existing.setLastName(userRequest.getLastName());
            existing.setEmail(userRequest.getEmail());
            existing.setPhoneNumber(userRequest.getPhoneNumber());
            existing.setIsActive(userRequest.getIsActive());

            // Si un nouveau mot de passe a été envoyé (non hashé)
            if (userRequest.getPasswordHash() != null && !userRequest.getPasswordHash().trim().isEmpty() 
                && !userRequest.getPasswordHash().startsWith("$2a$")) {
                existing.setPasswordHash(passwordEncoder.encode(userRequest.getPasswordHash()));
            }

            // Mise à jour des rôles
            if (userRequest.getRoles() != null) {
                Set<Role> roles = new HashSet<>();
                for (Role r : userRequest.getRoles()) {
                    roleRepository.findById(r.getId()).ifPresent(roles::add);
                }
                existing.setRoles(roles);
            }

            return ResponseEntity.ok(userRepository.save(existing));
        }

        // Création d'un nouvel utilisateur
        if (userRequest.getPasswordHash() != null) {
            userRequest.setPasswordHash(passwordEncoder.encode(userRequest.getPasswordHash()));
        }

        // Association du tenant
        if (userRequest.getTenant() != null && userRequest.getTenant().getId() != null) {
            Tenant tenant = tenantRepository.findById(userRequest.getTenant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant introuvable"));
            userRequest.setTenant(tenant);
        }

        // Liaison des rôles
        if (userRequest.getRoles() != null) {
            Set<Role> roles = new HashSet<>();
            for (Role r : userRequest.getRoles()) {
                roleRepository.findById(r.getId()).ifPresent(roles::add);
            }
            userRequest.setRoles(roles);
        }

        return ResponseEntity.ok(userRepository.save(userRequest));
    }

    // ==================== SUPPRIMER UN UTILISATEUR ====================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== RÉCUPÉRER LES RÔLES DISPONIBLES ====================

    @GetMapping("/roles/tenant/{tenantId}")
    public ResponseEntity<List<Role>> getAvailableRoles(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(roleRepository.findByTenantIdOrIsSystemRoleTrue(tenantId));
    }
}
