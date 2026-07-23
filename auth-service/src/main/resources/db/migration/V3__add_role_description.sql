-- V3 : Ajout de la colonne description à la table roles
ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(255);
