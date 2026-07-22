-- Données d'initialisation pour le SaaS (Packs, Modules et Super Admin)

-- 1. Insertion des modules métiers au catalogue
INSERT INTO feature_modules (id, code, name_fr, name_en, description_fr, description_en) VALUES
(uuid_generate_v4(), 'ACADEMIC', 'Gestion Académique', 'Academic Management', 'Classes, sections, matières, emplois du temps', 'Classes, sections, subjects, timetables'),
(uuid_generate_v4(), 'EVALUATION', 'Évaluations & Bulletins', 'Evaluations & Bulletins', 'Saisie des notes, moyennes, bulletins JasperReports', 'Grades input, averages, report cards'),
(uuid_generate_v4(), 'ATTENDANCE', 'Assiduité & Présences', 'Attendance & Absences', 'Appel journalier, justificatifs et alertes sms/emails', 'Daily attendance, excuses and alerts'),
(uuid_generate_v4(), 'FINANCE', 'Gestion Financière', 'Financial Management', 'Frais scolaires, facturation, encaissement, reçus Jasper', 'Tuition fees, billing, payments, receipts'),
(uuid_generate_v4(), 'EXAMS', 'Examens', 'Exams', 'Planification des épreuves, répartition des salles et surveillants', 'Exam planning, room and proctor allocation'),
(uuid_generate_v4(), 'LIBRARY', 'Bibliothèque', 'Library', 'Catalogue de livres, prêts et retours', 'Book catalog, check-out and check-in'),
(uuid_generate_v4(), 'HR', 'Ressources Humaines', 'Human Resources', 'Gestion du personnel, contrats, congés', 'Staff management, contracts, leaves'),
(uuid_generate_v4(), 'MEDICAL', 'Santé & Infirmerie', 'Health & Infirmary', 'Fiches de santé, consultations et médicaments', 'Health records, consultations and medicine'),
(uuid_generate_v4(), 'DISCIPLINE', 'Discipline & Sanctions', 'Discipline', 'Registre des sanctions, avertissements', 'Sanctions registry, warnings')
ON CONFLICT (code) DO NOTHING;

-- 2. Insertion des Plans d'Abonnement standard
INSERT INTO subscription_plans (id, code, name_fr, name_en, price_monthly, price_yearly, max_students, max_staff, is_active) VALUES
(uuid_generate_v4(), 'STARTER', 'Pack Starter', 'Starter Pack', 49.00, 490.00, 100, 10, true),
(uuid_generate_v4(), 'PRO', 'Pack Pro', 'Pro Pack', 149.00, 1490.00, 500, 50, true),
(uuid_generate_v4(), 'ENTERPRISE', 'Pack Enterprise', 'Enterprise Pack', 299.00, 2990.00, 2000, 200, true),
(uuid_generate_v4(), 'CUSTOM', 'Pack Sur-Mesure', 'Custom Pack', 0.00, 0.00, 99999, 9999, true)
ON CONFLICT (code) DO NOTHING;

-- 3. Association des modules aux plans standard
-- STARTER : ACADEMIC seulement
INSERT INTO plan_feature_modules (plan_id, module_id)
SELECT p.id, m.id FROM subscription_plans p, feature_modules m
WHERE p.code = 'STARTER' AND m.code IN ('ACADEMIC')
ON CONFLICT DO NOTHING;

-- PRO : ACADEMIC, EVALUATION, ATTENDANCE, FINANCE
INSERT INTO plan_feature_modules (plan_id, module_id)
SELECT p.id, m.id FROM subscription_plans p, feature_modules m
WHERE p.code = 'PRO' AND m.code IN ('ACADEMIC', 'EVALUATION', 'ATTENDANCE', 'FINANCE')
ON CONFLICT DO NOTHING;

-- ENTERPRISE : Tous les modules
INSERT INTO plan_feature_modules (plan_id, module_id)
SELECT p.id, m.id FROM subscription_plans p, feature_modules m
WHERE p.code = 'ENTERPRISE'
ON CONFLICT DO NOTHING;

-- 4. Insertion du Super Administrateur SaaS Global
-- Username: superadmin
-- Password en clair: adminpassword
-- Le mot de passe crypté ci-dessous correspond à la clé de hachage BCrypt de 'adminpassword'
INSERT INTO users (id, tenant_id, username, email, password_hash, first_name, last_name, is_super_admin, is_active, is_account_non_locked) VALUES
(uuid_generate_v4(), null, 'superadmin', 'superadmin@schoolmanager.saas', '$2a$10$eE2O2c9w8L2X4/j2s1hN.O3aIoxUf6GgK6Z7dKqTf9D1d51a6E1qy', 'SaaS', 'SuperAdmin', true, true, true)
ON CONFLICT (username) DO NOTHING;
