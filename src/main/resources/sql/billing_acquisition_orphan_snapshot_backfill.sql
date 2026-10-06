-- One-off data repair: backfill billing_acquisition for orphaned billing snapshots.
--
-- Before the fix, POST /api/v1/billing-snapshots and
-- POST /api/billing-data-acquisition/acquire were separate, non-atomic calls,
-- so a billing_snapshot could be committed without its billing_acquisition
-- row. The application now creates both in one transaction, reports orphans as
-- READY on GET /api/billing-data-acquisition/active-configurations, and
-- reconciles an orphan the next time its snapshot is requested - so this
-- script is optional. Run it to repair all existing orphans at once.
--
-- NOTE: This project has no Flyway/Liquibase migration runner; this script is
-- run manually (dialect: MySQL). It is idempotent - it only inserts rows for
-- snapshots that have no acquisition record for their configuration and
-- billing period (uk_billing_acq_config_period), and never updates existing rows.

-- 1. Preview the orphaned snapshots.
SELECT s.billing_snapshot_id, s.snapshot_number, s.billing_configuration_id, s.project_id,
       s.billing_period_start, s.billing_period_end, s.status, s.created_date
FROM billing_snapshot s
JOIN billing_configuration bc ON bc.billing_configuration_id = s.billing_configuration_id
LEFT JOIN billing_acquisition a
       ON a.billing_configuration_id = s.billing_configuration_id
      AND a.billing_period_start = s.billing_period_start
      AND a.billing_period_end = s.billing_period_end
WHERE a.id IS NULL;

-- 2. Check how billing_acquisition.id is stored, then run the matching INSERT.
SHOW COLUMNS FROM billing_acquisition LIKE 'id';

-- 2a. id is BINARY(16) (Hibernate 6 default for java.util.UUID on MySQL):
INSERT INTO billing_acquisition
    (id, billing_configuration_id, project_id, billing_period_start, billing_period_end,
     trigger_mode, status, snapshot_id, acquired_at, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), s.billing_configuration_id, s.project_id,
       s.billing_period_start, s.billing_period_end,
       'MANUAL', 'READY', s.billing_snapshot_id,
       COALESCE(s.created_date, NOW()), NOW(), NOW()
FROM billing_snapshot s
JOIN billing_configuration bc ON bc.billing_configuration_id = s.billing_configuration_id
LEFT JOIN billing_acquisition a
       ON a.billing_configuration_id = s.billing_configuration_id
      AND a.billing_period_start = s.billing_period_start
      AND a.billing_period_end = s.billing_period_end
WHERE a.id IS NULL;

-- 2b. id is CHAR(36) / VARCHAR(36) - use this instead of 2a:
-- INSERT INTO billing_acquisition
--     (id, billing_configuration_id, project_id, billing_period_start, billing_period_end,
--      trigger_mode, status, snapshot_id, acquired_at, created_at, updated_at)
-- SELECT UUID(), s.billing_configuration_id, s.project_id,
--        s.billing_period_start, s.billing_period_end,
--        'MANUAL', 'READY', s.billing_snapshot_id,
--        COALESCE(s.created_date, NOW()), NOW(), NOW()
-- FROM billing_snapshot s
-- JOIN billing_configuration bc ON bc.billing_configuration_id = s.billing_configuration_id
-- LEFT JOIN billing_acquisition a
--        ON a.billing_configuration_id = s.billing_configuration_id
--       AND a.billing_period_start = s.billing_period_start
--       AND a.billing_period_end = s.billing_period_end
-- WHERE a.id IS NULL;
