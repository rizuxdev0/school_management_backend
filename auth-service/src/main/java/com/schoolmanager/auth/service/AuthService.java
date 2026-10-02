package com.schoolmanager.auth.service;

import com.schoolmanager.auth.dto.*;
import com.schoolmanager.auth.entity.*;
import com.schoolmanager.auth.repository.*;
import com.schoolmanager.auth.security.JwtUtils;
import com.schoolmanager.auth.security.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final FeatureModuleRepository featureModuleRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final PasswordValidator passwordValidator;
    private final GlobalSettingRepository globalSettingRepository;
    private final TenantDatabaseProvisioner tenantDatabaseProvisioner;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    /**
     * Authentifie l'utilisateur et retourne un JWT complet avec son Refresh Token.
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

        // Blocage de connexion si la plateforme est en maintenance globale
        if (!Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            UUID globalSettingsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            GlobalSetting setting = globalSettingRepository.findById(globalSettingsId).orElse(null);
            if (setting != null && setting.isMaintenanceMode()) {
                throw new RuntimeException("MAINTENANCE_MODE");
            }
        }

        return buildJwtResponse(user, true);
    }

    /**
     * Recharge toutes les données de l'utilisateur depuis la base de données
     * et réémet un nouveau JWT frais.
     */
    @Transactional
    public JwtResponse refreshCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!user.getIsActive() || !user.getIsAccountNonLocked()) {
            throw new RuntimeException("Compte désactivé ou verrouillé");
        }

        return buildJwtResponse(user, false);
    }

    /**
     * Valide un Refresh Token, applique la rotation de jeton et retourne un nouveau couple JWT / Refresh Token.
     */
    @Transactional
    public JwtResponse processRefreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Refresh Token introuvable ou invalide"));

        if (refreshToken.isRevoked()) {
            // Détection possible de vol de jeton : révocation de toutes les sessions de l'utilisateur
            refreshTokenRepository.revokeAllByUser(refreshToken.getUser());
            throw new RuntimeException("Refresh Token révoqué (Tentative de réutilisation suspecte)");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh Token expiré. Veuillez vous reconnecter.");
        }

        User user = refreshToken.getUser();
        if (!user.getIsActive() || !user.getIsAccountNonLocked()) {
            throw new RuntimeException("Compte utilisateur inactif ou verrouillé");
        }

        // Révocation de l'ancien jeton (Rotation stricte)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Génération d'une nouvelle session fraîche
        return buildJwtResponse(user, true);
    }

    /**
     * Révoque un Refresh Token lors d'une déconnexion explicite.
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        if (token != null && !token.isBlank()) {
            refreshTokenRepository.findByToken(token).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }
    }

    /**
     * Initialise la demande de réinitialisation de mot de passe (Mot de passe oublié).
     */
    @Transactional
    public Map<String, String> requestForgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOpt;
        String identifier = request.getIdentifier().trim();

        if (request.getTenantCode() != null && !request.getTenantCode().trim().isEmpty()) {
            String tenantCode = request.getTenantCode().trim();
            userOpt = userRepository.findByTenantCodeAndUsername(tenantCode, identifier);
            if (userOpt.isEmpty()) {
                // Recherche par email dans le tenant
                userOpt = userRepository.findAll().stream()
                        .filter(u -> u.getTenant() != null && tenantCode.equalsIgnoreCase(u.getTenant().getCode()))
                        .filter(u -> identifier.equalsIgnoreCase(u.getEmail()))
                        .findFirst();
            }
        } else {
            userOpt = userRepository.findByUsername(identifier);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(identifier);
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Si un compte correspond à ces informations, un lien de réinitialisation a été préparé.");

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Invalider les anciens tokens de réinitialisation
            passwordResetTokenRepository.invalidateAllByUser(user);

            String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiryDate(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(resetToken);
            log.info("Clé de réinitialisation générée pour l'utilisateur '{}' : {}", user.getUsername(), token);

            // Pour faciliter les tests et l'usage sans serveur SMTP externe configuré, on retourne le token
            response.put("resetToken", token);
        }

        return response;
    }

    /**
     * Valide le jeton et applique le nouveau mot de passe avec vérification des règles de complexité.
     */
    @Transactional
    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Jeton de réinitialisation invalide ou introuvable"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("Ce jeton de réinitialisation a déjà été utilisé");
        }

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new RuntimeException("Ce jeton de réinitialisation a expiré (durée de validité : 30 minutes)");
        }

        if (!passwordValidator.isValid(request.getNewPassword(), null)) {
            throw new RuntimeException("Le nouveau mot de passe ne respecte pas les critères de sécurité exigés (8 caractères min, majuscule, minuscule, chiffre, symbole).");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Sécurité : Révocation de toutes les sessions actives Refresh Tokens
        refreshTokenRepository.revokeAllByUser(user);

        Map<String, String> result = new HashMap<>();
        result.put("message", "Votre mot de passe a été mis à jour avec succès. Veuillez vous connecter.");
        return result;
    }

    /**
     * Crée et persiste un nouveau Refresh Token pour un utilisateur donné.
     */
    private String createAndSaveRefreshToken(User user) {
        String tokenString = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenString)
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenString;
    }

    /**
     * Construit le JwtResponse complet à partir d'un utilisateur chargé depuis la DB.
     */
    private JwtResponse buildJwtResponse(User user, boolean generateRefreshToken) {
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
                tenant != null ? tenant.getDatabaseName() : null,
                tenant != null ? tenant.getPlanCode() : "SYSTEM",
                enabledModules,
                roles,
                permissions,
                Boolean.TRUE.equals(user.getIsSuperAdmin()),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getId());

        String refreshToken = generateRefreshToken ? createAndSaveRefreshToken(user) : null;

        Integer maxStudents = 999999;
        Integer maxStaff = 999999;
        Integer maxClassrooms = 999999;
        Integer maxBooks = 999999;

        if (tenant != null) {
            Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findByCode(tenant.getPlanCode());
            if (planOpt.isPresent()) {
                SubscriptionPlan plan = planOpt.get();
                maxStudents = Math.max(tenant.getMaxStudents() != null ? tenant.getMaxStudents() : 0, plan.getMaxStudents() != null ? plan.getMaxStudents() : 0);
                maxStaff = Math.max(tenant.getMaxStaff() != null ? tenant.getMaxStaff() : 0, plan.getMaxStaff() != null ? plan.getMaxStaff() : 0);
                maxClassrooms = Math.max(tenant.getMaxClassrooms() != null ? tenant.getMaxClassrooms() : 0, plan.getMaxClassrooms() != null ? plan.getMaxClassrooms() : 0);
                maxBooks = Math.max(tenant.getMaxBooks() != null ? tenant.getMaxBooks() : 0, plan.getMaxBooks() != null ? plan.getMaxBooks() : 0);
            } else {
                maxStudents = tenant.getMaxStudents() != null ? tenant.getMaxStudents() : 999999;
                maxStaff = tenant.getMaxStaff() != null ? tenant.getMaxStaff() : 999999;
                maxClassrooms = tenant.getMaxClassrooms() != null ? tenant.getMaxClassrooms() : 999999;
                maxBooks = tenant.getMaxBooks() != null ? tenant.getMaxBooks() : 999999;
            }
        }

        return JwtResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantCode(tenant != null ? tenant.getCode() : "SUPERADMIN")
                .databaseName(tenant != null ? tenant.getDatabaseName() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .isSuperAdmin(user.getIsSuperAdmin())
                .roles(roles)
                .permissions(permissions)
                .enabledModules(enabledModules)
                .planCode(tenant != null ? tenant.getPlanCode() : "SYSTEM")
                .maxStudents(maxStudents)
                .maxStaff(maxStaff)
                .maxClassrooms(maxClassrooms)
                .maxBooks(maxBooks)
                .logoUrl(tenant != null ? tenant.getLogoUrl() : null)
                .primaryColor(tenant != null ? tenant.getPrimaryColor() : null)
                .institutionType(tenant != null ? tenant.getInstitutionType() : null)
                .systemPreset(tenant != null ? tenant.getSystemPreset() : null)
                .currencyCode(tenant != null ? tenant.getCurrencyCode() : null)
                .currencySymbol(tenant != null ? tenant.getCurrencySymbol() : null)
                .currencyNameFr(tenant != null ? tenant.getCurrencyNameFr() : null)
                .defaultLanguage(tenant != null ? tenant.getDefaultLanguage() : null)
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

        String rawCode = dto.getTenantCode().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String databaseName = "school_tenant_" + rawCode;

        Tenant tenant = Tenant.builder()
                .code(dto.getTenantCode())
                .name(dto.getTenantName())
                .domainName(dto.getDomainName())
                .databaseName(databaseName)
                .isActive(true)
                .enabledModules(modules)
                .planCode(plan.getCode())
                .maxStudents(plan.getMaxStudents())
                .maxStaff(plan.getMaxStaff())
                .maxClassrooms(plan.getMaxClassrooms())
                .maxBooks(plan.getMaxBooks())
                .subscriptionExpiresAt(java.time.ZonedDateTime.now().plusYears(1))
                .institutionType(dto.getInstitutionType() != null ? dto.getInstitutionType() : "PRIVATE")
                .systemPreset(dto.getSystemPreset() != null ? dto.getSystemPreset() : "FRENCH")
                .currencyCode(dto.getCurrencyCode() != null ? dto.getCurrencyCode() : "XOF")
                .currencySymbol(dto.getCurrencySymbol() != null ? dto.getCurrencySymbol() : "FCFA")
                .currencyNameFr(dto.getCurrencyNameFr() != null ? dto.getCurrencyNameFr() : "Franc CFA")
                .defaultLanguage(dto.getDefaultLanguage() != null ? dto.getDefaultLanguage() : "fr")
                .build();

        final Tenant savedTenant = tenantRepository.save(tenant);

        // Auto-provisioning de la base PostgreSQL dédiée
        tenantDatabaseProvisioner.createDatabase(databaseName);
        tenantDatabaseProvisioner.notifySystemConfigService(savedTenant);

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
        return savedTenant;
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
