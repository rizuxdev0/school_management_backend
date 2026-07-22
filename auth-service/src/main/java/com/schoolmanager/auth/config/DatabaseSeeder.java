package com.schoolmanager.auth.config;

import com.schoolmanager.auth.entity.FeatureModule;
import com.schoolmanager.auth.entity.SubscriptionPlan;
import com.schoolmanager.auth.entity.User;
import com.schoolmanager.auth.repository.FeatureModuleRepository;
import com.schoolmanager.auth.repository.SubscriptionPlanRepository;
import com.schoolmanager.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Seed automatique des données lors du premier lancement du conteneur/application.
 * Crée le catalogue des modules SaaS, les abonnements par défaut, et le compte Super Admin.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FeatureModuleRepository featureModuleRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Vérification et seeding des données de base de la plateforme SaaS...");

        // 1. Initialisation des modules métiers
        if (featureModuleRepository.count() == 0) {
            log.info("Création du catalogue des modules SaaS...");
            List<FeatureModule> modules = List.of(
                    FeatureModule.builder().code("ACADEMIC").nameFr("Gestion Académique").nameEn("Academic").build(),
                    FeatureModule.builder().code("EVALUATION").nameFr("Évaluations & Bulletins").nameEn("Evaluations").build(),
                    FeatureModule.builder().code("ATTENDANCE").nameFr("Assiduité & Présences").nameEn("Attendance").build(),
                    FeatureModule.builder().code("FINANCE").nameFr("Gestion Financière").nameEn("Finance").build(),
                    FeatureModule.builder().code("EXAMS").nameFr("Examens").nameEn("Exams").build(),
                    FeatureModule.builder().code("LIBRARY").nameFr("Bibliothèque").nameEn("Library").build(),
                    FeatureModule.builder().code("HR").nameFr("Ressources Humaines").nameEn("HR").build(),
                    FeatureModule.builder().code("MEDICAL").nameFr("Santé & Infirmerie").nameEn("Medical").build(),
                    FeatureModule.builder().code("DISCIPLINE").nameFr("Vie Scolaire & Discipline").nameEn("Discipline").build()
            );
            featureModuleRepository.saveAll(modules);
        }

        // 2. Initialisation des plans d'abonnement
        if (subscriptionPlanRepository.count() == 0) {
            log.info("Création des abonnements (Packs) standards...");
            
            FeatureModule academic = featureModuleRepository.findByCode("ACADEMIC").orElseThrow();
            FeatureModule evaluation = featureModuleRepository.findByCode("EVALUATION").orElseThrow();
            FeatureModule attendance = featureModuleRepository.findByCode("ATTENDANCE").orElseThrow();
            FeatureModule finance = featureModuleRepository.findByCode("FINANCE").orElseThrow();

            // STARTER Pack
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("STARTER")
                    .nameFr("Pack Starter")
                    .nameEn("Starter Pack")
                    .priceMonthly(new BigDecimal("49.00"))
                    .priceYearly(new BigDecimal("490.00"))
                    .maxStudents(100)
                    .maxStaff(10)
                    .includedModules(Set.of(academic))
                    .build());

            // PRO Pack
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("PRO")
                    .nameFr("Pack Pro")
                    .nameEn("Pro Pack")
                    .priceMonthly(new BigDecimal("149.00"))
                    .priceYearly(new BigDecimal("1490.00"))
                    .maxStudents(500)
                    .maxStaff(50)
                    .includedModules(Set.of(academic, evaluation, attendance, finance))
                    .build());

            // ENTERPRISE Pack (Tous les modules)
            List<FeatureModule> allModules = featureModuleRepository.findAll();
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("ENTERPRISE")
                    .nameFr("Pack Enterprise")
                    .nameEn("Enterprise Pack")
                    .priceMonthly(new BigDecimal("299.00"))
                    .priceYearly(new BigDecimal("2990.00"))
                    .maxStudents(2000)
                    .maxStaff(200)
                    .includedModules(Set.copyOf(allModules))
                    .build());
        }

        // 3. Initialisation du Super Administrateur SaaS Global
        if (userRepository.findByUsername("superadmin").isEmpty()) {
            log.info("Création du compte Super Administrateur par défaut (superadmin / adminpassword)...");
            userRepository.save(User.builder()
                    .username("superadmin")
                    .email("superadmin@schoolmanager.saas")
                    .passwordHash(passwordEncoder.encode("adminpassword"))
                    .firstName("SaaS")
                    .lastName("SuperAdmin")
                    .isSuperAdmin(true)
                    .isActive(true)
                    .isAccountNonLocked(true)
                    .build());
        }

        log.info("Seeding de base terminé.");
    }
}
