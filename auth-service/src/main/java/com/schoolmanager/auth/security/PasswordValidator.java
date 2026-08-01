package com.schoolmanager.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.schoolmanager.auth.repository.GlobalSettingRepository;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validateur de force de mot de passe dynamique et paramétrable par établissement (SaaS).
 * Récupère les exigences du tenant depuis le microservice system-config-service.
 */
@Component
@Slf4j
public class PasswordValidator {

    @Value("${app.services.system-config-url}")
    private String systemConfigUrl;

    @Autowired
    private GlobalSettingRepository globalSettingRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    public record PasswordPolicyDto(
            Integer passwordMinLength,
            Boolean passwordRequireUppercase,
            Boolean passwordRequireLowercase,
            Boolean passwordRequireNumber,
            Boolean passwordRequireSpecial
    ) {}

    /**
     * Valide si le mot de passe respecte la politique de sécurité du tenant.
     * Si tenantId est null ou si le service est injoignable, applique les règles par défaut (stricte).
     */
    public boolean isValid(String password, UUID tenantId) {
        if (password == null) {
            return false;
        }

        PasswordPolicyDto policy = getPolicy(tenantId);

        // Construction dynamique de la Regex selon la politique du tenant
        int minLength = policy.passwordMinLength() != null ? policy.passwordMinLength() : 8;
        boolean reqUpper = policy.passwordRequireUppercase() != null ? policy.passwordRequireUppercase() : true;
        boolean reqLower = policy.passwordRequireLowercase() != null ? policy.passwordRequireLowercase() : true;
        boolean reqNumber = policy.passwordRequireNumber() != null ? policy.passwordRequireNumber() : true;
        boolean reqSpecial = policy.passwordRequireSpecial() != null ? policy.passwordRequireSpecial() : true;

        // Si aucun critère de complexité n'est requis, laisser l'utilisateur écrire ce qu'il veut
        if (!reqUpper && !reqLower && !reqNumber && !reqSpecial) {
            return minLength <= 0 || password.length() >= minLength;
        }

        StringBuilder regex = new StringBuilder("^");

        if (reqNumber) {
            regex.append("(?=.*[0-9])");
        }
        if (reqLower) {
            regex.append("(?=.*[a-z])");
        }
        if (reqUpper) {
            regex.append("(?=.*[A-Z])");
        }
        if (reqSpecial) {
            regex.append("(?=.*[@#$%^&+=!\\-_*()\\\\\\[\\]{}|;:',.<>?/`~])");
        }
        
        regex.append("(?=\\S+$).{").append(minLength).append(",}$");

        try {
            return Pattern.compile(regex.toString()).matcher(password).matches();
        } catch (Exception e) {
            log.error("Erreur lors de la validation regex du mot de passe : {}", e.getMessage());
            return password.length() >= minLength; // Fallback simple de sécurité
        }
    }

    private PasswordPolicyDto getPolicy(UUID tenantId) {
        UUID globalSettingsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (tenantId == null) {
            return getGlobalPolicy(globalSettingsId);
        }
        try {
            String url = systemConfigUrl + "/api/v1/system/settings/tenant/" + tenantId + "/password-policy";
            return restTemplate.getForObject(url, PasswordPolicyDto.class);
        } catch (Exception e) {
            log.warn("Impossible de récupérer la politique de mot de passe pour le tenant {} : {}. Fallback sur la politique globale.", tenantId, e.getMessage());
            return getGlobalPolicy(globalSettingsId);
        }
    }

    private PasswordPolicyDto getGlobalPolicy(UUID id) {
        try {
            return globalSettingRepository.findById(id)
                    .map(g -> new PasswordPolicyDto(
                            g.getPasswordMinLength(),
                            g.isPasswordRequireUppercase(),
                            g.isPasswordRequireLowercase(),
                            g.isPasswordRequireNumber(),
                            g.isPasswordRequireSpecial()
                    ))
                    .orElse(new PasswordPolicyDto(8, true, true, true, true));
        } catch (Exception e) {
            log.error("Erreur lors de la lecture de la politique globale en BDD : {}", e.getMessage());
            return new PasswordPolicyDto(8, true, true, true, true);
        }
    }
}
