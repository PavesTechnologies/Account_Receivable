-- Invoice tax breakdown: diagnostics and tax-context backfill (MySQL, run manually).
--
-- Background: for Time & Material billing snapshots, tax calculation used to
-- complete (snapshot -> TAX_COMPLETED) with ZERO components and zero tax when
-- no active component of the resolved tax configuration applied to the
-- transaction's jurisdictions. Invoice generation then copied that empty
-- result, producing "No tax components available" and Tax = 0.00. Tax
-- calculation now fails with the missing prerequisite instead.
--
-- NOTE: This project has no Flyway/Liquibase runner; the three new invoice
-- columns (tax_region_code, source_tax_jurisdiction_code,
-- destination_tax_jurisdiction_code) are added by
-- spring.jpa.hibernate.ddl-auto=update on application startup.
-- Replace :invoice_number below before running the diagnostics.

-- ---------------------------------------------------------------------------
-- 1. Diagnostics (read-only) for one invoice
-- ---------------------------------------------------------------------------

-- 1a. Invoice, its snapshot and its tax calculation.
SELECT i.invoice_id, i.invoice_number, i.status AS invoice_status,
       i.subtotal, i.total_tax_amount, i.grand_total,
       s.billing_snapshot_id, s.snapshot_number, s.status AS snapshot_status,
       s.tax_region_id, s.tax_region_code,
       s.source_tax_jurisdiction_code, s.destination_tax_jurisdiction_code,
       tc.tax_calculation_id, tc.status AS tax_calculation_status,
       tc.tax_configuration_id, tc.taxable_amount, tc.total_tax_amount AS calc_total_tax
FROM invoice i
LEFT JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
LEFT JOIN tax_calculation tc ON tc.tax_calculation_id = i.tax_calculation_id
WHERE i.invoice_number = :invoice_number;

-- 1b. Persisted calculation components vs invoice components (0 rows each = the defect).
SELECT 'tax_calculation_component' AS source, c.tax_type_code, c.applied_rate,
       c.tax_amount, c.applicability_type
FROM invoice i
JOIN tax_calculation_component c ON c.tax_calculation_id = i.tax_calculation_id
WHERE i.invoice_number = :invoice_number
UNION ALL
SELECT 'invoice_tax_component', c.tax_type_code, c.applied_rate,
       c.tax_amount, c.applicability_type
FROM invoice i
JOIN invoice_tax_component c ON c.invoice_id = i.invoice_id
WHERE i.invoice_number = :invoice_number;

-- 1c. The tax configuration the calculation used, and its components.
--     Compare applicability_type / is_active with the jurisdictions from 1a:
--     source = destination  -> SAME_JURISDICTION components apply
--     source <> destination -> DIFFERENT_JURISDICTION components apply
--     ALL components always apply.
SELECT tcfg.tax_configuration_id, tcfg.tax_regime, tcfg.is_active AS configuration_active,
       tcfg.effective_from, tcfg.effective_to,
       t.tax_type_code, comp.tax_rate, comp.applicability_type, comp.is_active AS component_active
FROM invoice i
JOIN tax_calculation tc ON tc.tax_calculation_id = i.tax_calculation_id
JOIN tax_configuration tcfg ON tcfg.tax_configuration_id = tc.tax_configuration_id
LEFT JOIN tax_configuration_component comp ON comp.tax_configuration_id = tcfg.tax_configuration_id
LEFT JOIN tax_type_master t ON t.tax_type_id = comp.tax_type_id
WHERE i.invoice_number = :invoice_number;

-- ---------------------------------------------------------------------------
-- 2. Backfill tax context onto existing snapshot-based invoices (idempotent)
-- ---------------------------------------------------------------------------
-- Copies the values frozen on each invoice's own billing snapshot. Only
-- fills columns that are still NULL; never touches amounts or components.
UPDATE invoice i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
SET i.tax_region_code = COALESCE(i.tax_region_code, s.tax_region_code),
    i.source_tax_jurisdiction_code = COALESCE(i.source_tax_jurisdiction_code, s.source_tax_jurisdiction_code),
    i.destination_tax_jurisdiction_code = COALESCE(i.destination_tax_jurisdiction_code, s.destination_tax_jurisdiction_code)
WHERE i.tax_region_code IS NULL
   OR i.source_tax_jurisdiction_code IS NULL
   OR i.destination_tax_jurisdiction_code IS NULL;

-- 3. Invoices already generated from a zero-component calculation.
--    These are NOT repaired by any script: their tax must be recalculated
--    after the tax configuration is corrected (see the final report).
SELECT i.invoice_id, i.invoice_number, i.status, i.subtotal, i.total_tax_amount
FROM invoice i
JOIN tax_calculation tc ON tc.tax_calculation_id = i.tax_calculation_id
LEFT JOIN tax_calculation_component c ON c.tax_calculation_id = tc.tax_calculation_id
WHERE c.tax_calculation_component_id IS NULL;
