-- =========================================================
-- cdc_inbox.status: native MySQL ENUM -> VARCHAR(20)
-- =========================================================
-- Why: the column was created by Hibernate (ddl-auto=update) as ENUM('PENDING','PROCESSED','FAILED'). The Java enum
-- CdcInbox.ProcessingStatus now has a fourth constant, DEAD_LETTER, for an event that has used up its retries.
-- ddl-auto=update never alters an existing column, so on any database created before that change MySQL rejects the new
-- value with "Data truncated for column 'status'".
--
-- That failure is not cosmetic. DEAD_LETTER is written by CdcInboxService.markAsFailed, which InboxEventProcessor calls
-- from the catch block that handles a failed event; if the write throws, the exception escapes processSingleEvent and
-- aborts the whole sweep, so the remaining pending events in that run are not processed either. Run this before
-- deploying the change.
--
-- Effect: existing values are preserved as text (MySQL converts ENUM -> VARCHAR by value). The column's own character
-- set, collation, nullability, default and comment are DETECTED from information_schema and re-applied in the ALTER, so
-- they never silently fall back to the table default (MODIFY COLUMN without a COLLATE clause would otherwise adopt the
-- TABLE's default collation, which can differ from the column's).
-- The JPA mapping is @Enumerated(STRING) + @JdbcTypeCode(VARCHAR) + length 20, which reads/writes this column unchanged.
--
-- MySQL 8. NOTE: manual script - the application has no migration runner (spring.flyway.enabled defaults to false).
--
-- BEHAVIOUR (same shape as V14__project_status_varchar.sql):
--   * column is an ENUM                    -> converted to VARCHAR(20); row counts are verified afterwards.
--   * column is already VARCHAR(>=20)      -> no-op (safe to re-run; also the case for fresh databases created from the entity).
--   * database has no cdc_inbox table, no status column, the column is VARCHAR shorter than 20, or any other type
--                                          -> FAILS with SQLSTATE 45000 and a clear message; nothing is changed.
--   The script never drops data or columns. If it fails after CREATE PROCEDURE, the helper procedure may remain; it is
--   dropped by the first statement of the next run, or manually:  DROP PROCEDURE IF EXISTS cdc_inbox_status_to_varchar_v18;
--
-- Run:  mysql --database=<ar_db> < V18__cdc_inbox_status_varchar.sql      (stops at the first error; do NOT use --force)
-- Required privileges on <ar_db>: SELECT and ALTER on cdc_inbox, CREATE ROUTINE and ALTER ROUTINE.
-- CAUTION: an account WITHOUT SELECT does not get a privilege error - information_schema hides the column from it, so
-- the script aborts (safely) with the misleading message "column cdc_inbox.status not found". Check privileges before
-- concluding that the wrong database was selected.
--
-- ---------------------------------------------------------
-- PRE-MIGRATION CHECKLIST (do before running)
-- ---------------------------------------------------------
--  1. Confirm the TARGET database:   SELECT DATABASE();   -- must be the AR database, not PMS/RMS/another environment.
--  2. STOP every AR instance that has this schema open (an ALTER takes a metadata lock, and a running instance polls
--     cdc_inbox every 5 seconds).
--  3. Backup:   mysqldump --single-transaction <ar_db> cdc_inbox > cdc_inbox_pre_V18.sql
--  4. Record the current definition and value distribution (save the output; the rollback uses the charset/collation):
--        SELECT column_type, is_nullable, column_default, character_set_name, collation_name, column_comment
--          FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'cdc_inbox' AND column_name = 'status';
--        SELECT status, COUNT(*) FROM cdc_inbox GROUP BY status;
--  5. Confirm no index uses status  (an index on it is preserved by MODIFY COLUMN, but record it anyway).
--
-- ---------------------------------------------------------
-- POST-MIGRATION VERIFICATION
-- ---------------------------------------------------------
--        SELECT column_type, is_nullable, character_set_name, collation_name FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'cdc_inbox' AND column_name = 'status';
--        -- expect: varchar(20) | same nullability, charset and collation as recorded in checklist step 4
--        SELECT status, COUNT(*) FROM cdc_inbox GROUP BY status;
--        -- expect: identical counts to the pre-migration output
--
-- ---------------------------------------------------------
-- AFTER DEPLOYING THE APPLICATION CHANGE
-- ---------------------------------------------------------
--  Events abandoned BEFORE this change are still FAILED with retry_count >= 3: they predate DEAD_LETTER, so the
--  monitor does not count them and the retry sweep does not select them. Promote them so they are reported and can be
--  replayed through POST /api/v1/operations/cdc-inbox/dead-letters/replay:
--        SELECT COUNT(*) FROM cdc_inbox WHERE status = 'FAILED' AND retry_count >= 3;   -- review first
--        UPDATE cdc_inbox SET status = 'DEAD_LETTER' WHERE status = 'FAILED' AND retry_count >= 3;
--
-- ---------------------------------------------------------
-- ROLLBACK
-- ---------------------------------------------------------
--  Preferred: restore cdc_inbox from the step-3 backup.
--  Or revert in place (only valid while no row holds DEAD_LETTER - otherwise MySQL strict mode rejects it with
--  "Data truncated" and the column stays VARCHAR). Use the charset/collation recorded in checklist step 4
--  (shown here for utf8mb4_0900_ai_ci):
--        UPDATE cdc_inbox SET status = 'FAILED' WHERE status = 'DEAD_LETTER';
--        ALTER TABLE cdc_inbox MODIFY COLUMN status
--            ENUM('PENDING','PROCESSED','FAILED')
--            CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;
-- =========================================================

