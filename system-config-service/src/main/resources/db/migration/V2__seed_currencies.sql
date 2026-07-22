-- Migration to seed major global currencies
INSERT INTO currencies (id, code, symbol, name_fr, name_en, decimal_digits, is_active) VALUES
(uuid_generate_v4(), 'USD', '$', 'Dollar américain', 'US Dollar', 2, true),
(uuid_generate_v4(), 'EUR', '€', 'Euro', 'Euro', 2, true),
(uuid_generate_v4(), 'XOF', 'F CFA', 'Franc CFA (BCEAO)', 'CFA Franc (BCEAO)', 0, true),
(uuid_generate_v4(), 'CAD', 'C$', 'Dollar canadien', 'Canadian Dollar', 2, true),
(uuid_generate_v4(), 'GBP', '£', 'Livre sterling', 'British Pound', 2, true),
(uuid_generate_v4(), 'CHF', 'CHF', 'Franc suisse', 'Swiss Franc', 2, true),
(uuid_generate_v4(), 'JPY', '¥', 'Yen japonais', 'Japanese Yen', 0, true),
(uuid_generate_v4(), 'CNY', '¥', 'Yuan chinois', 'Chinese Yuan', 2, true),
(uuid_generate_v4(), 'ZAR', 'R', 'Rand sud-africain', 'South African Rand', 2, true),
(uuid_generate_v4(), 'MAD', 'DH', 'Dirham marocain', 'Moroccan Dirham', 2, true)
ON CONFLICT (code) DO NOTHING;
