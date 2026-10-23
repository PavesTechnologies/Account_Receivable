-- =====================================================================================================================
-- AR LOCAL DEMO SEED  -  FIRST-RUN ONLY  -  for the dedicated LOCAL demo database
--
-- Creates clients, projects, milestones, master data, tax setup, billing configurations and two billing snapshots, so the
-- demo needs no TMS, PMS, RMS or XMS. Everything is fictional (names, .invalid e-mail addresses, amounts).
--
-- HOW TO RUN  (manually, after the backend has been started once with SPRING_PROFILES_ACTIVE=local-demo so Hibernate created
-- the tables; the application never runs this script):
--     mysql --host=127.0.0.1 --port=<port> --user=<demo user> --password --database=ar_demo < local_demo_seed.sql
-- Do not use --force: the script relies on the client stopping at the first error.
--
-- THIS SCRIPT IS NOT RE-RUNNABLE.  It never deletes or updates any data. Before it inserts anything, a guard aborts it unless
--   1. the current database is named ar_demo (checked FIRST, in plain SQL, before anything at all is created; to use another
--      LOCAL schema name, change it in both the first check and DEMO_SCHEMA below),
--   2. every table it touches exists and uses the InnoDB engine (so the transaction below is atomic), and
--   3. every business table is EMPTY (clients, projects, milestones, billing configurations/snapshots/schedules, tax
--      configurations and calculations, invoices, receivables, ...). Reference/master tables may already hold rows.
-- If demo data already exists, or the database is not empty, the guard stops with a clear message and no DATA is changed:
-- recreate the schema instead (stop the backend; DROP DATABASE ar_demo; CREATE DATABASE ar_demo CHARACTER SET utf8mb4;
-- start the backend once to create the tables; run this script again).
--
-- ALL data changes run in ONE transaction (START TRANSACTION ... COMMIT). If any statement fails, the client stops, the
-- connection closes and MySQL rolls the whole transaction back, so a failed run leaves nothing behind.
-- (Reference rows use INSERT IGNORE because they have unique names/codes; they are referenced by that natural key.)
--
-- Shared databases: never run this against one. In any database not named ar_demo the script stops at its very first
-- statement that does anything, having created nothing. It contains no DELETE, UPDATE, TRUNCATE or DROP of data.
-- One side effect to know about: when it refuses AFTER the schema check (tables missing, not InnoDB, or data already there),
-- the empty stored procedure ar_demo_seed_guard stays behind in ar_demo. It holds no data; the next run drops it first and
-- recreating the schema removes it.
--
-- THE DEMO WORKFLOW this data supports (real API calls; see docs/local-demo.md for the exact requests):
--   a. create the company profile (POST /api/v1/company-profile) - required for invoices, deliberately not seeded
--   b. calculate tax for a snapshot (POST /api/v1/billing-snapshots/{id}/tax-calculation)
--   c. generate the invoice (POST /api/v1/billing-snapshots/{id}/invoice)
-- Snapshot BS-DEMO-000001 (August, taxable 2137.50) and BS-DEMO-000002 (September, taxable 1342.50) are both READY_FOR_TAX.
-- Project 9001's location equals the tax region name 'Demo United States', so source and destination jurisdictions are both
-- DEMO-US (SAME_JURISDICTION): the 6% state and 2% federal components apply, the 10% cross-jurisdiction component does not.
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- Schema check #1, plain SQL, BEFORE anything is created or dropped: in any database other than ar_demo this prints the
-- refusal and then deliberately fails (ERROR 1242), which stops the mysql client. In ar_demo it prints nothing.
-- ---------------------------------------------------------------------------------------------------------------------
SELECT CONCAT('Seed refused: current database is ', IFNULL(DATABASE(), '(none)'), ', expected ar_demo') AS refusal
 WHERE DATABASE() IS NULL OR DATABASE() <> 'ar_demo';
SELECT (SELECT 1 UNION ALL SELECT 2) AS seed_refused_wrong_database
 WHERE DATABASE() IS NULL OR DATABASE() <> 'ar_demo';

DROP PROCEDURE IF EXISTS ar_demo_seed_guard;

