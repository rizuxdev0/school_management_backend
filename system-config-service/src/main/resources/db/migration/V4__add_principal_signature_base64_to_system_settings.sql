-- V4 : Ajout de la colonne principal_signature_base64 pour stocker la signature numérique du directeur de l'établissement
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS principal_signature_base64 TEXT;
