-- Schema Initial System Config & Currencies
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Devises
CREATE TABLE IF NOT EXISTS currencies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(3) NOT NULL UNIQUE,
    symbol VARCHAR(10) NOT NULL,
    name_fr VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    decimal_digits INT DEFAULT 2,
    is_active BOOLEAN DEFAULT TRUE
);

-- 2. System Settings par Tenant
CREATE TABLE IF NOT EXISTS system_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL UNIQUE,
    institution_name VARCHAR(200) NOT NULL,
    institution_type VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    default_language VARCHAR(5) DEFAULT 'fr',
    main_currency_id UUID REFERENCES currencies(id),
    time_zone VARCHAR(50) DEFAULT 'UTC',
    date_format VARCHAR(20) DEFAULT 'DD/MM/YYYY',
    enable_sms_notifications BOOLEAN DEFAULT FALSE,
    enable_email_notifications BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Cycles Académiques
CREATE TABLE IF NOT EXISTS academic_cycles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name_fr VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    sequence_order INT NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    CONSTRAINT uq_cycle_code_tenant UNIQUE (tenant_id, code)
);

-- 4. Niveaux Académiques
CREATE TABLE IF NOT EXISTS academic_levels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cycle_id UUID NOT NULL REFERENCES academic_cycles(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name_fr VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    sequence_order INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    CONSTRAINT uq_level_code_tenant UNIQUE (tenant_id, code)
);

-- 5. Années Académiques
CREATE TABLE IF NOT EXISTS academic_years (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_current BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'PLANNED',
    CONSTRAINT uq_year_code_tenant UNIQUE (tenant_id, code)
);

-- 6. Systèmes de Notation Paramétrables
CREATE TABLE IF NOT EXISTS grading_systems (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    max_score DECIMAL(5,2) DEFAULT 20.00,
    passing_score DECIMAL(5,2) DEFAULT 10.00,
    grading_type VARCHAR(20) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE
);