DELIMITER //
CREATE PROCEDURE ar_demo_seed_guard()
BEGIN
    DECLARE DEMO_SCHEMA VARCHAR(64) DEFAULT 'ar_demo';
    DECLARE v_missing TEXT;
    DECLARE v_not_innodb TEXT;
    DECLARE v_nonempty TEXT;
    DECLARE v_msg VARCHAR(128);   -- SIGNAL MESSAGE_TEXT is limited to 128 characters

    IF DATABASE() IS NULL OR DATABASE() <> DEMO_SCHEMA THEN
        SET v_msg = LEFT(CONCAT('Seed refused: current database is ', IFNULL(DATABASE(), '(none)'), ', expected ', DEMO_SCHEMA), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT GROUP_CONCAT(expected.t SEPARATOR ', ') INTO v_missing
      FROM (SELECT 'currency_master' AS t
            UNION ALL SELECT 'payment_terms_master'
            UNION ALL SELECT 'billing_type_master'
            UNION ALL SELECT 'billing_frequency_master'
            UNION ALL SELECT 'tax_regime_master'
            UNION ALL SELECT 'tax_type_master'
            UNION ALL SELECT 'tax_component_master'
            UNION ALL SELECT 'tax_region_master'
            UNION ALL SELECT 'tax_configuration'
            UNION ALL SELECT 'tax_configuration_component'
            UNION ALL SELECT 'client'
            UNION ALL SELECT 'project_master_reference'
            UNION ALL SELECT 'project_milestone_reference'
            UNION ALL SELECT 'billing_configuration'
            UNION ALL SELECT 'billing_tm_rate_card'
            UNION ALL SELECT 'billing_milestone_plan'
            UNION ALL SELECT 'billing_payment_entry'
            UNION ALL SELECT 'billing_snapshot'
            UNION ALL SELECT 'billing_snapshot_item'
            UNION ALL SELECT 'billing_acquisition'
            UNION ALL SELECT 'billing_schedule'
            UNION ALL SELECT 'billing_configuration_snapshot'
            UNION ALL SELECT 'billing_fixed_price_configuration'
            UNION ALL SELECT 'billing_subscription_configuration'
            UNION ALL SELECT 'billing_tm_rate_discount'
            UNION ALL SELECT 'project_tool_assignment'
            UNION ALL SELECT 'client_budget_summary'
            UNION ALL SELECT 'ar_project_tool_billing_config'
            UNION ALL SELECT 'software_billing_history'
            UNION ALL SELECT 'tax_calculation'
            UNION ALL SELECT 'tax_calculation_component'
            UNION ALL SELECT 'invoice'
            UNION ALL SELECT 'invoice_item'
            UNION ALL SELECT 'receivable') expected
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.tables x
                        WHERE x.table_schema = DATABASE() AND x.table_name = expected.t);
    IF v_missing IS NOT NULL THEN
        SELECT v_missing AS missing_tables, 'Start the backend once with SPRING_PROFILES_ACTIVE=local-demo so Hibernate creates the tables.' AS what_to_do;
        SET v_msg = LEFT(CONCAT('Seed refused: tables are missing: ', v_missing), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT GROUP_CONCAT(x.table_name SEPARATOR ', ') INTO v_not_innodb
      FROM information_schema.tables x
     WHERE x.table_schema = DATABASE() AND x.engine <> 'InnoDB'
       AND x.table_name IN ('currency_master', 'payment_terms_master', 'billing_type_master', 'billing_frequency_master', 'tax_regime_master', 'tax_type_master', 'tax_component_master', 'tax_region_master', 'tax_configuration', 'tax_configuration_component', 'client', 'project_master_reference', 'project_milestone_reference', 'billing_configuration', 'billing_tm_rate_card', 'billing_milestone_plan', 'billing_payment_entry', 'billing_snapshot', 'billing_snapshot_item', 'billing_acquisition', 'billing_schedule', 'billing_configuration_snapshot', 'billing_fixed_price_configuration', 'billing_subscription_configuration', 'billing_tm_rate_discount', 'project_tool_assignment', 'client_budget_summary', 'ar_project_tool_billing_config', 'software_billing_history', 'tax_calculation', 'tax_calculation_component', 'invoice', 'invoice_item', 'receivable');
    IF v_not_innodb IS NOT NULL THEN
        SELECT v_not_innodb AS tables_not_innodb, 'The seed is one transaction and needs InnoDB for every table it touches.' AS what_to_do;
        SET v_msg = LEFT(CONCAT('Seed refused: not InnoDB, cannot be atomic: ', v_not_innodb), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SET v_nonempty = CONCAT_WS(', ',
        IF((SELECT COUNT(*) FROM tax_configuration) > 0, 'tax_configuration', NULL),
        IF((SELECT COUNT(*) FROM tax_configuration_component) > 0, 'tax_configuration_component', NULL),
        IF((SELECT COUNT(*) FROM client) > 0, 'client', NULL),
        IF((SELECT COUNT(*) FROM project_master_reference) > 0, 'project_master_reference', NULL),
        IF((SELECT COUNT(*) FROM project_milestone_reference) > 0, 'project_milestone_reference', NULL),
        IF((SELECT COUNT(*) FROM billing_configuration) > 0, 'billing_configuration', NULL),
        IF((SELECT COUNT(*) FROM billing_tm_rate_card) > 0, 'billing_tm_rate_card', NULL),
        IF((SELECT COUNT(*) FROM billing_milestone_plan) > 0, 'billing_milestone_plan', NULL),
        IF((SELECT COUNT(*) FROM billing_payment_entry) > 0, 'billing_payment_entry', NULL),
        IF((SELECT COUNT(*) FROM billing_snapshot) > 0, 'billing_snapshot', NULL),
        IF((SELECT COUNT(*) FROM billing_snapshot_item) > 0, 'billing_snapshot_item', NULL),
        IF((SELECT COUNT(*) FROM billing_acquisition) > 0, 'billing_acquisition', NULL),
        IF((SELECT COUNT(*) FROM billing_schedule) > 0, 'billing_schedule', NULL),
        IF((SELECT COUNT(*) FROM billing_configuration_snapshot) > 0, 'billing_configuration_snapshot', NULL),
        IF((SELECT COUNT(*) FROM billing_fixed_price_configuration) > 0, 'billing_fixed_price_configuration', NULL),
        IF((SELECT COUNT(*) FROM billing_subscription_configuration) > 0, 'billing_subscription_configuration', NULL),
        IF((SELECT COUNT(*) FROM billing_tm_rate_discount) > 0, 'billing_tm_rate_discount', NULL),
        IF((SELECT COUNT(*) FROM project_tool_assignment) > 0, 'project_tool_assignment', NULL),
        IF((SELECT COUNT(*) FROM client_budget_summary) > 0, 'client_budget_summary', NULL),
        IF((SELECT COUNT(*) FROM ar_project_tool_billing_config) > 0, 'ar_project_tool_billing_config', NULL),
        IF((SELECT COUNT(*) FROM software_billing_history) > 0, 'software_billing_history', NULL),
        IF((SELECT COUNT(*) FROM tax_calculation) > 0, 'tax_calculation', NULL),
        IF((SELECT COUNT(*) FROM tax_calculation_component) > 0, 'tax_calculation_component', NULL),
        IF((SELECT COUNT(*) FROM invoice) > 0, 'invoice', NULL),
        IF((SELECT COUNT(*) FROM invoice_item) > 0, 'invoice_item', NULL),
        IF((SELECT COUNT(*) FROM receivable) > 0, 'receivable', NULL));
    IF v_nonempty IS NOT NULL AND v_nonempty <> '' THEN
        SELECT v_nonempty AS tables_with_data,
               'First-run only. Recreate the dedicated ar_demo schema: stop the backend, DROP DATABASE ar_demo, CREATE DATABASE ar_demo CHARACTER SET utf8mb4, start the backend once, run this script again.' AS what_to_do;
        SET v_msg = LEFT(CONCAT('Seed refused: first-run only, data exists in: ', v_nonempty), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
END//
DELIMITER ;

CALL ar_demo_seed_guard();
DROP PROCEDURE ar_demo_seed_guard;

-- ---------------------------------------------------------------------------------------------------------------------
-- From here on everything is ONE transaction. The guard (which is DDL) has already completed.
-- ---------------------------------------------------------------------------------------------------------------------
START TRANSACTION;

-- ---------------------------------------------------------------------------------------------------------------------
-- 1. Reference data (INSERT IGNORE: unique names/codes; existing entries are kept and referenced by natural key)
-- ---------------------------------------------------------------------------------------------------------------------
INSERT IGNORE INTO currency_master (currency_id, currency_code, currency_name, currency_symbol, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de010000000000000000000000000001'), 'USD', 'US Dollar', '$', 'Demo currency', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de010000000000000000000000000002'), 'INR', 'Indian Rupee', 'Rs.', 'Demo currency', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT IGNORE INTO payment_terms_master (payment_term_id, payment_term_name, payment_days, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de020000000000000000000000000001'), 'Net 15', 15, 'Payment due 15 days after invoice', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de020000000000000000000000000002'), 'Net 30', 30, 'Payment due 30 days after invoice', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT IGNORE INTO billing_type_master (billing_type_id, billing_type_name, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de030000000000000000000000000001'), 'Timesheet Based', 'Time and material billing from approved timesheets', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de030000000000000000000000000002'), 'Fixed Price', 'Fixed price billing', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de030000000000000000000000000003'), 'Milestone Plan', 'Milestone based billing', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de030000000000000000000000000004'), 'Subscription', 'Recurring subscription billing', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT IGNORE INTO billing_frequency_master (billing_frequency_id, billing_frequency_name, duration_value, duration_unit, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de040000000000000000000000000001'), 'Monthly', 1, 'MONTHS', 'Every month', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de040000000000000000000000000002'), 'Weekly', 7, 'DAYS', 'Every week', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de040000000000000000000000000003'), 'Quarterly', 3, 'MONTHS', 'Every quarter', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');

-- ---------------------------------------------------------------------------------------------------------------------
-- 2. Tax setup - what the application's tax lookup needs
--    TaxCalculationServiceImpl.calculateTax: snapshot READY_FOR_TAX + snapshot.tax_region_id set, then
--    TaxConfigurationRepository.findApplicableConfigurations(tax_region_id, billing_period_start): is_active = 1 AND
--    effective_from <= period start AND (effective_to IS NULL OR effective_to >= period start), newest first; each ACTIVE
--    component is applied when its applicability_type is ALL or equals the snapshot's jurisdiction relation (source vs
--    destination code: equal = SAME_JURISDICTION, different = DIFFERENT_JURISDICTION, either missing = ALL only).
--    The API (TaxConfigurationServiceImpl) additionally requires an active region and active tax types, at least one
--    component, one component per tax type, non-negative rates, and no overlapping active configuration per region.
-- ---------------------------------------------------------------------------------------------------------------------
INSERT IGNORE INTO tax_regime_master (tax_regime_id, tax_regime_code, tax_regime_name, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de0d0000000000000000000000000001'), 'DEMO-REGIME', 'Demo Sales Tax Regime', 'Fictional regime for the local demo', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT IGNORE INTO tax_type_master (tax_type_id, tax_type_code, tax_type_name, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de0e0000000000000000000000000001'), 'DEMO-STATE', 'Demo State Tax', 'Fictional tax type for the local demo', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0e0000000000000000000000000002'), 'DEMO-FEDERAL', 'Demo Federal Tax', 'Fictional tax type for the local demo', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0e0000000000000000000000000003'), 'DEMO-CROSS', 'Demo Cross-Jurisdiction Tax', 'Fictional tax type for the local demo', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0e0000000000000000000000000004'), 'DEMO-GST', 'Demo Goods and Services Tax', 'Fictional tax type for the local demo', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT IGNORE INTO tax_component_master (tax_component_id, tax_regime_id, tax_type_id, component_code, component_name, description, input_type, display_order, effective_from, effective_to, is_active, created_at, updated_at) VALUES
  (UNHEX('de0f0000000000000000000000000001'), (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'DEMO-REGIME'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-STATE'), 'DEMO-STATE-COMPONENT', 'Demo State Tax', 'Fictional component for the local demo', 'PERCENTAGE', 1, '2026-01-01', NULL, 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0f0000000000000000000000000002'), (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'DEMO-REGIME'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-FEDERAL'), 'DEMO-FEDERAL-COMPONENT', 'Demo Federal Tax', 'Fictional component for the local demo', 'PERCENTAGE', 2, '2026-01-01', NULL, 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0f0000000000000000000000000003'), (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'DEMO-REGIME'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-CROSS'), 'DEMO-CROSS-COMPONENT', 'Demo Cross-Jurisdiction Tax', 'Fictional component for the local demo', 'PERCENTAGE', 3, '2026-01-01', NULL, 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0f0000000000000000000000000004'), (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'DEMO-REGIME'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-GST'), 'DEMO-GST-COMPONENT', 'Demo Goods and Services Tax', 'Fictional component for the local demo', 'PERCENTAGE', 4, '2026-01-01', NULL, 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT IGNORE INTO tax_region_master (tax_region_id, tax_region_code, tax_region_name, currency_code, tax_regime, description, is_active, created_at, updated_at) VALUES
  (UNHEX('de100000000000000000000000000001'), 'DEMO-US', 'Demo United States', 'USD', 'DEMO-REGIME', 'Fictional tax region (seller and Alpha projects)', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de100000000000000000000000000002'), 'DEMO-IN', 'Demo India', 'INR', 'DEMO-REGIME', 'Fictional tax region (Beta projects)', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO tax_configuration (tax_configuration_id, tax_region_id, tax_regime_id, tax_regime, effective_from, effective_to, is_active, created_at, updated_at) VALUES
  (UNHEX('de110000000000000000000000000001'), (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-US'), (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'DEMO-REGIME'), 'DEMO-REGIME', '2026-01-01', NULL, 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de110000000000000000000000000002'), (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-IN'), (SELECT tax_regime_id FROM tax_regime_master WHERE tax_regime_code = 'DEMO-REGIME'), 'DEMO-REGIME', '2026-01-01', NULL, 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO tax_configuration_component (tax_configuration_component_id, tax_configuration_id, tax_type_id, tax_rate, applicability_type, is_active) VALUES
  (UNHEX('de120000000000000000000000000001'), UNHEX('de110000000000000000000000000001'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-STATE'), 6.0000, 'SAME_JURISDICTION', 1),
  (UNHEX('de120000000000000000000000000002'), UNHEX('de110000000000000000000000000001'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-FEDERAL'), 2.0000, 'SAME_JURISDICTION', 1),
  (UNHEX('de120000000000000000000000000003'), UNHEX('de110000000000000000000000000001'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-CROSS'), 10.0000, 'DIFFERENT_JURISDICTION', 1),
  (UNHEX('de120000000000000000000000000004'), UNHEX('de110000000000000000000000000002'), (SELECT tax_type_id FROM tax_type_master WHERE tax_type_code = 'DEMO-GST'), 18.0000, 'ALL', 1);

-- ---------------------------------------------------------------------------------------------------------------------
-- 3. Clients, projects and milestones (what RMS / PMS CDC would have delivered)
--    primary_location is a tax-region NAME: it is how the destination tax jurisdiction of a snapshot is resolved.
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO client (client_id, client_name, country_code, country_name, default_timezone, email, phone_number, client_type, delivery_model, priority_level, status, sla, compliance, escalation_contact, assets, created_at, updated_at) VALUES
  (UNHEX('de050000000000000000000000000001'), 'Demo Client Alpha', '+1', 'United States', 'America/New_York', 'billing@alpha.ar-demo.invalid', '5550100', 'STANDARD', 'OFFSHORE', 'MEDIUM', 'ACTIVE', 1, 1, 0, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de050000000000000000000000000002'), 'Demo Client Beta', '+91', 'India', 'Asia/Kolkata', 'accounts@beta.ar-demo.invalid', '5550200', 'STRATEGIC', 'HYBRID', 'HIGH', 'ACTIVE', 1, 1, 0, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO project_master_reference (pms_project_id, project_name, project_code, client_id, project_manager_id, resource_manager_id, delivery_owner_id, delivery_model, primary_location, priority_level, risk_level, start_date, end_date, project_budget, project_budget_currency, project_status, lifecycle_stage, data_status, changed_at, last_synced_at, created_at) VALUES
  (9001, 'Alpha Portal Rebuild', 'ALPHA-001', UNHEX('de050000000000000000000000000001'), 1001, 1002, 1003, 'OFFSHORE', 'Demo United States', 'MEDIUM', 'LOW', '2026-06-01', '2027-06-30', 120000.00, 'USD', 'ACTIVE', 'DEVELOPMENT', 'ACTIVE', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9002, 'Beta ERP Migration', 'BETA-001', UNHEX('de050000000000000000000000000002'), 1001, 1002, 1003, 'OFFSHORE', 'Demo India', 'MEDIUM', 'LOW', '2026-07-01', '2027-03-31', 2500000.00, 'INR', 'ACTIVE', 'DESIGN', 'ACTIVE', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9003, 'Alpha Discovery Phase', 'ALPHA-002', UNHEX('de050000000000000000000000000001'), 1001, 1002, 1003, 'OFFSHORE', 'Demo United States', 'MEDIUM', 'LOW', '2026-11-01', '2027-01-31', 30000.00, 'USD', 'PLANNING', 'INITIATION', 'ACTIVE', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9004, 'Beta Legacy Support', 'BETA-002', UNHEX('de050000000000000000000000000002'), 1001, 1002, 1003, 'OFFSHORE', 'Demo India', 'MEDIUM', 'LOW', '2025-01-01', '2025-12-31', 90000.00, 'INR', 'ARCHIVED', 'COMPLETED', 'ARCHIVED', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9005, 'Alpha Mobile App', 'ALPHA-003', UNHEX('de050000000000000000000000000001'), 1001, 1002, 1003, 'OFFSHORE', 'Demo United States', 'MEDIUM', 'LOW', '2025-03-01', '2025-11-30', 75000.00, 'USD', 'COMPLETED', 'COMPLETED', 'COMPLETED', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO project_milestone_reference (pms_milestone_id, pms_project_id, milestone_code, milestone_name, description, pms_epic_id, planned_start_date, planned_due_date, actual_completion_date, execution_status, billable, allocation_percentage, billing_readiness, source_version, source_deleted, created_at, changed_at, last_synced_at) VALUES
  (9101, 9002, 'M1', 'Requirements sign-off', 'Demo milestone for the Beta ERP Migration project', NULL, '2026-07-01', '2026-08-15', '2026-08-15', 'COMPLETED', 1, 20.00, 'READY_FOR_BILLING', 3, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9102, 9002, 'M2', 'Data migration complete', 'Demo milestone for the Beta ERP Migration project', NULL, '2026-08-16', '2026-09-20', '2026-09-20', 'COMPLETED', 1, 30.00, 'READY_FOR_BILLING', 3, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9103, 9002, 'M3', 'User acceptance testing complete', 'Demo milestone for the Beta ERP Migration project', NULL, '2026-09-21', '2026-11-15', NULL, 'IN_PROGRESS', 1, 30.00, 'NOT_READY', 2, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9104, 9002, 'M4', 'Go-live', 'Demo milestone for the Beta ERP Migration project', NULL, '2026-11-16', '2026-12-15', NULL, 'PLANNED', 1, 20.00, 'NOT_READY', 1, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (9105, 9002, 'M5', 'Optional reporting module (cancelled)', 'Demo milestone for the Beta ERP Migration project', NULL, NULL, NULL, NULL, 'CANCELLED', 0, 0.00, 'NOT_BILLABLE', 2, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');

-- ---------------------------------------------------------------------------------------------------------------------
-- 4. Billing configurations: 1 = time and material (project 9001, seller tax region DEMO-US), 2 = milestone plan (project 9002,
--    DEMO-IN); both approved and active. The configuration's tax region is the source jurisdiction of its snapshots.
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO billing_configuration (billing_configuration_id, client_id, project_id, billing_type_id, billing_frequency_id, currency_id, payment_term_id, tax_region_id, approval_status, billing_status, billing_context, invoice_generation_type, pricing_model, contract_value, hourly_rate, effective_from, effective_to, expense_billing_eligible, manually_deactivated, version, created_at, updated_at) VALUES
  (UNHEX('de060000000000000000000000000001'), UNHEX('de050000000000000000000000000001'), 9001, (SELECT billing_type_id FROM billing_type_master WHERE billing_type_name = 'Timesheet Based'), (SELECT billing_frequency_id FROM billing_frequency_master WHERE billing_frequency_name = 'Monthly'), (SELECT currency_id FROM currency_master WHERE currency_code = 'USD'), (SELECT payment_term_id FROM payment_terms_master WHERE payment_term_name = 'Net 30'), (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-US'), 'APPROVED', 'ACTIVE', 'PROJECT', 'MANUAL', 'STANDARD', NULL, NULL, '2026-06-01', '2027-06-30', 0, 0, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de060000000000000000000000000002'), UNHEX('de050000000000000000000000000002'), 9002, (SELECT billing_type_id FROM billing_type_master WHERE billing_type_name = 'Milestone Plan'), NULL, (SELECT currency_id FROM currency_master WHERE currency_code = 'INR'), (SELECT payment_term_id FROM payment_terms_master WHERE payment_term_name = 'Net 15'), (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-IN'), 'APPROVED', 'ACTIVE', 'PROJECT', 'MANUAL', NULL, 2500000.00, NULL, '2026-07-01', '2027-03-31', 0, 0, 0, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO billing_tm_rate_card (rate_card_id, billing_configuration_id, role_name, rate, rate_period, effective_from, effective_to, include_base_rate_in_invoice, is_active, remarks, created_at, updated_at) VALUES
  (UNHEX('de070000000000000000000000000001'), UNHEX('de060000000000000000000000000001'), 'Senior Developer', 85.00, 'HOURLY', '2026-06-01', NULL, 1, 1, 'Demo rate', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de070000000000000000000000000002'), UNHEX('de060000000000000000000000000001'), 'QA Engineer', 55.00, 'HOURLY', '2026-06-01', NULL, 1, 1, 'Demo rate', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de070000000000000000000000000003'), UNHEX('de060000000000000000000000000001'), 'Project Manager', 95.00, 'HOURLY', '2026-06-01', NULL, 1, 1, 'Demo rate', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO billing_milestone_plan (milestone_plan_id, billing_configuration_id, total_contract_value, payment_structure, remarks, is_active, created_at, updated_at) VALUES
  (UNHEX('de0a0000000000000000000000000001'), UNHEX('de060000000000000000000000000002'), 2500000.00, 'INSTALLMENTS', 'Demo milestone plan (20/30/30/20)', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO billing_payment_entry (payment_entry_id, milestone_plan_id, sequence, percentage, amount, billing_date, remarks, is_active, created_at, updated_at) VALUES
  (UNHEX('de0b0000000000000000000000000001'), UNHEX('de0a0000000000000000000000000001'), 1, 20.00, 500000.00, '2026-08-15', 'Milestone M1', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0b0000000000000000000000000002'), UNHEX('de0a0000000000000000000000000001'), 2, 30.00, 750000.00, '2026-09-20', 'Milestone M2', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0b0000000000000000000000000003'), UNHEX('de0a0000000000000000000000000001'), 3, 30.00, 750000.00, '2026-11-15', 'Milestone M3', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0b0000000000000000000000000004'), UNHEX('de0a0000000000000000000000000001'), 4, 20.00, 500000.00, '2026-12-15', 'Milestone M4', 1, '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');

-- ---------------------------------------------------------------------------------------------------------------------
-- 5. Billing snapshots for configuration 1 (what a time-and-material acquisition from TMS would have produced).
--    tax_region_id / tax_region_code come from the configuration; source and destination jurisdictions are derived as
--    BillingSnapshotServiceImpl does (configuration tax region code; code of the region named like the project location).
--    Both are READY_FOR_TAX: nothing downstream is claimed to be done.
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO billing_snapshot (billing_snapshot_id, snapshot_number, billing_configuration_id, project_id, client_id, billing_type_id, billing_frequency_id, currency_id, payment_term_id, tax_region_id, tax_region_code, source_tax_jurisdiction_code, destination_tax_jurisdiction_code, billing_period_start, billing_period_end, currency_code, billing_type, billing_frequency, payment_term_code, payment_term_name, expense_amount, subtotal, total_amount, status, created_by, updated_by, created_date, updated_date) VALUES
  (UNHEX('de080000000000000000000000000001'), 'BS-DEMO-000001', UNHEX('de060000000000000000000000000001'), 9001, UNHEX('de050000000000000000000000000001'), (SELECT billing_type_id FROM billing_type_master WHERE billing_type_name = 'Timesheet Based'), (SELECT billing_frequency_id FROM billing_frequency_master WHERE billing_frequency_name = 'Monthly'), (SELECT currency_id FROM currency_master WHERE currency_code = 'USD'), (SELECT payment_term_id FROM payment_terms_master WHERE payment_term_name = 'Net 30'), (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-US'), 'DEMO-US', 'DEMO-US', 'DEMO-US', '2026-08-01', '2026-08-31', 'USD', 'Timesheet Based', 'Monthly', 'NET30', 'Net 30', 0.00, 2137.50, 2137.50, 'READY_FOR_TAX', 'demo-seed', 'demo-seed', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de080000000000000000000000000002'), 'BS-DEMO-000002', UNHEX('de060000000000000000000000000001'), 9001, UNHEX('de050000000000000000000000000001'), (SELECT billing_type_id FROM billing_type_master WHERE billing_type_name = 'Timesheet Based'), (SELECT billing_frequency_id FROM billing_frequency_master WHERE billing_frequency_name = 'Monthly'), (SELECT currency_id FROM currency_master WHERE currency_code = 'USD'), (SELECT payment_term_id FROM payment_terms_master WHERE payment_term_name = 'Net 30'), (SELECT tax_region_id FROM tax_region_master WHERE tax_region_code = 'DEMO-US'), 'DEMO-US', 'DEMO-US', 'DEMO-US', '2026-09-01', '2026-09-30', 'USD', 'Timesheet Based', 'Monthly', 'NET30', 'Net 30', 0.00, 1342.50, 1342.50, 'READY_FOR_TAX', 'demo-seed', 'demo-seed', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');
INSERT INTO billing_snapshot_item (billing_snapshot_item_id, billing_snapshot_id, item_type, item_name, resource_name, role, work_date, quantity, rate, base_rate, amount, rate_period, rate_currency, include_in_invoice, source_system, approval_status, source_reference_id, source_event_id) VALUES
  (UNHEX('de090000000000000000000000000001'), UNHEX('de080000000000000000000000000001'), 'TIME_ENTRY', 'Timesheet entry - Senior Developer', 'Asha Rao', 'Senior Developer', '2026-08-04', 8.00, 85.00, 85.00, 680.00, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0001', 'DEMO-EVT-0001'),
  (UNHEX('de090000000000000000000000000002'), UNHEX('de080000000000000000000000000001'), 'TIME_ENTRY', 'Timesheet entry - Senior Developer', 'Asha Rao', 'Senior Developer', '2026-08-05', 7.50, 85.00, 85.00, 637.50, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0002', 'DEMO-EVT-0002'),
  (UNHEX('de090000000000000000000000000003'), UNHEX('de080000000000000000000000000001'), 'TIME_ENTRY', 'Timesheet entry - QA Engineer', 'Ben Chen', 'QA Engineer', '2026-08-06', 8.00, 55.00, 55.00, 440.00, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0003', 'DEMO-EVT-0003'),
  (UNHEX('de090000000000000000000000000004'), UNHEX('de080000000000000000000000000001'), 'TIME_ENTRY', 'Timesheet entry - Project Manager', 'Cara Diaz', 'Project Manager', '2026-08-07', 4.00, 95.00, 95.00, 380.00, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0004', 'DEMO-EVT-0004'),
  (UNHEX('de090000000000000000000000000005'), UNHEX('de080000000000000000000000000002'), 'TIME_ENTRY', 'Timesheet entry - Senior Developer', 'Asha Rao', 'Senior Developer', '2026-09-02', 8.00, 85.00, 85.00, 680.00, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0005', 'DEMO-EVT-0005'),
  (UNHEX('de090000000000000000000000000006'), UNHEX('de080000000000000000000000000002'), 'TIME_ENTRY', 'Timesheet entry - QA Engineer', 'Ben Chen', 'QA Engineer', '2026-09-03', 6.00, 55.00, 55.00, 330.00, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0006', 'DEMO-EVT-0006'),
  (UNHEX('de090000000000000000000000000007'), UNHEX('de080000000000000000000000000002'), 'TIME_ENTRY', 'Timesheet entry - Project Manager', 'Cara Diaz', 'Project Manager', '2026-09-04', 3.50, 95.00, 95.00, 332.50, 'HOURLY', 'USD', 1, 'TMS', 'APPROVED', 'DEMO-TS-0007', 'DEMO-EVT-0007');
INSERT INTO billing_acquisition (id, billing_configuration_id, project_id, billing_period_start, billing_period_end, status, trigger_mode, snapshot_id, acquired_at, created_at, updated_at) VALUES
  (UNHEX('de0c0000000000000000000000000001'), UNHEX('de060000000000000000000000000001'), 9001, '2026-08-01', '2026-08-31', 'READY', 'MANUAL', UNHEX('de080000000000000000000000000001'), '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000'),
  (UNHEX('de0c0000000000000000000000000002'), UNHEX('de060000000000000000000000000001'), 9001, '2026-09-01', '2026-09-30', 'READY', 'MANUAL', UNHEX('de080000000000000000000000000002'), '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000', '2026-10-01 09:00:00.000000');

COMMIT;

-- ---------------------------------------------------------------------------------------------------------------------
-- Done. Expected results of the real API calls (for checking the demo, computed with the application's rounding HALF_UP, scale 2):
--   BS-DEMO-000001: taxable 2137.50, 6% = 128.25, 2% = 42.75, total tax 171.00, grand total 2308.50
--   BS-DEMO-000002: taxable 1342.50, 6% =  80.55, 2% = 26.85, total tax 107.40, grand total 1449.90
-- ---------------------------------------------------------------------------------------------------------------------
