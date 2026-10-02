package com.schoolmanager.config.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Gestionnaire d'exécution DDL et d'initialisation de schéma pour chaque base de données isolée de souscripteur.
 */
@Slf4j
@Service
public class TenantDatabaseSchemaManager {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/school_system_db}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username:${DB_USER:postgres}}")
    private String dbUser;

    @Value("${spring.datasource.password:${DB_PASSWORD:admin}}")
    private String dbPassword;

    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:5432}")
    private String dbPort;

    /**
     * Crée la base de données PostgreSQL isolée si elle n'existe pas encore.
     */
    public boolean createDatabaseIfNotExists(String databaseName) {
        if (databaseName == null || databaseName.isBlank() || TenantContext.DEFAULT_TENANT.equals(databaseName)) {
            return false;
        }

        String cleanDb = databaseName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String masterUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/postgres";

        try (Connection conn = DriverManager.getConnection(masterUrl, dbUser, dbPassword)) {
            conn.setAutoCommit(true);

            String checkSql = "SELECT 1 FROM pg_database WHERE datname = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, cleanDb);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }

            log.info("Création de la base PostgreSQL dédiée '{}'...", cleanDb);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DATABASE \"" + cleanDb + "\" ENCODING 'UTF8'");
                log.info("Base de données '{}' créée avec succès !", cleanDb);
                return true;
            }
        } catch (Exception e) {
            log.warn("Impossible de vérifier/créer la base '{}' via le master PostgreSQL : {}", cleanDb, e.getMessage());
            return false;
        }
    }

    /**
     * Exécute les tables DDL de base et insère les configurations par défaut dans la base du tenant.
     */
    public void initializeTenantSchema(DataSource tenantDataSource, String databaseName, TenantProvisioningRequest request) {
        log.info("Initialisation du schéma DDL et des seeders pour la base tenant : '{}'", databaseName);

        try (Connection connection = tenantDataSource.getConnection()) {
            connection.setAutoCommit(true);

            try (Statement stmt = connection.createStatement()) {
                // Extension UUID
                stmt.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";");

                // 1. Currencies
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS currencies (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        code VARCHAR(10) NOT NULL UNIQUE,
                        symbol VARCHAR(10) NOT NULL,
                        name_fr VARCHAR(100) NOT NULL,
                        name_en VARCHAR(100) NOT NULL,
                        decimal_digits INT DEFAULT 2,
                        is_active BOOLEAN DEFAULT TRUE
                    );
                """);

                // 2. System Settings
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS system_settings (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        institution_name VARCHAR(200),
                        institution_type VARCHAR(50),
                        system_preset VARCHAR(30) DEFAULT 'FRENCH',
                        logo_url TEXT,
                        primary_color VARCHAR(7),
                        default_language VARCHAR(10) DEFAULT 'fr',
                        main_currency_id UUID REFERENCES currencies(id),
                        currency_code VARCHAR(10),
                        currency_symbol VARCHAR(10),
                        currency_name_fr VARCHAR(50),
                        time_zone VARCHAR(50) DEFAULT 'UTC',
                        date_format VARCHAR(20) DEFAULT 'DD/MM/YYYY',
                        enable_sms_notifications BOOLEAN DEFAULT FALSE,
                        enable_email_notifications BOOLEAN DEFAULT TRUE,
                        principal_signature_base64 TEXT,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                    );
                """);

                // 3. Academic Cycles
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS academic_cycles (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        code VARCHAR(50) NOT NULL,
                        name_fr VARCHAR(100) NOT NULL,
                        name_en VARCHAR(100) NOT NULL,
                        sequence_order INT NOT NULL,
                        description TEXT,
                        is_active BOOLEAN DEFAULT TRUE,
                        CONSTRAINT uq_cycle_code UNIQUE (code)
                    );
                """);

                // 4. Academic Levels
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS academic_levels (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        cycle_id UUID REFERENCES academic_cycles(id) ON DELETE CASCADE,
                        code VARCHAR(50) NOT NULL,
                        name_fr VARCHAR(100) NOT NULL,
                        name_en VARCHAR(100) NOT NULL,
                        sequence_order INT NOT NULL,
                        is_active BOOLEAN DEFAULT TRUE,
                        CONSTRAINT uq_level_code UNIQUE (code)
                    );
                """);

                // 5. Academic Years
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS academic_years (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        code VARCHAR(50) NOT NULL,
                        start_date DATE NOT NULL,
                        end_date DATE NOT NULL,
                        is_current BOOLEAN DEFAULT FALSE,
                        status VARCHAR(20) DEFAULT 'PLANNED',
                        CONSTRAINT uq_year_code UNIQUE (code)
                    );
                """);

                // 6. Academic Periods
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS academic_periods (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        academic_year_id UUID REFERENCES academic_years(id) ON DELETE CASCADE,
                        code VARCHAR(50) NOT NULL,
                        name_fr VARCHAR(100) NOT NULL,
                        name_en VARCHAR(100) NOT NULL,
                        start_date DATE NOT NULL,
                        end_date DATE NOT NULL,
                        is_current BOOLEAN DEFAULT FALSE,
                        status VARCHAR(20) DEFAULT 'OPEN'
                    );
                """);

                // 7. Grading Systems
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS grading_systems (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        name VARCHAR(100) NOT NULL,
                        max_score DECIMAL(5,2) DEFAULT 20.00,
                        passing_score DECIMAL(5,2) DEFAULT 10.00,
                        grading_type VARCHAR(20) NOT NULL,
                        is_default BOOLEAN DEFAULT FALSE
                    );
                """);

                // 8. Rooms
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS rooms (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        code VARCHAR(50) NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        capacity INT DEFAULT 30,
                        room_type VARCHAR(50),
                        is_available BOOLEAN DEFAULT TRUE,
                        CONSTRAINT uq_room_code UNIQUE (code)
                    );
                """);

                // 9. Classrooms
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS classrooms (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        level_id UUID REFERENCES academic_levels(id) ON DELETE SET NULL,
                        code VARCHAR(50) NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        capacity INT DEFAULT 40,
                        main_room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
                        main_teacher_id UUID,
                        is_active BOOLEAN DEFAULT TRUE,
                        CONSTRAINT uq_classroom_code UNIQUE (code)
                    );
                """);

                // 10. Subjects
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS subjects (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        level_id UUID REFERENCES academic_levels(id) ON DELETE SET NULL,
                        code VARCHAR(50) NOT NULL,
                        name_fr VARCHAR(100) NOT NULL,
                        name_en VARCHAR(100) NOT NULL,
                        coefficient DECIMAL(4,2) DEFAULT 1.0,
                        is_active BOOLEAN DEFAULT TRUE,
                        CONSTRAINT uq_subject_code UNIQUE (code)
                    );
                """);

                // 11. Students
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS students (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        registration_number VARCHAR(50) NOT NULL UNIQUE,
                        first_name VARCHAR(100) NOT NULL,
                        last_name VARCHAR(100) NOT NULL,
                        date_of_birth DATE NOT NULL,
                        gender VARCHAR(10) NOT NULL,
                        email VARCHAR(100),
                        phone_number VARCHAR(30),
                        parent_name VARCHAR(150),
                        parent_phone VARCHAR(30),
                        is_active BOOLEAN DEFAULT TRUE,
                        allergies VARCHAR(250),
                        emergency_contact_name VARCHAR(150),
                        emergency_contact_phone VARCHAR(30)
                    );
                """);

                // 12. Student Enrollments
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS student_enrollments (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                        academic_year_id UUID NOT NULL REFERENCES academic_years(id) ON DELETE CASCADE,
                        classroom_id UUID NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
                        enrollment_date DATE NOT NULL,
                        status VARCHAR(20) DEFAULT 'ACTIVE',
                        CONSTRAINT uq_student_year UNIQUE (student_id, academic_year_id)
                    );
                """);

                // 13. Evaluations & Grades
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS evaluations (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        classroom_id UUID NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
                        subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
                        period_id UUID NOT NULL REFERENCES academic_periods(id) ON DELETE CASCADE,
                        title VARCHAR(150) NOT NULL,
                        evaluation_type VARCHAR(50) NOT NULL,
                        max_score DECIMAL(5,2) DEFAULT 20.00,
                        weight DECIMAL(4,2) DEFAULT 1.00,
                        evaluation_date DATE NOT NULL,
                        is_published BOOLEAN DEFAULT FALSE
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS student_grades (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        evaluation_id UUID NOT NULL REFERENCES evaluations(id) ON DELETE CASCADE,
                        student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                        score DECIMAL(5,2),
                        is_absent BOOLEAN DEFAULT FALSE,
                        appreciation VARCHAR(250),
                        CONSTRAINT uq_eval_student UNIQUE (evaluation_id, student_id)
                    );
                """);

                // 14. Finance: Tuition Fees & Payments
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS tuition_fees (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        level_id UUID REFERENCES academic_levels(id) ON DELETE CASCADE,
                        academic_year_id UUID REFERENCES academic_years(id) ON DELETE CASCADE,
                        title VARCHAR(150) NOT NULL,
                        amount DECIMAL(12,2) NOT NULL,
                        due_date DATE,
                        is_mandatory BOOLEAN DEFAULT TRUE
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS student_payments (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id UUID,
                        student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                        tuition_fee_id UUID REFERENCES tuition_fees(id) ON DELETE SET NULL,
                        academic_year_id UUID REFERENCES academic_years(id) ON DELETE CASCADE,
                        amount_paid DECIMAL(12,2) NOT NULL,
                        payment_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        payment_method VARCHAR(50) NOT NULL,
                        receipt_number VARCHAR(100) UNIQUE,
                        cashier_username VARCHAR(100),
                        notes TEXT
                    );
                """);

                // 15. Seed Currencies
                stmt.execute("""
                    INSERT INTO currencies (id, code, symbol, name_fr, name_en, decimal_digits, is_active)
                    VALUES 
                        ('00000000-0000-0000-0000-000000000001', 'XOF', 'FCFA', 'Franc CFA (UEMOA)', 'CFA Franc', 0, true),
                        ('00000000-0000-0000-0000-000000000002', 'XAF', 'FCFA', 'Franc CFA (CEMAC)', 'Central African CFA Franc', 0, true),
                        ('00000000-0000-0000-0000-000000000003', 'EUR', '€', 'Euro', 'Euro', 2, true),
                        ('00000000-0000-0000-0000-000000000004', 'USD', '$', 'Dollar Américain', 'US Dollar', 2, true),
                        ('00000000-0000-0000-0000-000000000005', 'GNF', 'FG', 'Franc Guinéen', 'Guinean Franc', 0, true),
                        ('00000000-0000-0000-0000-000000000006', 'CAD', '$', 'Dollar Canadien', 'Canadian Dollar', 2, true)
                    ON CONFLICT (code) DO NOTHING;
                """);

                // 16. Seed Default Grading Scale
                stmt.execute("""
                    INSERT INTO grading_systems (name, max_score, passing_score, grading_type, is_default)
                    SELECT 'Système Standard 20/20', 20.00, 10.00, 'NUMERIC_20', true
                    WHERE NOT EXISTS (SELECT 1 FROM grading_systems WHERE is_default = true);
                """);

                // 17. Seed System Settings si renseigné
                String instName = request != null && request.getTenantName() != null ? request.getTenantName() : "Établissement Démo";
                String instType = request != null && request.getInstitutionType() != null ? request.getInstitutionType() : "PRIVATE";
                String preset = request != null && request.getSystemPreset() != null ? request.getSystemPreset() : "FRENCH";
                String currCode = request != null && request.getCurrencyCode() != null ? request.getCurrencyCode() : "XOF";
                String currSym = request != null && request.getCurrencySymbol() != null ? request.getCurrencySymbol() : "FCFA";
                String currFr = request != null && request.getCurrencyNameFr() != null ? request.getCurrencyNameFr() : "Franc CFA";
                String lang = request != null && request.getDefaultLanguage() != null ? request.getDefaultLanguage() : "fr";
                UUID tId = request != null && request.getTenantId() != null ? request.getTenantId() : UUID.randomUUID();

                stmt.execute(String.format("""
                    INSERT INTO system_settings (tenant_id, institution_name, institution_type, system_preset, default_language, currency_code, currency_symbol, currency_name_fr)
                    SELECT '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s'
                    WHERE NOT EXISTS (SELECT 1 FROM system_settings LIMIT 1);
                """, tId, instName.replace("'", "''"), instType, preset, lang, currCode, currSym, currFr.replace("'", "''")));

                log.info("Schéma DDL et seeders initialisés avec succès pour '{}'", databaseName);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du schéma DDL pour la base tenant '{}' : {}", databaseName, e.getMessage(), e);
        }
    }
}
