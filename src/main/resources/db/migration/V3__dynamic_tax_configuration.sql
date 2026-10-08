-- =========================================================
-- Dynamic Tax Configuration Migration
-- =========================================================
-- This migration adds support for fully dynamic, country-based
-- tax configuration without hardcoding country-specific logic.
--
-- New tables:
-- - tax_regime_master: High-level tax regimes (GST, VAT, SALES_TAX)
-- - tax_component_master: Tax components per regime (CGST, SGST, IGST, VAT)
--
-- Existing tables updated:
-- - tax_configuration: Added tax_regime_id FK
--
-- This allows new countries, tax regimes, and tax components
-- to be added through master/configuration data without modifying
-- Java code or frontend code.
-- =========================================================

-- Table: tax_regime_master
-- Stores high-level tax regimes that can be associated with countries
CREATE TABLE IF NOT EXISTS tax_regime_master (
    tax_regime_id               UUID          NOT NULL,
    tax_regime_code             VARCHAR(50)   NOT NULL,
    tax_regime_name             VARCHAR(100)  NOT NULL,
    description                 VARCHAR(500)      NULL,
    is_active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP     NOT NULL,
    updated_at                  TIMESTAMP         NULL,
    PRIMARY KEY (tax_regime_id),
    CONSTRAINT uk_tax_regime_master_code UNIQUE (tax_regime_code)
);

-- Index for tax regime code lookups
CREATE INDEX idx_tax_regime_code ON tax_regime_master(tax_regime_code);

-- Index for active regimes
CREATE INDEX idx_tax_regime_active ON tax_regime_master(is_active);

-- Table: tax_component_master
-- Stores tax components within a tax regime
CREATE TABLE IF NOT EXISTS tax_component_master (
    tax_component_id            UUID          NOT NULL,
    tax_regime_id               UUID          NOT NULL,
    tax_type_id                 UUID          NOT NULL,
    component_code              VARCHAR(50)   NOT NULL,
    component_name              VARCHAR(100)  NOT NULL,
    description                 VARCHAR(500)      NULL,
    input_type                  VARCHAR(30)   NOT NULL DEFAULT 'PERCENTAGE',
    display_order               INT           NOT NULL DEFAULT 0,
    effective_from              DATE          NOT NULL,
    effective_to                DATE              NULL,
    is_active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP     NOT NULL,
    updated_at                  TIMESTAMP         NULL,
    PRIMARY KEY (tax_component_id),
    CONSTRAINT fk_tax_component_regime
        FOREIGN KEY (tax_regime_id)
        REFERENCES tax_regime_master(tax_regime_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tax_component_type
        FOREIGN KEY (tax_type_id)
        REFERENCES tax_type_master(tax_type_id)
        ON DELETE CASCADE
);

-- Index for regime-based component lookups
CREATE INDEX idx_tax_component_regime ON tax_component_master(tax_regime_id);

-- Index for type-based component lookups
CREATE INDEX idx_tax_component_type ON tax_component_master(tax_type_id);

-- Index for active components
CREATE INDEX idx_tax_component_active ON tax_component_master(is_active);

-- Make tax_regime column nullable in tax_region_master
-- (it was NOT NULL before, now it can be NULL to allow migration)
ALTER TABLE tax_region_master
MODIFY COLUMN tax_regime VARCHAR(50) NULL;

-- Add tax_regime_id column to tax_configuration
-- This allows configurations to reference tax regimes dynamically
ALTER TABLE tax_configuration
ADD COLUMN tax_regime_id UUID NULL AFTER tax_regime;

-- Add foreign key constraint for tax_regime_id in tax_configuration
ALTER TABLE tax_configuration
ADD CONSTRAINT fk_tax_configuration_regime
    FOREIGN KEY (tax_regime_id)
    REFERENCES tax_regime_master(tax_regime_id)
    ON DELETE SET NULL;

-- Make tax_regime column nullable in tax_configuration
-- (it was NOT NULL before, now it can be NULL to allow migration)
ALTER TABLE tax_configuration
MODIFY COLUMN tax_regime VARCHAR(50) NULL;
