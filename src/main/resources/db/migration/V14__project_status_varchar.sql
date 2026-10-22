-- =========================================================
-- project_master_reference.project_status: native MySQL ENUM -> VARCHAR(30)
-- =========================================================
-- Why: the column was created by Hibernate (ddl-auto=update) as ENUM('ACTIVE','INACTIVE','COMPLETED','ON_HOLD','CANCELLED'),
-- which has drifted from both the Java enum and PMS. PMS (pms.projects.status) uses ACTIVE / ARCHIVED / PLANNING / COMPLETED;
-- PLANNING and ARCHIVED cannot be stored in the old ENUM ("Data truncated"), and the CDC mapper currently leaves such
-- projects with a NULL status. ddl-auto=update never alters an existing column, so the type must be changed by hand.
--
-- Effect: existing values are preserved as text (MySQL converts ENUM -> VARCHAR by value).
-- The column's own character set, collation, nullability, default and comment are DETECTED from information_schema and
-- re-applied in the ALTER, so they never silently fall back to the table default (MODIFY COLUMN without a COLLATE clause
-- would otherwise adopt the TABLE's default collation, which can differ from the column's).
-- The JPA mapping is @Enumerated(STRING) + @JdbcTypeCode(VARCHAR) + length 30, which reads/writes this column unchanged.
-- Scope: ONLY project_status. lifecycle_stage is a separate decision - see V15__project_lifecycle_stage_varchar.sql.
--
-- MySQL 8. NOTE: manual script - the application has no migration runner.
--
-- BEHAVIOUR (validated on MySQL 8.4.8; see the test report):
--   * column is an ENUM                    -> converted to VARCHAR(30); counts of rows / non-NULL statuses are verified afterwards.
--   * column is already VARCHAR(>=30)      -> no-op (safe to re-run; also the case for fresh databases created from the entity).
--   * database has no project_master_reference table, no project_status column, the column is VARCHAR shorter than 30,
--     or any other type                    -> FAILS with SQLSTATE 45000 and a clear message; nothing is changed.
--   The script never drops data or columns. If it fails after CREATE PROCEDURE, the helper procedure may remain; it is dropped
--   by the first statement of the next run, or manually:  DROP PROCEDURE IF EXISTS project_status_to_varchar_v14;
--
-- Run:  mysql --database=<ar_db> < V14__project_status_varchar.sql      (stops at the first error; do NOT use --force)
-- Required privileges on <ar_db>: ALTER (on the table), CREATE ROUTINE and ALTER ROUTINE (the latter is needed even for
-- DROP PROCEDURE IF EXISTS). SELECT on information_schema is implicit. EXECUTE is not needed (the creator receives it).
--
-- ---------------------------------------------------------
-- PRE-MIGRATION CHECKLIST (do before running)
-- ---------------------------------------------------------
--  1. Confirm the TARGET database:   SELECT DATABASE();   -- must be the AR database, not PMS/RMS/another environment.
--  2. STOP every AR instance that has this schema open (an ALTER takes a metadata lock; devtools restarts also run DDL).
--     The change is a full table copy (ALGORITHM=COPY; writes are blocked while it runs) - trivial for this table's size.
--  3. Backup:   mysqldump --single-transaction <ar_db> project_master_reference > project_master_reference_pre_V14.sql
--  4. Record the current definition and value distribution (save the output; the rollback uses the charset/collation):
--        SELECT column_type, is_nullable, column_default, character_set_name, collation_name, column_comment
--          FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'project_master_reference' AND column_name = 'project_status';
--        SELECT project_status, COUNT(*) FROM project_master_reference GROUP BY project_status;
--        SELECT table_collation FROM information_schema.tables
--         WHERE table_schema = DATABASE() AND table_name = 'project_master_reference';   -- informational only now
--  5. Confirm no value is outside the old ENUM or empty-string-coerced:  expect only ACTIVE / COMPLETED / ON_HOLD /
--     CANCELLED / INACTIVE / NULL.  (An empty string '' in an ENUM column means an invalid value was stored; investigate first.)
--  6. Confirm no index or FK uses project_status (checked 2026-10-22: none).
--  7. Confirm the account has the privileges listed above (an account without them fails with error 1044 / 1370 and the
--     column stays an ENUM).
--
-- ---------------------------------------------------------
-- POST-MIGRATION VERIFICATION
-- ---------------------------------------------------------
--        SELECT column_type, is_nullable, character_set_name, collation_name FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'project_master_reference' AND column_name = 'project_status';
--        -- expect: varchar(30) | same nullability, charset and collation as recorded in checklist step 4
--        SELECT project_status, COUNT(*) FROM project_master_reference GROUP BY project_status;
--        -- expect: identical counts to the pre-migration output
--
-- ---------------------------------------------------------
-- ROLLBACK
-- ---------------------------------------------------------
--  Preferred: restore project_master_reference from the step-3 backup.
--  Or revert in place (only valid while no row holds a value outside the ENUM list - otherwise MySQL strict mode rejects it
--  with "Data truncated" and the column stays VARCHAR). Use the SUPERSET list so PLANNING/ARCHIVED rows written after
--  migration still fit, and the charset/collation recorded in checklist step 4 (shown here for utf8mb4_0900_ai_ci):
--        ALTER TABLE project_master_reference MODIFY COLUMN project_status
--            ENUM('ACTIVE','INACTIVE','COMPLETED','ON_HOLD','CANCELLED','NOT_STARTED','IN_PROGRESS','DELAYED','PLANNING','ARCHIVED')
--            CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL;
-- =========================================================

