-- V4 : Création du plan d'abonnement UNLIMITED (Illimité) et synchronisation des modules SaaS

-- 1. Création du plan d'abonnement UNLIMITED (Illimité) pour by-passer toutes les limites
INSERT INTO subscription_plans (id, code, name_fr, name_en, price_monthly, price_yearly, max_students, max_staff, max_classrooms, max_books, is_active) VALUES
(uuid_generate_v4(), 'UNLIMITED', 'Pack Illimité', 'Unlimited Pack', 0.00, 0.00, 999999, 99999, 99999, 99999, true)
ON CONFLICT (code) DO NOTHING;

-- 2. Association de TOUS les modules métiers existants au plan UNLIMITED
INSERT INTO plan_feature_modules (plan_id, module_id)
SELECT p.id, m.id FROM subscription_plans p, feature_modules m
WHERE p.code = 'UNLIMITED'
ON CONFLICT DO NOTHING;

-- 3. Synchronisation automatique des modules pour les tenants configurés sur le plan UNLIMITED
INSERT INTO tenant_enabled_modules (tenant_id, module_id)
SELECT t.id, m.id 
FROM tenants t, feature_modules m
WHERE t.plan_code = 'UNLIMITED'
ON CONFLICT (tenant_id, module_id) DO NOTHING;

-- 4. Synchronisation rétroactive pour les autres plans au cas où
INSERT INTO tenant_enabled_modules (tenant_id, module_id)
SELECT t.id, m.id 
FROM tenants t, feature_modules m
WHERE t.plan_code = 'ENTERPRISE'
ON CONFLICT (tenant_id, module_id) DO NOTHING;

INSERT INTO tenant_enabled_modules (tenant_id, module_id)
SELECT t.id, m.id 
FROM tenants t, feature_modules m
WHERE t.plan_code = 'PREMIUM'
  AND m.code IN ('ACADEMIC', 'EVALUATION', 'ATTENDANCE', 'FINANCE')
ON CONFLICT (tenant_id, module_id) DO NOTHING;

INSERT INTO tenant_enabled_modules (tenant_id, module_id)
SELECT t.id, m.id 
FROM tenants t, feature_modules m
WHERE t.plan_code = 'STANDARD'
  AND m.code IN ('ACADEMIC')
ON CONFLICT (tenant_id, module_id) DO NOTHING;
