-- =========================================================
-- Milestone billing support                                              (manual script - NOT applied automatically)
--   1. billing_milestone_plan.payment_structure : native MySQL ENUM('FULL_PAYMENT','INSTALLMENTS') -> VARCHAR(30)
--   2. billing_payment_entry.pms_milestone_id   : new nullable BIGINT column + index idx_bpe_pms_milestone
-- =========================================================
-- Why
--   1. Hibernate (ddl-auto=update) created payment_structure as ENUM('FULL_PAYMENT','INSTALLMENTS'). ddl-auto never alters an
--      existing column, so the new payment structure MILESTONES cannot be stored ("Data truncated") until the type is changed
--      by hand - the same situation as V14 (project_status). The JPA mapping now uses @JdbcTypeCode(VARCHAR), which creates
--      VARCHAR(30) on fresh databases and reads/writes the converted column unchanged.
--   2. A MILESTONES plan entry records which PMS milestone it bills. The value is the PMS milestone id, a LOGICAL reference to
--      project_milestone_reference.pms_milestone_id. There is deliberately NO foreign key: that table is maintained by CDC,
--      its rows are tombstoned (source_deleted) rather than removed, and a FK would make CDC and plan edits fail each other.
--      The column is NULL for every FULL_PAYMENT / INSTALLMENTS entry and for all existing rows.
--      The index serves the "is this milestone already claimed by another plan" lookup done while saving a plan.
--
-- Effect on existing data
--   * Existing payment_structure values (FULL_PAYMENT / INSTALLMENTS) are preserved as text; row counts and the count of each
--     value are verified before and after. The column keeps its own charset, collation, nullability, default and comment
--     (detected from information_schema and re-applied, as in V14).
--   * Existing billing_payment_entry rows get pms_milestone_id = NULL. Nothing else in either table changes.
--   * Adding a nullable BIGINT column is a metadata-only (INSTANT) change on MySQL 8.0.12 and later (an in-place change on
--     older releases); the index is built in place. The
--     ENUM -> VARCHAR conversion copies billing_milestone_plan (one row per Milestone Plan configuration - tiny) and blocks
--     writes to that table while it runs.
--
-- MySQL 8.  NOTE: manual script - the application has no migration runner.
--
-- BEHAVIOUR  (RE-RUNNABLE: every step checks the current state first; running it twice changes nothing the second time)
--   * payment_structure is an ENUM            -> converted to VARCHAR(30) (counts verified afterwards)
--   * payment_structure is already VARCHAR>=30 -> no-op (re-run, or a database created from the entity mapping)
--   * pms_milestone_id missing                -> added;  index missing -> created
--   * pms_milestone_id exists as nullable BIGINT and the index exists -> no-op. This is also what you get when the new application
--     version has already started once against this database: ddl-auto=update adds the column and index by itself, but it can
--     NEVER convert payment_structure, so step 1 is still required.
--   * a wrong database (tables missing), a column of an unexpected type or length, or an index on the wrong column
--                                             -> FAILS with SQLSTATE 45000 and a message; the failing step changes nothing.
--   EXCLUSIVE EXECUTION REQUIRED: run it from ONE session at a time, with every AR instance stopped. The script takes no lock
--   of its own, and two concurrent runs can drop each other's helper procedure or fail with "Duplicate column name".
--   DDL commits implicitly in MySQL, so the two parts are not one transaction. If part 2 fails after part 1 succeeded, fix the
--   cause and simply run the script again.  The script never drops data or columns.  If it fails after CREATE PROCEDURE the
--   helper procedure may remain; it is dropped by the first statement of the next run, or manually:
--        DROP PROCEDURE IF EXISTS milestone_billing_support_v16;
--
-- Run:  mysql --database=<ar_db> < V16__milestone_billing_support.sql      (stops at the first error; do NOT use --force)
-- Required privileges on <ar_db>: SELECT, ALTER and INDEX on billing_milestone_plan and billing_payment_entry, plus
-- CREATE ROUTINE and ALTER ROUTINE (the latter is needed even for DROP PROCEDURE IF EXISTS). INSERT/UPDATE are not needed.
-- CAUTION: an account WITHOUT SELECT does not get a privilege error - information_schema hides the objects from it, so the script
-- aborts safely with a misleading "not found" message. Check the account's privileges before concluding the wrong database
-- was selected.
--
-- ---------------------------------------------------------
-- ORDER OF ROLL-OUT  (important)
-- ---------------------------------------------------------
--   Apply V16 BEFORE the application version that contains the MILESTONES code starts serving milestone plans.
--   * The previous application version keeps working after V16 for FULL_PAYMENT / INSTALLMENTS plans (values are unchanged
--     text; it ignores the new column).
--   * The previous version cannot READ a plan whose payment_structure is MILESTONES (its enum does not know the value). Such
--     plans can only be created by the new version, so avoid creating any until every instance runs the new version - or
--     deactivate them (billing_milestone_plan.is_active = 0) before rolling the application back.
--   * The new version started BEFORE V16 on an existing database works for existing plans, but the first attempt to save a
--     MILESTONES plan fails with "Data truncated for column 'payment_structure'" until part 1 has been applied.
--
-- ---------------------------------------------------------
-- PRE-MIGRATION CHECKLIST (do before running)
-- ---------------------------------------------------------
--  1. Confirm the TARGET database:   SELECT DATABASE();   -- must be the intended AR database (not PMS/RMS or another environment)
--  2. STOP every AR instance that has this schema open (the ALTERs take metadata locks; devtools restarts also run DDL).
--  3. Backup:   mysqldump --single-transaction <ar_db> billing_milestone_plan billing_payment_entry > pre_V16.sql
--  4. Record the current state (save the output):
--        SELECT column_type, is_nullable, column_default, character_set_name, collation_name, column_comment
--          FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'billing_milestone_plan' AND column_name = 'payment_structure';
--        SELECT payment_structure, COUNT(*) FROM billing_milestone_plan GROUP BY payment_structure;
--        SELECT COUNT(*) FROM billing_payment_entry;
--        SELECT table_name, engine FROM information_schema.tables
--         WHERE table_schema = DATABASE() AND table_name IN ('billing_milestone_plan','billing_payment_entry');   -- expect InnoDB
--  5. Confirm no index or foreign key uses payment_structure (none is declared by the entity mapping):
--        SELECT index_name FROM information_schema.statistics
--         WHERE table_schema = DATABASE() AND table_name = 'billing_milestone_plan' AND column_name = 'payment_structure';
--  6. Confirm the account has the privileges listed above.
--
-- ---------------------------------------------------------
-- POST-MIGRATION VERIFICATION
-- ---------------------------------------------------------
--        SELECT column_type, is_nullable, character_set_name, collation_name FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'billing_milestone_plan' AND column_name = 'payment_structure';
--        -- expect: varchar(30) | same nullability, charset and collation as recorded in checklist step 4
--        SELECT payment_structure, COUNT(*) FROM billing_milestone_plan GROUP BY payment_structure;      -- identical to step 4
--        SELECT column_type, is_nullable FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'billing_payment_entry' AND column_name = 'pms_milestone_id';
--        -- expect: bigint | YES
--        SELECT index_name, seq_in_index, column_name, non_unique FROM information_schema.statistics
--         WHERE table_schema = DATABASE() AND table_name = 'billing_payment_entry' AND index_name = 'idx_bpe_pms_milestone';
--        -- expect one row: seq 1, pms_milestone_id, non_unique 1
--        SELECT COUNT(*), COUNT(pms_milestone_id) FROM billing_payment_entry;
--        -- expect: the same total as step 4; the second number is 0 only if no MILESTONES plan has been saved since the migration
--
-- ---------------------------------------------------------
-- ROLLBACK
-- ---------------------------------------------------------
--  Preferred: restore the two tables from the step-3 backup (with every AR instance stopped).
--  Or revert in place (data in the new column is lost; MILESTONES plans must be removed or moved first):
--        -- 1. only valid while no row holds MILESTONES (otherwise strict mode rejects it with "Data truncated"):
--        --      SELECT COUNT(*) FROM billing_milestone_plan WHERE payment_structure = 'MILESTONES';   -- must be 0
--        ALTER TABLE billing_milestone_plan MODIFY COLUMN payment_structure ENUM('FULL_PAYMENT','INSTALLMENTS') NOT NULL;
--          -- (re-use the charset/collation recorded in step 4, e.g.  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci)
--        -- 2. optional - the column and index are harmless to the previous application version, so leaving them is also fine:
--        ALTER TABLE billing_payment_entry DROP INDEX idx_bpe_pms_milestone, DROP COLUMN pms_milestone_id;
-- =========================================================

