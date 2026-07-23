-- V3 : Extension des limites de colonnes pour la table system_settings afin d'éviter les erreurs de dépassement de taille.

-- 1. Augmentation de la taille de la colonne téléphone à 255 caractères
ALTER TABLE system_settings ALTER COLUMN phone TYPE VARCHAR(255);

-- 2. Augmentation de la taille de la colonne time_zone à 100 caractères
ALTER TABLE system_settings ALTER COLUMN time_zone TYPE VARCHAR(100);

-- 3. Ajout ou forçage du type TEXT pour la colonne logo_base64 s'il a été créé en VARCHAR par Hibernate
ALTER TABLE system_settings ALTER COLUMN logo_base64 TYPE TEXT;

-- 4. Augmentation de la taille des colonnes primary_color, bulletin_template et watermark_text
ALTER TABLE system_settings ALTER COLUMN primary_color TYPE VARCHAR(50);
ALTER TABLE system_settings ALTER COLUMN bulletin_template TYPE VARCHAR(100);
ALTER TABLE system_settings ALTER COLUMN watermark_text TYPE VARCHAR(255);

-- 5. Ajout de la colonne bulletin_orientation pour la configuration de la disposition
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS bulletin_orientation VARCHAR(20) DEFAULT 'PORTRAIT';

