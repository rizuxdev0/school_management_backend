package com.schoolmanager.auth.config;

import com.schoolmanager.auth.entity.FeatureModule;
import com.schoolmanager.auth.entity.Permission;
import com.schoolmanager.auth.entity.Role;
import com.schoolmanager.auth.entity.SubscriptionPlan;
import com.schoolmanager.auth.entity.User;
import com.schoolmanager.auth.entity.GlobalSetting;
import com.schoolmanager.auth.repository.FeatureModuleRepository;
import com.schoolmanager.auth.repository.PermissionRepository;
import com.schoolmanager.auth.repository.RoleRepository;
import com.schoolmanager.auth.repository.SubscriptionPlanRepository;
import com.schoolmanager.auth.repository.UserRepository;
import com.schoolmanager.auth.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seed automatique des données lors du premier lancement du conteneur/application.
 * Crée le catalogue des modules SaaS, les abonnements par défaut, le compte Super Admin,
 * les rôles et permissions par défaut.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FeatureModuleRepository featureModuleRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.schoolmanager.auth.repository.TenantRepository tenantRepository;
    private final com.schoolmanager.auth.repository.SubscriptionInvoiceRepository subscriptionInvoiceRepository;
    private final GlobalSettingRepository globalSettingRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Vérification et seeding des données de base de la plateforme SaaS...");

        // 1. Initialisation des modules métiers
        // 1. Initialisation des modules métiers
        log.info("Vérification du catalogue des modules SaaS...");
        List<FeatureModule> modulesToSeed = List.of(
                FeatureModule.builder().code("ACADEMIC").nameFr("Gestion Académique").nameEn("Academic").build(),
                FeatureModule.builder().code("EVALUATION").nameFr("Évaluations & Bulletins").nameEn("Evaluations").build(),
                FeatureModule.builder().code("ATTENDANCE").nameFr("Assiduité & Présences").nameEn("Attendance").build(),
                FeatureModule.builder().code("FINANCE").nameFr("Gestion Financière").nameEn("Finance").build(),
                FeatureModule.builder().code("EXAMS").nameFr("Examens").nameEn("Exams").build(),
                FeatureModule.builder().code("LIBRARY").nameFr("Bibliothèque").nameEn("Library").build(),
                FeatureModule.builder().code("HR").nameFr("Ressources Humaines").nameEn("HR").build(),
                FeatureModule.builder().code("MEDICAL").nameFr("Santé & Infirmerie").nameEn("Medical").build(),
                FeatureModule.builder().code("DISCIPLINE").nameFr("Vie Scolaire & Discipline").nameEn("Discipline").build(),
                FeatureModule.builder().code("TRANSPORT").nameFr("Transport Scolaire").nameEn("School Transport").build(),
                FeatureModule.builder().code("CATERING").nameFr("Cantine & Restauration").nameEn("Catering & Canteen").build(),
                FeatureModule.builder().code("MESSAGING").nameFr("Messagerie & Communications").nameEn("Internal Messaging").build(),
                FeatureModule.builder().code("EXTRACURRICULAR").nameFr("Activités Périscolaires & Clubs").nameEn("Extracurricular & Clubs").build()
        );
        for (FeatureModule fm : modulesToSeed) {
            if (!featureModuleRepository.findByCode(fm.getCode()).isPresent()) {
                featureModuleRepository.save(fm);
            }
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
                    .maxClassrooms(5)
                    .maxBooks(100)
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
                    .maxClassrooms(25)
                    .maxBooks(1000)
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
                    .maxClassrooms(100)
                    .maxBooks(10000)
                    .includedModules(Set.copyOf(allModules))
                    .build());

            // UNLIMITED Pack (Illimité, Tous les modules)
            subscriptionPlanRepository.save(SubscriptionPlan.builder()
                    .code("UNLIMITED")
                    .nameFr("Pack Illimité")
                    .nameEn("Unlimited Pack")
                    .priceMonthly(new BigDecimal("999.00"))
                    .priceYearly(new BigDecimal("9990.00"))
                    .maxStudents(999999)
                    .maxStaff(999999)
                    .maxClassrooms(999999)
                    .maxBooks(999999)
                    .includedModules(Set.copyOf(allModules))
                    .build());
        }

        // S'assurer que les abonnements ENTERPRISE et UNLIMITED contiennent tous les nouveaux modules
        List<FeatureModule> currentModules = featureModuleRepository.findAll();
        subscriptionPlanRepository.findByCode("ENTERPRISE").ifPresent(plan -> {
            plan.setIncludedModules(new HashSet<>(currentModules));
            subscriptionPlanRepository.save(plan);
        });
        subscriptionPlanRepository.findByCode("UNLIMITED").ifPresent(plan -> {
            plan.setIncludedModules(new HashSet<>(currentModules));
            subscriptionPlanRepository.save(plan);
        });

        // 3. Initialisation des Permissions système
        log.info("Vérification des permissions système...");
        List<Permission> permissionsToSeed = List.of(
                Permission.builder().code("ACADEMIC_VIEW").module("ACADEMIC").descriptionFr("Voir la planification académique").descriptionEn("View academic planning").build(),
                Permission.builder().code("ACADEMIC_EDIT").module("ACADEMIC").descriptionFr("Créer/Modifier les structures académiques").descriptionEn("Edit academic structures").build(),
                Permission.builder().code("EVALUATION_VIEW").module("EVALUATION").descriptionFr("Voir les notes et bulletins").descriptionEn("View grades and reports").build(),
                Permission.builder().code("EVALUATION_EDIT").module("EVALUATION").descriptionFr("Saisir les notes et programmer les devoirs").descriptionEn("Edit grades and evaluations").build(),
                Permission.builder().code("ATTENDANCE_VIEW").module("ATTENDANCE").descriptionFr("Voir le registre de présence").descriptionEn("View attendance registry").build(),
                Permission.builder().code("ATTENDANCE_EDIT").module("ATTENDANCE").descriptionFr("Faire l'appel et justifier les absences").descriptionEn("Take attendance and excuse absences").build(),
                Permission.builder().code("FINANCE_VIEW").module("FINANCE").descriptionFr("Voir la comptabilité et les soldes").descriptionEn("View accounting and student balances").build(),
                Permission.builder().code("FINANCE_EDIT").module("FINANCE").descriptionFr("Encaisser les versements et configurer les tarifs").descriptionEn("Record payments and configure tuition fees").build(),
                Permission.builder().code("HR_VIEW").module("HR").descriptionFr("Voir le registre du personnel et enseignants").descriptionEn("View staff and teachers directory").build(),
                Permission.builder().code("HR_EDIT").module("HR").descriptionFr("Recruter des professeurs et créer des comptes").descriptionEn("Recruit teachers and manage accounts").build(),
                Permission.builder().code("MEDICAL_VIEW").module("MEDICAL").descriptionFr("Voir les fiches de soins de l'infirmerie").descriptionEn("View medical visit logs").build(),
                Permission.builder().code("MEDICAL_EDIT").module("MEDICAL").descriptionFr("Enregistrer des consultations de santé").descriptionEn("Record health consultations").build(),
                Permission.builder().code("DISCIPLINE_VIEW").module("DISCIPLINE").descriptionFr("Voir le registre de discipline").descriptionEn("View discipline logs").build(),
                Permission.builder().code("DISCIPLINE_EDIT").module("DISCIPLINE").descriptionFr("Signaler des incidents et infliger des sanctions").descriptionEn("Report incidents and assign sanctions").build(),
                Permission.builder().code("EXAMS_VIEW").module("EXAMS").descriptionFr("Voir les sessions d'examens et convocations").descriptionEn("View exam sessions and convocations").build(),
                Permission.builder().code("EXAMS_EDIT").module("EXAMS").descriptionFr("Organiser les examens et convoquer les élèves").descriptionEn("Organize exams and summon students").build(),
                Permission.builder().code("LIBRARY_VIEW").module("LIBRARY").descriptionFr("Consulter le catalogue de la bibliothèque").descriptionEn("Consult library catalog").build(),
                Permission.builder().code("LIBRARY_EDIT").module("LIBRARY").descriptionFr("Gérer les livres et enregistrer les emprunts").descriptionEn("Manage books and record loans").build(),
                Permission.builder().code("TRANSPORT_VIEW").module("TRANSPORT").descriptionFr("Voir la planification du transport").descriptionEn("View school transport").build(),
                Permission.builder().code("TRANSPORT_EDIT").module("TRANSPORT").descriptionFr("Gérer la flotte de bus et abonnements").descriptionEn("Manage transport fleet and subscriptions").build(),
                Permission.builder().code("CATERING_VIEW").module("CATERING").descriptionFr("Voir les menus et réservations cantine").descriptionEn("View catering menus and reservations").build(),
                Permission.builder().code("CATERING_EDIT").module("CATERING").descriptionFr("Gérer les menus cantine et les forfaits").descriptionEn("Manage catering menus and subscriptions").build(),
                Permission.builder().code("MESSAGING_VIEW").module("MESSAGING").descriptionFr("Voir la messagerie interne et les annonces").descriptionEn("View internal messaging and announcements").build(),
                Permission.builder().code("MESSAGING_EDIT").module("MESSAGING").descriptionFr("Publier des annonces et échanger").descriptionEn("Publish announcements and send messages").build(),
                Permission.builder().code("EXTRACURRICULAR_VIEW").module("EXTRACURRICULAR").descriptionFr("Voir les activités périscolaires").descriptionEn("View extracurricular activities").build(),
                Permission.builder().code("EXTRACURRICULAR_EDIT").module("EXTRACURRICULAR").descriptionFr("Gérer les inscriptions aux clubs et activités").descriptionEn("Manage extracurricular clubs and activities").build()
        );
        for (Permission perm : permissionsToSeed) {
            if (permissionRepository.findByCode(perm.getCode()).isEmpty()) {
                permissionRepository.save(perm);
            }
        }

        // 4. Initialisation des Rôles système par défaut
        List<Permission> allPerms = permissionRepository.findAll();
        log.info("Vérification et mise à jour des rôles système...");

        // SCHOOL_ADMIN (Tous les accès)
        Role adminRole = roleRepository.findByCode("SCHOOL_ADMIN").orElse(null);
        if (adminRole == null) {
            adminRole = roleRepository.save(Role.builder()
                    .code("SCHOOL_ADMIN")
                    .nameFr("Administrateur")
                    .nameEn("Administrator")
                    .isSystemRole(true)
                    .permissions(new HashSet<>(allPerms))
                    .build());
        } else {
            adminRole.setPermissions(new HashSet<>(allPerms));
            roleRepository.save(adminRole);
        }

        // TEACHER (Accès aux notes et présences)
        if (roleRepository.findByCode("TEACHER").isEmpty()) {
            List<Permission> teacherPerms = allPerms.stream()
                    .filter(p -> p.getCode().startsWith("EVALUATION") || p.getCode().startsWith("ATTENDANCE") || p.getCode().startsWith("ACADEMIC_VIEW"))
                    .toList();
            roleRepository.save(Role.builder()
                    .code("TEACHER")
                    .nameFr("Enseignant / Professeur")
                    .nameEn("Teacher")
                    .isSystemRole(true)
                    .permissions(new HashSet<>(teacherPerms))
                    .build());
        }

        // STUDENT (Accès restreint à ses propres informations)
        if (roleRepository.findByCode("STUDENT").isEmpty()) {
            List<Permission> studentPerms = allPerms.stream()
                    .filter(p -> p.getCode().equals("ACADEMIC_VIEW") || p.getCode().equals("EVALUATION_VIEW") 
                            || p.getCode().equals("ATTENDANCE_VIEW") || p.getCode().equals("EXAMS_VIEW") 
                            || p.getCode().equals("LIBRARY_VIEW"))
                    .toList();
            roleRepository.save(Role.builder()
                    .code("STUDENT")
                    .nameFr("Élève / Étudiant")
                    .nameEn("Student")
                    .isSystemRole(true)
                    .permissions(new HashSet<>(studentPerms))
                    .build());
        }

        // PARENT (Accès suivi de son enfant)
        if (roleRepository.findByCode("PARENT").isEmpty()) {
            List<Permission> parentPerms = allPerms.stream()
                    .filter(p -> p.getCode().equals("ACADEMIC_VIEW") || p.getCode().equals("EVALUATION_VIEW") 
                            || p.getCode().equals("ATTENDANCE_VIEW") || p.getCode().equals("EXAMS_VIEW"))
                    .toList();
            roleRepository.save(Role.builder()
                    .code("PARENT")
                    .nameFr("Parent d'Élève")
                    .nameEn("Parent")
                    .isSystemRole(true)
                    .permissions(new HashSet<>(parentPerms))
                    .build());
        }

        // LIBRARIAN (Gestionnaire bibliothèque)
        if (roleRepository.findByCode("LIBRARIAN").isEmpty()) {
            List<Permission> librarianPerms = allPerms.stream()
                    .filter(p -> p.getCode().startsWith("LIBRARY"))
                    .toList();
            roleRepository.save(Role.builder()
                    .code("LIBRARIAN")
                    .nameFr("Bibliothécaire")
                    .nameEn("Librarian")
                    .isSystemRole(true)
                    .permissions(new HashSet<>(librarianPerms))
                    .build());
        }

        // ACCOUNTANT (Comptable / Trésorier)
        if (roleRepository.findByCode("ACCOUNTANT").isEmpty()) {
            List<Permission> accountantPerms = allPerms.stream()
                    .filter(p -> p.getCode().startsWith("FINANCE"))
                    .toList();
            roleRepository.save(Role.builder()
                    .code("ACCOUNTANT")
                    .nameFr("Comptable / Trésorier")
                    .nameEn("Accountant")
                    .isSystemRole(true)
                    .permissions(new HashSet<>(accountantPerms))
                    .build());
        }

        // 5. Initialisation du Super Administrateur SaaS Global
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

        // 6. Initialisation de factures d'abonnements démo pour les tenants existants
        if (subscriptionInvoiceRepository.count() == 0) {
            log.info("Création de factures d'abonnements démo pour les écoles...");
            tenantRepository.findAll().forEach(t -> {
                subscriptionInvoiceRepository.save(com.schoolmanager.auth.entity.SubscriptionInvoice.builder()
                        .tenant(t)
                        .invoiceNumber("INV-" + t.getCode() + "-2026-0001")
                        .amount(new BigDecimal("149.00"))
                        .invoiceDate(java.time.ZonedDateTime.now().minusMonths(1))
                        .dueDate(java.time.ZonedDateTime.now().minusMonths(1).plusDays(15))
                        .paymentStatus("PAID")
                        .planCode(t.getPlanCode() != null ? t.getPlanCode() : "PRO")
                        .paymentMethod("TRANSFER")
                        .build());
            });
        }

        // Mise à jour automatique des modules activés pour les tenants existants selon leur plan
        log.info("Mise à jour des modules activés pour tous les tenants existants...");
        tenantRepository.findAll().forEach(t -> {
            String planCode = t.getPlanCode() != null ? t.getPlanCode() : "UNLIMITED";
            subscriptionPlanRepository.findByCode(planCode).ifPresent(plan -> {
                t.setEnabledModules(new HashSet<>(plan.getIncludedModules()));
                tenantRepository.save(t);
                log.info("Modules mis à jour pour le tenant {}: {}", t.getName(), planCode);
            });
        });

        // 7. Initialisation de la configuration globale
        UUID globalSettingsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (globalSettingRepository.findById(globalSettingsId).isEmpty()) {
            log.info("Création de la configuration globale par défaut...");
            globalSettingRepository.save(GlobalSetting.builder()
                    .id(globalSettingsId)
                    .maintenanceMode(false)
                    .announcementText(null)
                    .announcementEnd(null)
                    .passwordMinLength(8)
                    .passwordRequireUppercase(true)
                    .passwordRequireLowercase(true)
                    .passwordRequireNumber(true)
                    .passwordRequireSpecial(true)
                    .build());
        }

        log.info("Seeding de base terminé.");
    }
}
