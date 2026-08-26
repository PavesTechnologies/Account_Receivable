-- Epic 4 - Tool / Software / License Billing (Phase 1)
-- Reference INSERT statements for the billing_frequency_master records
-- required for recurring billing configuration.
--
-- NOT executed automatically - this project has no Flyway/Liquibase and no
-- data.sql seeding mechanism (schema is `spring.jpa.hibernate.ddl-auto=update`,
-- and no other master table in this project is auto-seeded either). Run
-- manually per environment after the billing_frequency_master table exists, e.g.
-- via the BillingFrequencyMasterController API (POST /api/billing-frequency) or by
-- executing this script directly against the database.

-- First, update any existing records to ensure they have valid duration values
-- This fixes the "Billing Frequency duration value must be positive" error
UPDATE billing_frequency_master
SET duration_value = 7, duration_unit = 'DAYS'
WHERE billing_frequency_name = 'Weekly' AND (duration_value IS NULL OR duration_value <= 0 OR duration_unit IS NULL);

UPDATE billing_frequency_master
SET duration_value = 14, duration_unit = 'DAYS'
WHERE billing_frequency_name = 'Bi-Weekly' AND (duration_value IS NULL OR duration_value <= 0 OR duration_unit IS NULL);

UPDATE billing_frequency_master
SET duration_value = 1, duration_unit = 'MONTHS'
WHERE billing_frequency_name = 'Monthly' AND (duration_value IS NULL OR duration_value <= 0 OR duration_unit IS NULL);

UPDATE billing_frequency_master
SET duration_value = 3, duration_unit = 'MONTHS'
WHERE billing_frequency_name = 'Quarterly' AND (duration_value IS NULL OR duration_value <= 0 OR duration_unit IS NULL);

UPDATE billing_frequency_master
SET duration_value = 6, duration_unit = 'MONTHS'
WHERE billing_frequency_name = 'Half-Yearly' AND (duration_value IS NULL OR duration_value <= 0 OR duration_unit IS NULL);

UPDATE billing_frequency_master
SET duration_value = 1, duration_unit = 'YEARS'
WHERE billing_frequency_name = 'Annually' AND (duration_value IS NULL OR duration_value <= 0 OR duration_unit IS NULL);

-- Insert standard billing frequency records if they don't exist
INSERT INTO billing_frequency_master
    (billing_frequency_id, billing_frequency_name, description, duration_value, duration_unit, is_active, created_at, updated_at)
SELECT 
    UUID(), 
    'Weekly', 
    'Billing occurs every week', 
    7, 
    'DAYS', 
    1, 
    NOW(), 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_frequency_master WHERE billing_frequency_name = 'Weekly'
);

INSERT INTO billing_frequency_master
    (billing_frequency_id, billing_frequency_name, description, duration_value, duration_unit, is_active, created_at, updated_at)
SELECT 
    UUID(), 
    'Bi-Weekly', 
    'Billing occurs every two weeks', 
    14, 
    'DAYS', 
    1, 
    NOW(), 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_frequency_master WHERE billing_frequency_name = 'Bi-Weekly'
);

INSERT INTO billing_frequency_master
    (billing_frequency_id, billing_frequency_name, description, duration_value, duration_unit, is_active, created_at, updated_at)
SELECT 
    UUID(), 
    'Monthly', 
    'Billing occurs every month', 
    1, 
    'MONTHS', 
    1, 
    NOW(), 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_frequency_master WHERE billing_frequency_name = 'Monthly'
);

INSERT INTO billing_frequency_master
    (billing_frequency_id, billing_frequency_name, description, duration_value, duration_unit, is_active, created_at, updated_at)
SELECT 
    UUID(), 
    'Quarterly', 
    'Billing occurs every quarter', 
    3, 
    'MONTHS', 
    1, 
    NOW(), 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_frequency_master WHERE billing_frequency_name = 'Quarterly'
);

INSERT INTO billing_frequency_master
    (billing_frequency_id, billing_frequency_name, description, duration_value, duration_unit, is_active, created_at, updated_at)
SELECT 
    UUID(), 
    'Half-Yearly', 
    'Billing occurs every six months', 
    6, 
    'MONTHS', 
    1, 
    NOW(), 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_frequency_master WHERE billing_frequency_name = 'Half-Yearly'
);

INSERT INTO billing_frequency_master
    (billing_frequency_id, billing_frequency_name, description, duration_value, duration_unit, is_active, created_at, updated_at)
SELECT 
    UUID(), 
    'Annually', 
    'Billing occurs every year', 
    1, 
    'YEARS', 
    1, 
    NOW(), 
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_frequency_master WHERE billing_frequency_name = 'Annually'
);
