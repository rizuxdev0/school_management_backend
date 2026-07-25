package com.schoolmanager.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
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
            return password.length() >= 8; // Fallback simple de sécurité
        }
    }

    private PasswordPolicyDto getPolicy(UUID tenantId) {
        if (tenantId == null) {
            return new PasswordPolicyDto(8, true, true, true, true);
        }
        try {
            String url = systemConfigUrl + "/api/v1/system/settings/tenant/" + tenantId + "/password-policy";
            return restTemplate.getForObject(url, PasswordPolicyDto.class);
        } catch (Exception e) {
            log.warn("Impossible de récupérer la politique de mot de passe pour le tenant {} : {}. Fallback sur la politique par défaut.", tenantId, e.getMessage());
            return new PasswordPolicyDto(8, true, true, true, true);
        }
    }
}
