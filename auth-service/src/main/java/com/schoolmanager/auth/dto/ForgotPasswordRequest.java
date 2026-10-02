package com.schoolmanager.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String tenantCode;

    @NotBlank(message = "Le nom d'utilisateur ou l'email est obligatoire")
    private String identifier;
}
