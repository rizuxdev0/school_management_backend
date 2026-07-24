package com.schoolmanager.auth.security;

import java.util.regex.Pattern;

/**
 * Validateur de force de mot de passe pour les exigences de sécurité SaaS.
 * Exige : minimum 8 caractères, 1 lettre majuscule, 1 lettre minuscule, 1 chiffre et 1 caractère spécial.
 */
public class PasswordValidator {

    private static final String PASSWORD_PATTERN = 
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!\\-_*()\\\\\\[\\]{}|;:',.<>?/`~])(?=\\S+$).{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    /**
     * Valide si le mot de passe respecte la politique de sécurité.
     */
    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        return pattern.matcher(password).matches();
    }
}
