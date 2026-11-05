-- =========================================================
-- project_master_reference.lifecycle_stage: native MySQL ENUM -> VARCHAR(30)      [SEPARATE, OPTIONAL DECISION]
-- =========================================================
-- Do not run this together with V14 unless the lifecycle_stage change has been explicitly approved.
--
-- Evidence (checked 2026-10-22, read-only):
--   * AR column today:  ENUM('INITIATION','DESIGN','DEVELOPMENT','TESTING','UAT','DEPLOYMENT','COMPLETED')
--   * PMS pms.projects.current_stage: ENUM('INITIATION','PLANNING','DESIGN','DEVELOPMENT','TESTING','DEPLOYMENT','MAINTENANCE','COMPLETED')
--   * AR Java ProjectStage: INITIATION, PLANNING, DESIGN, DEVELOPMENT, TESTING, EXECUTION, DEPLOYMENT, COMPLETED, MONITORING, CLOSURE, MAINTENANCE
--   -> PLANNING and MAINTENANCE are valid PMS stages that the AR column cannot store; UAT exists only in the AR column.
--   Current PMS rows only use INITIATION / DESIGN / DEVELOPMENT, so nothing is failing today. A project moving to
--   PLANNING or MAINTENANCE would fail to insert (this already happened once: "Data truncated for column 'lifecycle_stage'").
--
-- Effect: existing values are preserved as text. Nullability stays NULL, no default.
-- ENTITY: unlike project_status, the entity mapping for lifecycle_stage is NOT changed by the V14 commit. If this script is
-- approved, also add  @Column(length = 30) @JdbcTypeCode(SqlTypes.VARCHAR)  to ProjectMasterReference.lifecycleStage so
-- fresh databases are created the same way. Reads/writes of an existing VARCHAR column work either way.
--
-- MySQL 8. NOTE: manual script - the application has no migration runner. IDEMPOTENT (alters only while still an ENUM).
-- Run:  mysql --database=<ar_db> < V15__project_lifecycle_stage_varchar.sql
--
-- PRE-MIGRATION CHECKLIST: same as V14, substituting lifecycle_stage:
--   stop all AR instances; mysqldump --single-transaction <ar_db> project_master_reference > project_master_reference_pre_V15.sql;
--   SELECT lifecycle_stage, COUNT(*) FROM project_master_reference GROUP BY lifecycle_stage;   (save output; expect no '' values)
-- POST-MIGRATION: information_schema column_type = varchar(30), is_nullable = YES; value counts identical to before.
-- ROLLBACK: restore from the backup, or (only while every value fits) the superset ENUM:
--   ALTER TABLE project_master_reference MODIFY COLUMN lifecycle_stage
--       ENUM('INITIATION','DESIGN','DEVELOPMENT','TESTING','UAT','DEPLOYMENT','COMPLETED','PLANNING','EXECUTION','MONITORING','CLOSURE','MAINTENANCE') NULL;
-- =========================================================

DROP PROCEDURE IF EXISTS lifecycle_stage_to_varchar_v15;

DELIMITER //
CREATE PROCEDURE lifecycle_stage_to_varchar_v15()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE()
                 AND table_name = 'project_master_reference'
                 AND column_name = 'lifecycle_stage'
                 AND data_type = 'enum') THEN
        ALTER TABLE project_master_reference MODIFY COLUMN lifecycle_stage VARCHAR(30) NULL;
    END IF;
END//
DELIMITER ;

CALL lifecycle_stage_to_varchar_v15();
DROP PROCEDURE lifecycle_stage_to_varchar_v15;
