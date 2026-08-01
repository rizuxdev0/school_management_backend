package com.schoolmanager.config.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final String username;
    private final String tenantId;
    private final Boolean isSuperAdmin;
    private final String planCode;
    private final String email;
    private final String phoneNumber;
    private final java.util.UUID userId;
}
