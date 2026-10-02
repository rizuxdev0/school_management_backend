-- Ajout de la colonne database_name pour l'architecture Multi-Tenant Database-per-Tenant
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS database_name VARCHAR(100);

-- Mise à jour automatique des tenants existants
UPDATE tenants 
SET database_name = CONCAT('school_tenant_', LOWER(REGEXP_REPLACE(code, '[^a-zA-Z0-9_]', '_', 'g')))
WHERE database_name IS NULL;