DROP PROCEDURE IF EXISTS project_status_to_varchar_v14;

DELIMITER //
CREATE PROCEDURE project_status_to_varchar_v14()
BEGIN
    DECLARE v_db           VARCHAR(64);
    DECLARE v_table_count  INT DEFAULT 0;
    DECLARE v_col_count    INT DEFAULT 0;
    DECLARE v_data_type    VARCHAR(64);
    DECLARE v_max_len      BIGINT;
    DECLARE v_charset      VARCHAR(64);
    DECLARE v_collation    VARCHAR(64);
    DECLARE v_nullable     VARCHAR(3);
    DECLARE v_default      TEXT;
    DECLARE v_comment      TEXT;
    DECLARE v_rows_before  BIGINT DEFAULT 0;
    DECLARE v_vals_before  BIGINT DEFAULT 0;
    DECLARE v_rows_after   BIGINT DEFAULT 0;
    DECLARE v_vals_after   BIGINT DEFAULT 0;
    DECLARE v_type_after   VARCHAR(64);
    DECLARE v_msg          VARCHAR(255);

    SET v_db = DATABASE();

    -- 1. Wrong target: fail loudly instead of "succeeding" without changing anything.
    SELECT COUNT(*) INTO v_table_count
      FROM information_schema.tables
     WHERE table_schema = v_db AND table_name = 'project_master_reference';
    IF v_table_count = 0 THEN
        SET v_msg = CONCAT('V14 aborted: table project_master_reference not found in database ',
                           IFNULL(v_db, '(none)'), ' - wrong database selected? Nothing was changed.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- 2. Read the column's CURRENT definition (aggregates guarantee exactly one row even when the column is absent).
    SELECT COUNT(*), MAX(data_type), MAX(character_maximum_length), MAX(character_set_name), MAX(collation_name),
           MAX(is_nullable), MAX(column_default), MAX(column_comment)
      INTO v_col_count, v_data_type, v_max_len, v_charset, v_collation, v_nullable, v_default, v_comment
      FROM information_schema.columns
     WHERE table_schema = v_db AND table_name = 'project_master_reference' AND column_name = 'project_status';
    IF v_col_count = 0 THEN
        SET v_msg = CONCAT('V14 aborted: column project_master_reference.project_status not found in database ',
                           v_db, '. Nothing was changed.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_data_type = 'varchar' THEN
        -- Already converted (re-run, or a fresh database created from the entity mapping).
        IF v_max_len < 30 THEN
            SET v_msg = CONCAT('V14 aborted: project_status is VARCHAR(', v_max_len,
                               '), shorter than the required 30. Resolve manually; nothing was changed.');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    ELSEIF v_data_type = 'enum' THEN
        SELECT COUNT(*), COUNT(project_status) INTO v_rows_before, v_vals_before FROM project_master_reference;

        -- Preserve the column's own charset/collation/nullability/default/comment (never the table default).
        SET @v14_ddl = CONCAT(
            'ALTER TABLE `', v_db, '`.`project_master_reference` MODIFY COLUMN `project_status` VARCHAR(30)',
            ' CHARACTER SET ', v_charset, ' COLLATE ', v_collation,
            IF(v_nullable = 'NO', ' NOT NULL', ' NULL'),
            IF(v_default IS NULL, '', CONCAT(' DEFAULT ', QUOTE(v_default))),
            IF(v_comment IS NULL OR v_comment = '', '', CONCAT(' COMMENT ', QUOTE(v_comment))));
        PREPARE v14_stmt FROM @v14_ddl;
        EXECUTE v14_stmt;
        DEALLOCATE PREPARE v14_stmt;

        -- Post-conditions: still the same rows and the same number of non-NULL statuses, and the new type is in place.
        SELECT COUNT(*), COUNT(project_status) INTO v_rows_after, v_vals_after FROM project_master_reference;
        SELECT CONCAT(data_type, '(', character_maximum_length, ')') INTO v_type_after
          FROM information_schema.columns
         WHERE table_schema = v_db AND table_name = 'project_master_reference' AND column_name = 'project_status';
        IF v_rows_after <> v_rows_before OR v_vals_after <> v_vals_before OR v_type_after <> 'varchar(30)' THEN
            SET v_msg = CONCAT('V14 post-check FAILED: rows ', v_rows_before, '->', v_rows_after,
                               ', non-NULL statuses ', v_vals_before, '->', v_vals_after, ', type now ', IFNULL(v_type_after, '?'),
                               '. Restore from the backup.');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    ELSE
        SET v_msg = CONCAT('V14 aborted: project_status has unexpected type ', v_data_type,
                           ' (expected enum or varchar). Nothing was changed.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
END//
DELIMITER ;

CALL project_status_to_varchar_v14();
DROP PROCEDURE project_status_to_varchar_v14;
