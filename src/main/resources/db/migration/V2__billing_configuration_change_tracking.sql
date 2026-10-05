-- =========================================================
-- Billing Configuration Change Tracking Migration
-- =========================================================
-- This migration adds support for tracking changes when
-- APPROVED+ACTIVE billing configurations are edited.
-- Preserves previous approved state for comparison and restoration.
-- =========================================================

-- Table: billing_configuration_snapshot
-- Stores snapshots of the previous approved configuration state
CREATE TABLE IF NOT EXISTS billing_configuration_snapshot (
    snapshot_id UUID PRIMARY KEY,
    billing_configuration_id UUID NOT NULL,
    previous_approval_status VARCHAR(20) NOT NULL,
    previous_billing_status VARCHAR(20) NOT NULL,
    previous_client_id UUID,
    previous_project_id BIGINT,
    previous_billing_type_id UUID,
    previous_currency_id UUID,
    previous_payment_term_id UUID,
    previous_billing_frequency_id UUID,
    previous_tax_region_id UUID,
    previous_contract_value DECIMAL(19, 2),
    previous_expense_billing_eligible BOOLEAN,
    previous_effective_from DATE,
    previous_effective_to DATE,
    previous_hourly_rate DECIMAL(19, 2),
    previous_invoice_generation_type VARCHAR(50),
    previous_pricing_model VARCHAR(50),
    previous_billing_context VARCHAR(30),
    previous_product_name VARCHAR(200),
    previous_product_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    is_restored BOOLEAN NOT NULL DEFAULT FALSE,
    
    CONSTRAINT fk_snapshot_billing_config 
        FOREIGN KEY (billing_configuration_id) 
        REFERENCES billing_configuration(billing_configuration_id)
        ON DELETE CASCADE,
    
    CONSTRAINT fk_snapshot_client 
        FOREIGN KEY (previous_client_id) 
        REFERENCES client(client_id)
        ON DELETE SET NULL,
    
    CONSTRAINT fk_snapshot_project 
        FOREIGN KEY (previous_project_id) 
        REFERENCES project_master_reference(pms_project_id)
        ON DELETE SET NULL,
    
    CONSTRAINT fk_snapshot_billing_type 
        FOREIGN KEY (previous_billing_type_id) 
        REFERENCES billing_type_master(billing_type_id)
        ON DELETE SET NULL,
    
    CONSTRAINT fk_snapshot_currency 
        FOREIGN KEY (previous_currency_id) 
        REFERENCES currency_master(currency_id)
        ON DELETE SET NULL,
    
    CONSTRAINT fk_snapshot_payment_term 
        FOREIGN KEY (previous_payment_term_id) 
        REFERENCES payment_terms_master(payment_term_id)
        ON DELETE SET NULL,
    
    CONSTRAINT fk_snapshot_billing_frequency 
        FOREIGN KEY (previous_billing_frequency_id) 
        REFERENCES billing_frequency_master(billing_frequency_id)
        ON DELETE SET NULL,
    
    CONSTRAINT fk_snapshot_tax_region 
        FOREIGN KEY (previous_tax_region_id) 
        REFERENCES tax_region_master(tax_region_id)
        ON DELETE SET NULL
);

-- Index for faster lookup of latest snapshot
CREATE INDEX idx_snapshot_config_created 
    ON billing_configuration_snapshot(billing_configuration_id, created_at DESC);

-- Index for finding unrestored snapshots
CREATE INDEX idx_snapshot_unrestored 
    ON billing_configuration_snapshot(billing_configuration_id, is_restored);

-- Table: billing_configuration_change_detail
-- Stores individual field changes for comparison during approval
CREATE TABLE IF NOT EXISTS billing_configuration_change_detail (
    change_detail_id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    field_display_name VARCHAR(200) NOT NULL,
    field_type VARCHAR(50),
    previous_value VARCHAR(1000),
    new_value VARCHAR(1000),
    category VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_change_detail_snapshot 
        FOREIGN KEY (snapshot_id) 
        REFERENCES billing_configuration_snapshot(snapshot_id)
        ON DELETE CASCADE
);

-- Index for faster lookup of changes by snapshot
CREATE INDEX idx_change_detail_snapshot 
    ON billing_configuration_change_detail(snapshot_id, created_at ASC);

-- Index for category-based queries
CREATE INDEX idx_change_detail_category 
    ON billing_configuration_change_detail(category);
