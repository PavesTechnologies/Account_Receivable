-- =====================================================================
-- LOCAL-ONLY SCRIPT - delete billing configurations except one project
-- =====================================================================
-- Deletes every billing configuration whose project is NOT @keep_project_name, together with all rows that
-- belong to it, EXCEPT configurations that have an invoice (through any of their billing snapshots or billing
-- schedules): those are skipped and listed, untouched. Projects themselves (project_master_reference) are kept.
--
-- Intended for a LOCAL database only. Never run it against the shared (Aiven) database.
--
-- Safety:
--   * DRY RUN by default (@apply = 0): everything runs inside one transaction and is ROLLED BACK; you see the
--     decision per configuration and the number of rows each table would lose.
--   * @apply = 1 commits. Any error, or a failed safety check, rolls the whole run back - nothing partial.
--   * Refuses to run if no configuration belongs to @keep_project_name (e.g. a typo in the name).
--   * Refuses to run if anything that would be deleted is still referenced by an invoice.
--   * Take a backup first:  mysqldump --single-transaction <schema> > before_delete.sql
--
-- Run (mysql client, connected to the LOCAL schema):
--   mysql --host=127.0.0.1 --user=<user> -p <local_schema> < delete_billing_configurations_except_project.sql
-- Review the output, then set @apply = 1 below and run it again.
-- Dependency order derived from the schema Hibernate creates (FK constraints + plain UUID reference columns).
-- =====================================================================

SET @keep_project_name = 'Project TM AR';
SET @apply = 0;   -- 0 = dry run (rollback), 1 = delete (commit)

DROP PROCEDURE IF EXISTS ar_delete_configs_except_project;

