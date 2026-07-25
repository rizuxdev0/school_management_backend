package com.schoolmanager.auth.service;

import com.schoolmanager.auth.dto.AuthRequest;
import com.schoolmanager.auth.dto.JwtResponse;
import com.schoolmanager.auth.dto.TenantRegistrationDto;
import com.schoolmanager.auth.entity.*;
import com.schoolmanager.auth.repository.*;
import com.schoolmanager.auth.security.JwtUtils;
import com.schoolmanager.auth.security.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final FeatureModuleRepository featureModuleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final PasswordValidator passwordValidator;

    /**
     * Authentifie l'utilisateur et retourne un JWT complet avec tous ses droits.
     */
    @Transactional
    public JwtResponse login(AuthRequest request) {
        String username = request.getUsername();
        User user;

        if ("superadmin".equalsIgnoreCase(username != null ? username.trim() : "")) {
            // Le Super Admin global système n'appartient à aucun tenant
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        } else {
            // Pour tous les autres utilisateurs, le code établissement est obligatoire
            if (request.getTenantCode() == null || request.getTenantCode().trim().isEmpty()) {
                throw new RuntimeException("Le code établissement est obligatoire");
            }

            String cleanCode = request.getTenantCode().trim();
            Tenant tenant = tenantRepository.findByCode(cleanCode)
                    .orElseThrow(() -> new RuntimeException("Code établissement incorrect ou introuvable"));

            user = userRepository.findByTenantCodeAndUsername(tenant.getCode(), username)
                    .orElseThrow(() -> new RuntimeException("Utilisateur introuvable pour cet établissement"));
        }

        if (!user.getIsActive() || !user.getIsAccountNonLocked()) {
            throw new RuntimeException("Compte désactivé ou verrouillé");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Mot de passe incorrect");
        }

        return buildJwtResponse(user);
    }

    /**
     * Recharge toutes les données de l'utilisateur depuis la base de données
     * (tenant, modules activés, quotas du plan, rôles, permissions) et réémet
     * un nouveau JWT frais. Utilisé pour éviter la déconnexion après un changement
     * de plan ou de droits par le Super Admin.
     *
     * @param username le nom d'utilisateur extrait du JWT courant (via SecurityContext)
     * @return un nouveau JwtResponse avec les données à jour
     */
    @Transactional
    public JwtResponse refreshCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!user.getIsActive() || !user.getIsAccountNonLocked()) {
            throw new RuntimeException("Compte désactivé ou verrouillé");
        }

        return buildJwtResponse(user);
    }

    /**
     * Construit le JwtResponse complet à partir d'un utilisateur chargé depuis la DB.
     * Mutualisé entre login() et refreshCurrentUser() pour éviter la duplication (DRY).
     */
    private JwtResponse buildJwtResponse(User user) {
        Tenant tenant = user.getTenant();
        List<String> enabledModules = new ArrayList<>();

        if (tenant != null) {
            if (!tenant.getIsActive()) {
                throw new RuntimeException(
                        "L'établissement est suspendu ou désactivé. Veuillez contacter l'administrateur SVP !");
            }
            if (tenant.getSubscriptionExpiresAt() != null &&
                tenant.getSubscriptionExpiresAt().isBefore(java.time.ZonedDateTime.now())) {
                throw new RuntimeException(
                        "Votre abonnement SaaS a expiré le " +
                        tenant.getSubscriptionExpiresAt().format(
                                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                        ". Veuillez contacter le Super Admin pour renouveler votre licence SVP !");
            }
            enabledModules = tenant.getEnabledModules().stream()
                    .map(FeatureModule::getCode)
                    .toList();
        } else if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            // Le Super Admin a accès à TOUS les modules du catalogue
            enabledModules = featureModuleRepository.findAll().stream()
                    .map(FeatureModule::getCode)
                    .toList();
        }

        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.emptyList());
        String token = jwtUtils.generateJwtToken(
                auth,
                tenant != null ? tenant.getId() : null,
                tenant != null ? tenant.getCode() : "SUPERADMIN",
                tenant != null ? tenant.getPlanCode() : "SYSTEM",
                enabledModules,
                roles,
                permissions,
                Boolean.TRUE.equals(user.getIsSuperAdmin()));

        return JwtResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantCode(tenant != null ? tenant.getCode() : "SUPERADMIN")
                .tenantName(tenant != null ? tenant.getName() : null)
                .isSuperAdmin(user.getIsSuperAdmin())
                .roles(roles)
                .permissions(permissions)
                .enabledModules(enabledModules)
                .planCode(tenant != null ? tenant.getPlanCode() : "SYSTEM")
                .maxStudents(tenant != null && tenant.getMaxStudents() != null ? tenant.getMaxStudents() : 999999)
                .maxStaff(tenant != null && tenant.getMaxStaff() != null ? tenant.getMaxStaff() : 999999)
                .maxClassrooms(tenant != null && tenant.getMaxClassrooms() != null ? tenant.getMaxClassrooms() : 999999)
                .maxBooks(tenant != null && tenant.getMaxBooks() != null ? tenant.getMaxBooks() : 999999)
                .logoUrl(tenant != null ? tenant.getLogoUrl() : null)
                .primaryColor(tenant != null ? tenant.getPrimaryColor() : null)
                .build();
    }

    @Transactional
    public Tenant registerTenant(TenantRegistrationDto dto) {
        if (tenantRepository.existsByCode(dto.getTenantCode())) {
            throw new RuntimeException("Ce code d'établissement existe déjà");
        }

        if (!passwordValidator.isValid(dto.getAdminPassword(), null)) {
            throw new RuntimeException("Le mot de passe administrateur ne respecte pas les critères de complexité exigés.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(dto.getPlanCode())
                .orElseThrow(() -> new RuntimeException("Plan d'abonnement introuvable : " + dto.getPlanCode()));

        Set<FeatureModule> modules = new HashSet<>(plan.getIncludedModules());
        if ("CUSTOM".equalsIgnoreCase(dto.getPlanCode()) && dto.getCustomModuleCodes() != null) {
            for (String modCode : dto.getCustomModuleCodes()) {
                featureModuleRepository.findByCode(modCode).ifPresent(modules::add);
            }
        }

        Tenant tenant = Tenant.builder()
                .code(dto.getTenantCode())
                .name(dto.getTenantName())
                .domainName(dto.getDomainName())
                .isActive(true)
                .enabledModules(modules)
                .planCode(plan.getCode())
                .maxStudents(plan.getMaxStudents())
                .maxStaff(plan.getMaxStaff())
                .maxClassrooms(plan.getMaxClassrooms())
                .maxBooks(plan.getMaxBooks())
                .subscriptionExpiresAt(java.time.ZonedDateTime.now().plusYears(1))
                .build();

        final Tenant savedTenant = tenantRepository.save(tenant);

        // Création du rôle Admin Établissement (récupère le rôle système existant si présent)
        Role adminRole = roleRepository.findByCode("SCHOOL_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .tenant(savedTenant)
                        .code("SCHOOL_ADMIN")
                        .nameFr("Administrateur Établissement")
                        .nameEn("School Administrator")
                        .isSystemRole(true)
                        .build()));

        User adminUser = User.builder()
                .tenant(savedTenant)
                .username(dto.getAdminUsername())
                .email(dto.getAdminEmail())
                .passwordHash(passwordEncoder.encode(dto.getAdminPassword()))
                .firstName(dto.getAdminFirstName())
                .lastName(dto.getAdminLastName())
                .isSuperAdmin(false)
                .isActive(true)
                .isAccountNonLocked(true)
                .roles(Set.of(adminRole))
                .build();

        userRepository.save(adminUser);
        return tenant;
    }

    @Transactional
    public Tenant updateTenantBranding(java.util.UUID tenantId, String logoUrl, String primaryColor) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Établissement introuvable"));
        tenant.setLogoUrl(logoUrl);
        tenant.setPrimaryColor(primaryColor);
        return tenantRepository.save(tenant);
    }

    public Optional<Map<String, Object>> getTenantStatus(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(tenant -> {
            Map<String, Object> status = new HashMap<>();
            status.put("isActive", tenant.getIsActive());
            status.put("planCode", tenant.getPlanCode());
            return status;
        });
    }
}
