-- Dynamic Tax Configuration Master Data Seed Reference
-- Reference INSERT statements for tax regions, regimes, types, and components
-- required for dynamic tax configuration.
--
-- NOT executed automatically - this project has no Flyway/Liquibase and no
-- data.sql seeding mechanism (schema is `spring.jpa.hibernate.ddl-auto=update`,
-- and no other master table in this project is auto-seeded either). Run
-- manually per environment after the tax_regime_master and tax_component_master
-- tables exist, e.g. via the respective Controller APIs or by executing this
-- script directly against the database.

-- =========================================================
-- TAX TYPES
-- =========================================================
-- Generic tax types that can be used across regimes
INSERT INTO tax_type_master
    (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'CGST',
    'Central GST',
    'Central Goods and Services Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_type_master WHERE tax_type_code = 'CGST'
);

INSERT INTO tax_type_master
    (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'SGST',
    'State GST',
    'State Goods and Services Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_type_master WHERE tax_type_code = 'SGST'
);

INSERT INTO tax_type_master
    (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'IGST',
    'Integrated GST',
    'Integrated Goods and Services Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_type_master WHERE tax_type_code = 'IGST'
);

INSERT INTO tax_type_master
    (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'VAT',
    'VAT',
    'Value Added Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_type_master WHERE tax_type_code = 'VAT'
);

INSERT INTO tax_type_master
    (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'STATE_TAX',
    'State Tax',
    'State Sales Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_type_master WHERE tax_type_code = 'STATE_TAX'
);

INSERT INTO tax_type_master
    (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'LOCAL_TAX',
    'Local Tax',
    'Local Sales Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_type_master WHERE tax_type_code = 'LOCAL_TAX'
);

-- =========================================================
-- TAX REGIMES
-- =========================================================
-- High-level tax regimes
INSERT INTO tax_regime_master
    (tax_regime_id, tax_regime_code, tax_regime_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'GST',
    'GST',
    'Goods and Services Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_regime_master WHERE tax_regime_code = 'GST'
);

INSERT INTO tax_regime_master
    (tax_regime_id, tax_regime_code, tax_regime_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'VAT',
    'VAT',
    'Value Added Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_regime_master WHERE tax_regime_code = 'VAT'
);

INSERT INTO tax_regime_master
    (tax_regime_id, tax_regime_code, tax_regime_name, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'SALES_TAX',
    'SALES_TAX',
    'Sales Tax',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_regime_master WHERE tax_regime_code = 'SALES_TAX'
);

-- =========================================================
-- TAX REGIONS
-- =========================================================
-- Countries/jurisdictions
-- Note: Existing tax_region records should be preserved. This only adds new ones.
INSERT INTO tax_region_master
    (tax_region_id, tax_region_code, tax_region_name, tax_regime, currency_code, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'GB',
    'United Kingdom',
    'VAT',
    'GBP',
    'United Kingdom',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_region_master WHERE tax_region_code = 'GB'
);

INSERT INTO tax_region_master
    (tax_region_id, tax_region_code, tax_region_name, tax_regime, currency_code, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'SG',
    'Singapore',
    'GST',
    'SGD',
    'Singapore',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_region_master WHERE tax_region_code = 'SG'
);

INSERT INTO tax_region_master
    (tax_region_id, tax_region_code, tax_region_name, tax_regime, currency_code, description, is_active, created_at, updated_at)
SELECT
    UUID(),
    'US',
    'United States',
    'SALES_TAX',
    'USD',
    'United States',
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_region_master WHERE tax_region_code = 'US'
);

-- =========================================================
-- TAX COMPONENTS
-- =========================================================
-- Components for GST regime (India, Singapore)
-- CGST
INSERT INTO tax_component_master
    (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    UUID(),
    (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'GST' LIMIT 1),
    (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'CGST' LIMIT 1),
    'CGST',
    'CGST',
    'Central Goods and Services Tax',
    'PERCENTAGE',
    1,
    '2020-01-01',
    NULL,
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_component_master tc
    JOIN tax_regime_master tr ON tc.tax_regime_id = tr.tax_regime_id
    JOIN tax_type_master tt ON tc.tax_type_id = tt.tax_type_id
    WHERE tr.tax_regime_code = 'GST' AND tt.tax_type_code = 'CGST'
);

-- SGST
INSERT INTO tax_component_master
    (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    UUID(),
    (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'GST' LIMIT 1),
    (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'SGST' LIMIT 1),
    'SGST',
    'SGST',
    'State Goods and Services Tax',
    'PERCENTAGE',
    2,
    '2020-01-01',
    NULL,
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_component_master tc
    JOIN tax_regime_master tr ON tc.tax_regime_id = tr.tax_regime_id
    JOIN tax_type_master tt ON tc.tax_type_id = tt.tax_type_id
    WHERE tr.tax_regime_code = 'GST' AND tt.tax_type_code = 'SGST'
);

-- IGST
INSERT INTO tax_component_master
    (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    UUID(),
    (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'GST' LIMIT 1),
    (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'IGST' LIMIT 1),
    'IGST',
    'IGST',
    'Integrated Goods and Services Tax',
    'PERCENTAGE',
    3,
    '2020-01-01',
    NULL,
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_component_master tc
    JOIN tax_regime_master tr ON tc.tax_regime_id = tr.tax_regime_id
    JOIN tax_type_master tt ON tc.tax_type_id = tt.tax_type_id
    WHERE tr.tax_regime_code = 'GST' AND tt.tax_type_code = 'IGST'
);

-- Components for VAT regime (UK)
INSERT INTO tax_component_master
    (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    UUID(),
    (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'VAT' LIMIT 1),
    (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'VAT' LIMIT 1),
    'VAT',
    'VAT',
    'Value Added Tax',
    'PERCENTAGE',
    1,
    '2020-01-01',
    NULL,
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_component_master tc
    JOIN tax_regime_master tr ON tc.tax_regime_id = tr.tax_regime_id
    JOIN tax_type_master tt ON tc.tax_type_id = tt.tax_type_id
    WHERE tr.tax_regime_code = 'VAT' AND tt.tax_type_code = 'VAT'
);

-- Components for SALES_TAX regime (USA)
-- State Tax
INSERT INTO tax_component_master
    (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    UUID(),
    (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'SALES_TAX' LIMIT 1),
    (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'STATE_TAX' LIMIT 1),
    'STATE_TAX',
    'State Tax',
    'State Sales Tax',
    'PERCENTAGE',
    1,
    '2020-01-01',
    NULL,
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_component_master tc
    JOIN tax_regime_master tr ON tc.tax_regime_id = tr.tax_regime_id
    JOIN tax_type_master tt ON tc.tax_type_id = tt.tax_type_id
    WHERE tr.tax_regime_code = 'SALES_TAX' AND tt.tax_type_code = 'STATE_TAX'
);

-- Local Tax
INSERT INTO tax_component_master
    (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    UUID(),
    (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'SALES_TAX' LIMIT 1),
    (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'LOCAL_TAX' LIMIT 1),
    'LOCAL_TAX',
    'Local Tax',
    'Local Sales Tax',
    'PERCENTAGE',
    2,
    '2020-01-01',
    NULL,
    1,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tax_component_master tc
    JOIN tax_regime_master tr ON tc.tax_regime_id = tr.tax_regime_id
    JOIN tax_type_master tt ON tc.tax_type_id = tt.tax_type_id
    WHERE tr.tax_regime_code = 'SALES_TAX' AND tt.tax_type_code = 'LOCAL_TAX'
);
