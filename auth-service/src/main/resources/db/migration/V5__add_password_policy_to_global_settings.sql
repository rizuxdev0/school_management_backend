-- V5__add_password_policy_to_global_settings.sql
-- Migration pour ajouter les colonnes de politique de mot de passe à la table global_settings.

CREATE TABLE IF NOT EXISTS global_settings (
    id UUID PRIMARY KEY,
    maintenance_mode BOOLEAN NOT NULL DEFAULT FALSE,
    announcement_text VARCHAR(1000),
    announcement_end TIMESTAMP WITH TIME ZONE
);

ALTER TABLE global_settings 
ADD COLUMN IF NOT EXISTS password_min_length INT NOT NULL DEFAULT 8,
ADD COLUMN IF NOT EXISTS password_require_uppercase BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS password_require_lowercase BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS password_require_number BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS password_require_special BOOLEAN NOT NULL DEFAULT TRUE;