DROP PROCEDURE IF EXISTS milestone_billing_support_v16;

DELIMITER //
CREATE PROCEDURE milestone_billing_support_v16()
BEGIN
    DECLARE v_db            VARCHAR(64);
    DECLARE v_count         INT DEFAULT 0;
    DECLARE v_data_type     VARCHAR(64);
    DECLARE v_max_len       BIGINT;
    DECLARE v_charset       VARCHAR(64);
    DECLARE v_collation     VARCHAR(64);
    DECLARE v_nullable      VARCHAR(3);
    DECLARE v_default       TEXT;
    DECLARE v_comment       TEXT;
    DECLARE v_rows_before   BIGINT DEFAULT 0;
    DECLARE v_vals_before   BIGINT DEFAULT 0;
    DECLARE v_full_before   BIGINT DEFAULT 0;
    DECLARE v_inst_before   BIGINT DEFAULT 0;
    DECLARE v_rows_after    BIGINT DEFAULT 0;
    DECLARE v_vals_after    BIGINT DEFAULT 0;
    DECLARE v_full_after    BIGINT DEFAULT 0;
    DECLARE v_inst_after    BIGINT DEFAULT 0;
    DECLARE v_type_after    VARCHAR(64);
    DECLARE v_idx_cols      INT DEFAULT 0;
    DECLARE v_idx_first_col VARCHAR(64);
    DECLARE v_msg           VARCHAR(255);

    SET v_db = DATABASE();

    -- =====================================================================================================================
    -- PART 1: billing_milestone_plan.payment_structure  ENUM -> VARCHAR(30)
    -- =====================================================================================================================
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
     WHERE table_schema = v_db AND table_name = 'billing_milestone_plan';
    IF v_count = 0 THEN
        SET v_msg = LEFT(CONCAT('V16 aborted: table billing_milestone_plan not found in database ', IFNULL(v_db, '(none)'),
                                ' - wrong database selected? Nothing was changed.'), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- Current definition (aggregates guarantee exactly one row even when the column is absent).
    SELECT COUNT(*), MAX(data_type), MAX(character_maximum_length), MAX(character_set_name), MAX(collation_name),
           MAX(is_nullable), MAX(column_default), MAX(column_comment)
      INTO v_count, v_data_type, v_max_len, v_charset, v_collation, v_nullable, v_default, v_comment
      FROM information_schema.columns
     WHERE table_schema = v_db AND table_name = 'billing_milestone_plan' AND column_name = 'payment_structure';
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16 aborted: column billing_milestone_plan.payment_structure not found. Nothing was changed.';
    END IF;

    IF v_data_type = 'varchar' THEN
        -- Already converted (re-run, or a database created from the entity mapping).
        IF v_max_len < 30 THEN
            SET v_msg = LEFT(CONCAT('V16 aborted: payment_structure is VARCHAR(', v_max_len,
                                    '), shorter than 30. Resolve manually; nothing was changed.'), 128);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    ELSEIF v_data_type = 'enum' THEN
        SELECT COUNT(*), COUNT(payment_structure),
               IFNULL(SUM(payment_structure = 'FULL_PAYMENT'), 0), IFNULL(SUM(payment_structure = 'INSTALLMENTS'), 0)
          INTO v_rows_before, v_vals_before, v_full_before, v_inst_before
          FROM billing_milestone_plan;

        -- Preserve the column's own charset/collation/nullability/default/comment (never the table default).
        SET @v16_ddl = CONCAT(
            'ALTER TABLE `', v_db, '`.`billing_milestone_plan` MODIFY COLUMN `payment_structure` VARCHAR(30)',
            ' CHARACTER SET ', v_charset, ' COLLATE ', v_collation,
            IF(v_nullable = 'NO', ' NOT NULL', ' NULL'),
            IF(v_default IS NULL, '', CONCAT(' DEFAULT ', QUOTE(v_default))),
            IF(v_comment IS NULL OR v_comment = '', '', CONCAT(' COMMENT ', QUOTE(v_comment))));
        PREPARE v16_stmt FROM @v16_ddl;
        EXECUTE v16_stmt;
        DEALLOCATE PREPARE v16_stmt;

        -- Post-conditions: same rows, same number of values, same count of each known value, and the new type is in place.
        SELECT COUNT(*), COUNT(payment_structure),
               IFNULL(SUM(payment_structure = 'FULL_PAYMENT'), 0), IFNULL(SUM(payment_structure = 'INSTALLMENTS'), 0)
          INTO v_rows_after, v_vals_after, v_full_after, v_inst_after
          FROM billing_milestone_plan;
        SELECT CONCAT(data_type, '(', character_maximum_length, ')') INTO v_type_after
          FROM information_schema.columns
         WHERE table_schema = v_db AND table_name = 'billing_milestone_plan' AND column_name = 'payment_structure';
        IF v_rows_after <> v_rows_before OR v_vals_after <> v_vals_before
           OR v_full_after <> v_full_before OR v_inst_after <> v_inst_before OR v_type_after <> 'varchar(30)' THEN
            SET v_msg = LEFT(CONCAT('V16 part 1 post-check FAILED: rows ', v_rows_before, '->', v_rows_after,
                                    ', values ', v_vals_before, '->', v_vals_after, ', type ', IFNULL(v_type_after, '?'),
                                    '. Restore from the backup.'), 128);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    ELSE
        SET v_msg = LEFT(CONCAT('V16 aborted: payment_structure has unexpected type ', v_data_type,
                                ' (expected enum or varchar). Nothing was changed.'), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- =====================================================================================================================
    -- PART 2: billing_payment_entry.pms_milestone_id  BIGINT NULL  + index idx_bpe_pms_milestone
    -- =====================================================================================================================
    SELECT COUNT(*) INTO v_count
      FROM information_schema.tables
     WHERE table_schema = v_db AND table_name = 'billing_payment_entry';
    IF v_count = 0 THEN
        SET v_msg = LEFT(CONCAT('V16 aborted: table billing_payment_entry not found in database ', IFNULL(v_db, '(none)'),
                                '. Part 1 was applied; fix and re-run.'), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT COUNT(*), MAX(data_type), MAX(is_nullable)
      INTO v_count, v_data_type, v_nullable
      FROM information_schema.columns
     WHERE table_schema = v_db AND table_name = 'billing_payment_entry' AND column_name = 'pms_milestone_id';
    IF v_count = 0 THEN
        -- Nullable, no default: existing rows get NULL. Metadata-only (INSTANT) on MySQL 8.
        ALTER TABLE billing_payment_entry ADD COLUMN pms_milestone_id BIGINT NULL;
    ELSEIF v_data_type <> 'bigint' OR v_nullable <> 'YES' THEN
        SET v_msg = LEFT(CONCAT('V16 aborted: pms_milestone_id exists as ', v_data_type, ' nullable=', v_nullable,
                                ' (expected bigint, nullable). Resolve manually.'), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT COUNT(*), MAX(IF(seq_in_index = 1, column_name, NULL))
      INTO v_idx_cols, v_idx_first_col
      FROM information_schema.statistics
     WHERE table_schema = v_db AND table_name = 'billing_payment_entry' AND index_name = 'idx_bpe_pms_milestone';
    IF v_idx_cols = 0 THEN
        CREATE INDEX idx_bpe_pms_milestone ON billing_payment_entry (pms_milestone_id);
    ELSEIF v_idx_cols <> 1 OR v_idx_first_col <> 'pms_milestone_id' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16 aborted: index idx_bpe_pms_milestone exists but is not on pms_milestone_id alone. Resolve manually.';
    END IF;

    -- Post-conditions of part 2.
    SELECT COUNT(*) INTO v_count
      FROM information_schema.columns
     WHERE table_schema = v_db AND table_name = 'billing_payment_entry' AND column_name = 'pms_milestone_id'
       AND data_type = 'bigint' AND is_nullable = 'YES';
    SELECT COUNT(*) INTO v_idx_cols
      FROM information_schema.statistics
     WHERE table_schema = v_db AND table_name = 'billing_payment_entry' AND index_name = 'idx_bpe_pms_milestone'
       AND column_name = 'pms_milestone_id' AND seq_in_index = 1;
    IF v_count <> 1 OR v_idx_cols <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V16 part 2 post-check FAILED: pms_milestone_id column or index not in the expected state.';
    END IF;
END//
DELIMITER ;

CALL milestone_billing_support_v16();
DROP PROCEDURE milestone_billing_support_v16;
