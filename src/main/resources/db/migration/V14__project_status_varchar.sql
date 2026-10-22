-- =========================================================
-- project_master_reference.project_status: native MySQL ENUM -> VARCHAR(30)
-- =========================================================
-- Why: the column was created by Hibernate (ddl-auto=update) as ENUM('ACTIVE','INACTIVE','COMPLETED','ON_HOLD','CANCELLED'),
-- which has drifted from both the Java enum and PMS. PMS (pms.projects.status) uses ACTIVE / ARCHIVED / PLANNING / COMPLETED;
-- PLANNING and ARCHIVED cannot be stored in the old ENUM ("Data truncated"), and the CDC mapper currently leaves such
-- projects with a NULL status. ddl-auto=update never alters an existing column, so the type must be changed by hand.
--
-- Effect: existing values are preserved as text (MySQL converts ENUM -> VARCHAR by value). Nullability stays NULL, no default.
-- The JPA mapping is @Enumerated(STRING) + @JdbcTypeCode(VARCHAR) + length 30, which reads/writes this column unchanged.
-- Scope: ONLY project_status. lifecycle_stage is a separate decision - see V15__project_lifecycle_stage_varchar.sql.
--
-- MySQL 8. NOTE: manual script - the application has no migration runner.
-- IDEMPOTENT: the column is altered only while it is still an ENUM, so it is safe to run more than once, and a no-op
-- where a fresh database already created it as VARCHAR from the entity mapping.
--
-- Run:  mysql --database=<ar_db> < V14__project_status_varchar.sql
--
-- ---------------------------------------------------------
-- PRE-MIGRATION CHECKLIST (do before running)
-- ---------------------------------------------------------
--  1. STOP every AR instance that has this schema open (an ALTER takes a metadata lock; devtools restarts also run DDL).
--  2. Backup:   mysqldump --single-transaction <ar_db> project_master_reference > project_master_reference_pre_V14.sql
--  3. Record the current type and value distribution (save the output):
--        SELECT column_type, is_nullable, column_default FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'project_master_reference' AND column_name = 'project_status';
--        SELECT project_status, COUNT(*) FROM project_master_reference GROUP BY project_status;
--  4. Confirm no value is outside the old ENUM or empty-string-coerced:  expect only ACTIVE / COMPLETED / ON_HOLD /
--     CANCELLED / INACTIVE / NULL.  (An empty string '' in an ENUM column means an invalid value was stored; investigate first.)
--  5. Confirm no index or FK uses project_status (checked 2026-10-22: none).
--
-- ---------------------------------------------------------
-- POST-MIGRATION VERIFICATION
-- ---------------------------------------------------------
--        SELECT column_type, is_nullable FROM information_schema.columns
--         WHERE table_schema = DATABASE() AND table_name = 'project_master_reference' AND column_name = 'project_status';
--        -- expect: varchar(30) | YES
--        SELECT project_status, COUNT(*) FROM project_master_reference GROUP BY project_status;
--        -- expect: identical counts to the pre-migration output
--
-- ---------------------------------------------------------
-- ROLLBACK
-- ---------------------------------------------------------
--  Preferred: restore project_master_reference from the step-2 backup.
--  Or revert in place (only valid while no row holds a value outside the ENUM list - otherwise MySQL strict mode rejects it).
--  Use the SUPERSET list so PLANNING/ARCHIVED rows written after migration still fit:
--        ALTER TABLE project_master_reference MODIFY COLUMN project_status
--            ENUM('ACTIVE','INACTIVE','COMPLETED','ON_HOLD','CANCELLED','NOT_STARTED','IN_PROGRESS','DELAYED','PLANNING','ARCHIVED') NULL;
-- =========================================================

DROP PROCEDURE IF EXISTS project_status_to_varchar_v14;

DELIMITER //
CREATE PROCEDURE project_status_to_varchar_v14()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE()
                 AND table_name = 'project_master_reference'
                 AND column_name = 'project_status'
                 AND data_type = 'enum') THEN
        ALTER TABLE project_master_reference MODIFY COLUMN project_status VARCHAR(30) NULL;
    END IF;
END//
DELIMITER ;

CALL project_status_to_varchar_v14();
DROP PROCEDURE project_status_to_varchar_v14;
