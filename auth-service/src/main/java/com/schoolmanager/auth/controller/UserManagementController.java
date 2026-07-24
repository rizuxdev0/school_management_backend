package com.schoolmanager.auth.controller;

import com.schoolmanager.auth.entity.Permission;
import com.schoolmanager.auth.entity.Role;
import com.schoolmanager.auth.entity.Tenant;
import com.schoolmanager.auth.entity.User;
import com.schoolmanager.auth.repository.PermissionRepository;
import com.schoolmanager.auth.repository.RoleRepository;
import com.schoolmanager.auth.repository.TenantRepository;
import com.schoolmanager.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion administrative des utilisateurs, des professeurs, des rôles et des permissions.
 * Sécurisé avec @PreAuthorize et isolation multi-tenant forcée depuis la session utilisateur.
 */
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    // Helper pour récupérer l'utilisateur connecté
    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
    }

    // Helper pour valider l'appartenance tenant
    private void assertOwnership(UUID entityTenantId) {
        User currentUser = getCurrentUser();
        if (Boolean.TRUE.equals(currentUser.getIsSuperAdmin())) {
            return; // Super Admin exempté
        }
        if (currentUser.getTenant() == null || !currentUser.getTenant().getId().equals(entityTenantId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Accès refusé : cette ressource n'appartient pas à votre établissement"
            );
        }
    }

    // ==================== LISTER LES UTILISATEURS ====================

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public ResponseEntity<List<User>> getUsersByTenant(@PathVariable UUID tenantId) {
        User currentUser = getCurrentUser();
        UUID targetTenantId = Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) ? tenantId : currentUser.getTenant().getId();
        return ResponseEntity.ok(userRepository.findByTenantId(targetTenantId));
    }

    @GetMapping("/tenant/{tenantId}/role/{roleCode}")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public ResponseEntity<List<User>> getUsersByRole(
            @PathVariable UUID tenantId,
            @PathVariable String roleCode) {
        User currentUser = getCurrentUser();
        UUID targetTenantId = Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) ? tenantId : currentUser.getTenant().getId();
        return ResponseEntity.ok(userRepository.findByTenantIdAndRolesCode(targetTenantId, roleCode));
    }

    // ==================== SAUVEGARDER / MODIFIER UN UTILISATEUR ====================

    @PostMapping
    @PreAuthorize("hasAuthority('HR_EDIT')")
    public ResponseEntity<User> saveUser(@RequestBody User userRequest) {
        User currentUser = getCurrentUser();

        // En cas de modification d'un utilisateur existant
        if (userRequest.getId() != null) {
            User existing = userRepository.findById(userRequest.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

            // Vérification de sécurité multi-tenant
            if (existing.getTenant() != null) {
                assertOwnership(existing.getTenant().getId());
            }

            existing.setFirstName(userRequest.getFirstName());
            existing.setLastName(userRequest.getLastName());
            existing.setEmail(userRequest.getEmail());
            existing.setPhoneNumber(userRequest.getPhoneNumber());
            existing.setIsActive(userRequest.getIsActive());
            
            // Recopie des attributs de profil enseignants
            existing.setSpecialty(userRequest.getSpecialty());
            existing.setContractType(userRequest.getContractType());
            existing.setWeeklyHours(userRequest.getWeeklyHours());
            existing.setDegree(userRequest.getDegree());

            // Si un nouveau mot de passe a été envoyé (non hashé)
            if (userRequest.getPasswordHash() != null && !userRequest.getPasswordHash().trim().isEmpty() 
                && !userRequest.getPasswordHash().startsWith("$2a$")) {
                if (!com.schoolmanager.auth.security.PasswordValidator.isValid(userRequest.getPasswordHash())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe ne respecte pas les critères de complexité (min 8 caractères, 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial).");
                }
                existing.setPasswordHash(passwordEncoder.encode(userRequest.getPasswordHash()));
            }

            // Mise à jour des rôles
            if (userRequest.getRoles() != null) {
                Set<Role> roles = new HashSet<>();
                for (Role r : userRequest.getRoles()) {
                    roleRepository.findById(r.getId()).ifPresent(role -> {
                        // Un tenant ne peut pas attribuer un rôle appartenant à un autre tenant
                        if (role.getTenant() != null) {
                            assertOwnership(role.getTenant().getId());
                        }
                        roles.add(role);
                    });
                }
                existing.setRoles(roles);
            }

            return ResponseEntity.ok(userRepository.save(existing));
        }

        // Création d'un nouvel utilisateur
        UUID targetTenantId = Boolean.TRUE.equals(currentUser.getIsSuperAdmin())
                ? (userRequest.getTenant() != null ? userRequest.getTenant().getId() : null)
                : (currentUser.getTenant() != null ? currentUser.getTenant().getId() : null);

        if (targetTenantId != null) {
            Tenant tenant = tenantRepository.findById(targetTenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Établissement introuvable"));

            Integer maxStaff = tenant.getMaxStaff();
            if (maxStaff != null) {
                long currentStaffCount = userRepository.countByTenantId(targetTenantId);
                if (currentStaffCount >= maxStaff) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Limite de quota de personnel atteinte (" + maxStaff + " max). Veuillez mettre à niveau votre forfait SaaS."
                    );
                }
            }
        }

        if (userRequest.getPasswordHash() != null) {
            if (!com.schoolmanager.auth.security.PasswordValidator.isValid(userRequest.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe ne respecte pas les critères de complexité (min 8 caractères, 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial).");
            }
            userRequest.setPasswordHash(passwordEncoder.encode(userRequest.getPasswordHash()));
        }

        // Association forcée du tenant de l'utilisateur connecté (sauf si Super Admin)
        if (Boolean.TRUE.equals(currentUser.getIsSuperAdmin())) {
            if (userRequest.getTenant() != null && userRequest.getTenant().getId() != null) {
                Tenant tenant = tenantRepository.findById(userRequest.getTenant().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant introuvable"));
                userRequest.setTenant(tenant);
            }
        } else {
            userRequest.setTenant(currentUser.getTenant());
        }

        // Liaison des rôles
        if (userRequest.getRoles() != null) {
            Set<Role> roles = new HashSet<>();
            for (Role r : userRequest.getRoles()) {
                roleRepository.findById(r.getId()).ifPresent(role -> {
                    if (role.getTenant() != null) {
                        assertOwnership(role.getTenant().getId());
                    }
                    roles.add(role);
                });
            }
            userRequest.setRoles(roles);
        }

        return ResponseEntity.ok(userRepository.save(userRequest));
    }

    // ==================== SUPPRIMER UN UTILISATEUR ====================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('HR_EDIT')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        
        if (user.getTenant() != null) {
            assertOwnership(user.getTenant().getId());
        }
        
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== RÉCUPÉRER LES RÔLES DISPONIBLES ====================

    @GetMapping("/roles/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public ResponseEntity<List<Role>> getAvailableRoles(@PathVariable UUID tenantId) {
        User currentUser = getCurrentUser();
        UUID targetTenantId = Boolean.TRUE.equals(currentUser.getIsSuperAdmin()) ? tenantId : currentUser.getTenant().getId();
        return ResponseEntity.ok(roleRepository.findByTenantIdOrIsSystemRoleTrue(targetTenantId));
    }

    // ==================== GESTION DES RÔLES ET PERMISSIONS ====================

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('HR_VIEW')")
    public ResponseEntity<List<Permission>> getAllPermissions() {
        return ResponseEntity.ok(permissionRepository.findAll());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('HR_EDIT')")
    public ResponseEntity<Role> saveRole(@RequestBody Role roleRequest) {
        User currentUser = getCurrentUser();

        if (roleRequest.getId() != null) {
            Role existing = roleRepository.findById(roleRequest.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rôle introuvable"));
            
            if (existing.getTenant() != null) {
                assertOwnership(existing.getTenant().getId());
            }

            existing.setNameFr(roleRequest.getNameFr());
            existing.setNameEn(roleRequest.getNameEn() != null ? roleRequest.getNameEn() : roleRequest.getNameFr());
            existing.setDescription(roleRequest.getDescription());
            
            // Map permissions
            if (roleRequest.getPermissions() != null) {
                Set<Permission> perms = new HashSet<>();
                for (Permission p : roleRequest.getPermissions()) {
                    permissionRepository.findById(p.getId()).ifPresent(perms::add);
                }
                existing.setPermissions(perms);
            }
            return ResponseEntity.ok(roleRepository.save(existing));
        }

        // Association forcée du tenant
        if (Boolean.TRUE.equals(currentUser.getIsSuperAdmin())) {
            if (roleRequest.getTenant() != null && roleRequest.getTenant().getId() != null) {
                Tenant tenant = tenantRepository.findById(roleRequest.getTenant().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant introuvable"));
                roleRequest.setTenant(tenant);
            }
        } else {
            roleRequest.setTenant(currentUser.getTenant());
        }

        if (roleRequest.getPermissions() != null) {
            Set<Permission> perms = new HashSet<>();
            for (Permission p : roleRequest.getPermissions()) {
                permissionRepository.findById(p.getId()).ifPresent(perms::add);
            }
            roleRequest.setPermissions(perms);
        }

        return ResponseEntity.ok(roleRepository.save(roleRequest));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('HR_EDIT')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rôle introuvable"));
        
        if (role.getTenant() != null) {
            assertOwnership(role.getTenant().getId());
        }
        
        roleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
