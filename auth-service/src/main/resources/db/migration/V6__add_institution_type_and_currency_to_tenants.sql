-- Ajout des colonnes de configuration initiale d'établissement (définies par le Super Admin SaaS)
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS institution_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS system_preset VARCHAR(30),
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS currency_symbol VARCHAR(10),
    ADD COLUMN IF NOT EXISTS currency_name_fr VARCHAR(50),
    ADD COLUMN IF NOT EXISTS default_language VARCHAR(10);
