package com.schoolmanager.config.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Swagger/OpenAPI pour le microservice de Paramétrage Système, Académique et Métier.
 * Configure le schéma de sécurité Bearer JWT de façon globale.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI systemConfigOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SchoolManager - System & Academics API")
                        .description("Documentation des API de scolarité, emplois du temps, évaluations, absences, finances, infirmerie, discipline, bibliothèque et examens.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Deepmind Pair Programming")
                                .email("support@schoolmanager.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer")
                .description("Saisissez votre token JWT pour vous authentifier aux endpoints sécurisés.");
    }
}