DROP PROCEDURE IF EXISTS cdc_inbox_status_to_varchar_v18;

DELIMITER //
CREATE PROCEDURE cdc_inbox_status_to_varchar_v18()
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
     WHERE table_schema = v_db AND table_name = 'cdc_inbox';
    IF v_table_count = 0 THEN
        SET v_msg = CONCAT('V18 aborted: table cdc_inbox not found in database ',
                           IFNULL(v_db, '(none)'), ' - wrong database selected? Nothing was changed.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- 2. Read the column's CURRENT definition (aggregates guarantee exactly one row even when the column is absent).
    SELECT COUNT(*), MAX(data_type), MAX(character_maximum_length), MAX(character_set_name), MAX(collation_name),
           MAX(is_nullable), MAX(column_default), MAX(column_comment)
      INTO v_col_count, v_data_type, v_max_len, v_charset, v_collation, v_nullable, v_default, v_comment
      FROM information_schema.columns
     WHERE table_schema = v_db AND table_name = 'cdc_inbox' AND column_name = 'status';
    IF v_col_count = 0 THEN
        SET v_msg = CONCAT('V18 aborted: column cdc_inbox.status not found in database ',
                           v_db, '. Nothing was changed.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_data_type = 'varchar' THEN
        -- Already converted (re-run, or a fresh database created from the entity mapping).
        IF v_max_len < 20 THEN
            SET v_msg = CONCAT('V18 aborted: cdc_inbox.status is VARCHAR(', v_max_len,
                               '), shorter than the required 20. Resolve manually; nothing was changed.');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    ELSEIF v_data_type = 'enum' THEN
        SELECT COUNT(*), COUNT(status) INTO v_rows_before, v_vals_before FROM cdc_inbox;

        -- Preserve the column's own charset/collation/nullability/default/comment (never the table default).
        SET @v18_ddl = CONCAT(
            'ALTER TABLE `', v_db, '`.`cdc_inbox` MODIFY COLUMN `status` VARCHAR(20)',
            ' CHARACTER SET ', v_charset, ' COLLATE ', v_collation,
            IF(v_nullable = 'NO', ' NOT NULL', ' NULL'),
            IF(v_default IS NULL, '', CONCAT(' DEFAULT ', QUOTE(v_default))),
            IF(v_comment IS NULL OR v_comment = '', '', CONCAT(' COMMENT ', QUOTE(v_comment))));
        PREPARE v18_stmt FROM @v18_ddl;
        EXECUTE v18_stmt;
        DEALLOCATE PREPARE v18_stmt;

        -- Post-conditions: still the same rows and the same number of non-NULL statuses, and the new type is in place.
        SELECT COUNT(*), COUNT(status) INTO v_rows_after, v_vals_after FROM cdc_inbox;
        SELECT CONCAT(data_type, '(', character_maximum_length, ')') INTO v_type_after
          FROM information_schema.columns
         WHERE table_schema = v_db AND table_name = 'cdc_inbox' AND column_name = 'status';
        IF v_rows_after <> v_rows_before OR v_vals_after <> v_vals_before OR v_type_after <> 'varchar(20)' THEN
            SET v_msg = CONCAT('V18 post-check FAILED: rows ', v_rows_before, '->', v_rows_after,
                               ', non-NULL statuses ', v_vals_before, '->', v_vals_after,
                               ', type now ', IFNULL(v_type_after, '?'), '. Restore from the backup.');
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;
    ELSE
        SET v_msg = CONCAT('V18 aborted: cdc_inbox.status has unexpected type ', v_data_type,
                           ' (expected enum or varchar). Nothing was changed.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
END//
DELIMITER ;

CALL cdc_inbox_status_to_varchar_v18();
DROP PROCEDURE cdc_inbox_status_to_varchar_v18;