DELIMITER //
CREATE PROCEDURE ar_delete_configs_except_project(IN keep_name VARCHAR(255), IN do_apply INT)
BEGIN
    DECLARE kept INT DEFAULT 0;
    DECLARE blocked INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    DROP TEMPORARY TABLE IF EXISTS t_cfg, t_skip, t_snap, t_sched, t_run, t_tax, t_cfgsnap, t_rate, t_plan, t_sub,
        t_invoiced_snap, t_invoiced_sched, t_result;
    CREATE TEMPORARY TABLE t_cfg (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_skip (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_snap (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_sched (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_run (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_tax (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_cfgsnap (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_rate (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_plan (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_sub (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_invoiced_snap (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_invoiced_sched (id BINARY(16) PRIMARY KEY);
    CREATE TEMPORARY TABLE t_result (step INT AUTO_INCREMENT PRIMARY KEY, table_name VARCHAR(64), rows_deleted BIGINT);

    START TRANSACTION;

    -- ---- guard: the project to keep must exist and own at least one configuration
    SELECT COUNT(*) INTO kept
    FROM billing_configuration bc
    JOIN project_master_reference p ON p.pms_project_id = bc.project_id
    WHERE p.project_name = keep_name;
    IF kept = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No billing configuration belongs to the project to keep - check @keep_project_name. Nothing was changed.';
    END IF;

    -- ---- configurations with an invoice (via snapshot or schedule) are skipped
    INSERT IGNORE INTO t_invoiced_snap SELECT s.billing_configuration_id FROM billing_snapshot s
        JOIN invoice i ON i.billing_snapshot_id = s.billing_snapshot_id;
    INSERT IGNORE INTO t_invoiced_sched SELECT bs.billing_configuration_id FROM billing_schedule bs
        JOIN invoice i ON i.billing_schedule_id = bs.billing_schedule_id;

    INSERT INTO t_skip
    SELECT bc.billing_configuration_id
    FROM billing_configuration bc
    LEFT JOIN project_master_reference p ON p.pms_project_id = bc.project_id
    WHERE NOT (COALESCE(p.project_name, '') <=> keep_name)
      AND (bc.billing_configuration_id IN (SELECT id FROM t_invoiced_snap)
           OR bc.billing_configuration_id IN (SELECT id FROM t_invoiced_sched));

    INSERT INTO t_cfg
    SELECT bc.billing_configuration_id
    FROM billing_configuration bc
    LEFT JOIN project_master_reference p ON p.pms_project_id = bc.project_id
    WHERE NOT (COALESCE(p.project_name, '') <=> keep_name)
      AND bc.billing_configuration_id NOT IN (SELECT id FROM t_skip);

    -- ---- decision per configuration
    SELECT bc.project_id, p.project_name, BIN_TO_UUID(bc.billing_configuration_id) AS billing_configuration_id,
           bc.approval_status,
           CASE WHEN bc.billing_configuration_id IN (SELECT id FROM t_cfg) THEN 'DELETE'
                WHEN bc.billing_configuration_id IN (SELECT id FROM t_skip) THEN 'SKIP (has invoice)'
                ELSE 'KEEP' END AS decision
    FROM billing_configuration bc
    LEFT JOIN project_master_reference p ON p.pms_project_id = bc.project_id
    ORDER BY decision, bc.project_id;

    -- ---- everything that belongs to the configurations being deleted
    INSERT IGNORE INTO t_snap SELECT billing_snapshot_id FROM billing_snapshot WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT IGNORE INTO t_sched SELECT billing_schedule_id FROM billing_schedule WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT IGNORE INTO t_run SELECT automatic_billing_run_id FROM automatic_billing_run WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT IGNORE INTO t_run SELECT automatic_billing_run_id FROM automatic_billing_run WHERE billing_snapshot_id IN (SELECT id FROM t_snap);
    INSERT IGNORE INTO t_run SELECT automatic_billing_run_id FROM automatic_billing_run WHERE billing_schedule_id IN (SELECT id FROM t_sched);
    INSERT IGNORE INTO t_tax SELECT tax_calculation_id FROM tax_calculation WHERE billing_snapshot_id IN (SELECT id FROM t_snap);
    INSERT IGNORE INTO t_tax SELECT tax_calculation_id FROM tax_calculation WHERE billing_schedule_id IN (SELECT id FROM t_sched);
    INSERT IGNORE INTO t_tax SELECT tax_calculation_id FROM tax_calculation WHERE superseded_billing_schedule_id IN (SELECT id FROM t_sched);
    INSERT IGNORE INTO t_cfgsnap SELECT snapshot_id FROM billing_configuration_snapshot WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT IGNORE INTO t_rate SELECT rate_card_id FROM billing_tm_rate_card WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT IGNORE INTO t_plan SELECT milestone_plan_id FROM billing_milestone_plan WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT IGNORE INTO t_sub SELECT subscription_configuration_id FROM billing_subscription_configuration WHERE billing_configuration_id IN (SELECT id FROM t_cfg);

    -- ---- guard: nothing being deleted may still be referenced by an invoice or by a kept configuration
    SELECT COUNT(*) INTO blocked FROM invoice
    WHERE billing_snapshot_id IN (SELECT id FROM t_snap);
    IF blocked = 0 THEN
        SELECT COUNT(*) INTO blocked FROM invoice WHERE billing_schedule_id IN (SELECT id FROM t_sched);
    END IF;
    IF blocked = 0 THEN
        SELECT COUNT(*) INTO blocked FROM invoice WHERE tax_calculation_id IN (SELECT id FROM t_tax);
    END IF;
    IF blocked = 0 THEN
        SELECT COUNT(*) INTO blocked FROM billing_subscription_configuration
        WHERE renewed_from_id IN (SELECT id FROM t_sub)
          AND billing_configuration_id NOT IN (SELECT id FROM t_cfg);
    END IF;
    IF blocked > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A row to be deleted is still referenced by an invoice or a kept configuration. Nothing was changed.';
    END IF;

    -- ---- delete, children first
    DELETE FROM automatic_billing_run_event WHERE run_id IN (SELECT id FROM t_run);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('automatic_billing_run_event', ROW_COUNT());
    DELETE FROM automatic_billing_run WHERE automatic_billing_run_id IN (SELECT id FROM t_run);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('automatic_billing_run', ROW_COUNT());

    DELETE FROM tax_calculation_component WHERE tax_calculation_id IN (SELECT id FROM t_tax);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('tax_calculation_component', ROW_COUNT());
    DELETE FROM tax_calculation WHERE tax_calculation_id IN (SELECT id FROM t_tax);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('tax_calculation', ROW_COUNT());

    DELETE FROM billing_snapshot_item WHERE billing_snapshot_id IN (SELECT id FROM t_snap);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_snapshot_item (incl. timesheet/expense claims)', ROW_COUNT());
    DELETE FROM software_billing_history WHERE billing_snapshot_id IN (SELECT id FROM t_snap);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('software_billing_history', ROW_COUNT());
    DELETE FROM billing_acquisition WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_acquisition', ROW_COUNT());
    DELETE FROM billing_acquisition WHERE snapshot_id IN (SELECT id FROM t_snap);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_acquisition (by snapshot)', ROW_COUNT());
    DELETE FROM billing_snapshot WHERE billing_snapshot_id IN (SELECT id FROM t_snap);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_snapshot', ROW_COUNT());

    DELETE FROM billing_schedule WHERE billing_schedule_id IN (SELECT id FROM t_sched);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_schedule', ROW_COUNT());

    DELETE FROM billing_configuration_change_detail WHERE snapshot_id IN (SELECT id FROM t_cfgsnap);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_configuration_change_detail', ROW_COUNT());
    DELETE FROM billing_configuration_snapshot WHERE snapshot_id IN (SELECT id FROM t_cfgsnap);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_configuration_snapshot', ROW_COUNT());

    DELETE FROM billing_tm_rate_discount WHERE rate_card_id IN (SELECT id FROM t_rate);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_tm_rate_discount', ROW_COUNT());
    DELETE FROM billing_tm_rate_card WHERE rate_card_id IN (SELECT id FROM t_rate);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_tm_rate_card', ROW_COUNT());

    DELETE FROM billing_payment_entry WHERE milestone_plan_id IN (SELECT id FROM t_plan);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_payment_entry', ROW_COUNT());
    DELETE FROM billing_milestone_plan WHERE milestone_plan_id IN (SELECT id FROM t_plan);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_milestone_plan', ROW_COUNT());

    UPDATE billing_subscription_configuration SET renewed_from_id = NULL
    WHERE subscription_configuration_id IN (SELECT id FROM t_sub);
    DELETE FROM billing_subscription_configuration WHERE subscription_configuration_id IN (SELECT id FROM t_sub);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_subscription_configuration', ROW_COUNT());

    DELETE FROM billing_fixed_price_configuration WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_fixed_price_configuration', ROW_COUNT());

    DELETE FROM billing_configuration WHERE billing_configuration_id IN (SELECT id FROM t_cfg);
    INSERT INTO t_result (table_name, rows_deleted) VALUES ('billing_configuration', ROW_COUNT());

    SELECT table_name, rows_deleted FROM t_result ORDER BY step;

    IF do_apply = 1 THEN
        COMMIT;
        SELECT 'COMMITTED - the rows above were deleted.' AS outcome;
    ELSE
        ROLLBACK;
        SELECT 'DRY RUN - rolled back, nothing was deleted. Set @apply = 1 to delete.' AS outcome;
    END IF;

    DROP TEMPORARY TABLE IF EXISTS t_cfg, t_skip, t_snap, t_sched, t_run, t_tax, t_cfgsnap, t_rate, t_plan, t_sub,
        t_invoiced_snap, t_invoiced_sched, t_result;
END //
DELIMITER ;

CALL ar_delete_configs_except_project(@keep_project_name, @apply);
DROP PROCEDURE IF EXISTS ar_delete_configs_except_project;
