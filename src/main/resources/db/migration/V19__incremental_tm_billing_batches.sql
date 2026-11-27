-- =========================================================
-- Incremental Time & Material billing - ADDITIVE schema (G-27)
-- =========================================================
-- Several acquisition batches (Billing Snapshots) may bill one project and
-- billing period. This Flyway script only ADDS what the batches need; it
-- removes and rewrites nothing, as G-27 requires of V12 onward:
--
--   * billing_snapshot.batch_sequence   1, 2, 3 ... per project + period
--   * billing_snapshot.idempotency_key  client key of the acquisition request
--   * uk_billing_snapshot_period_batch  (project, period, batch_sequence)
--   * uk_billing_snapshot_config_idempotency (configuration, idempotency_key)
--   * idx_billing_acq_config_period     plain index on billing_acquisition
--                                        (configuration, period) - keeps the
--                                        billing_configuration_id foreign key
--                                        indexed once the unique period key
--                                        is removed later
--
-- None of these can fail on existing data: both new columns start NULL on
-- every existing row, and a MySQL unique index never compares NULLs.
--
-- NOT in this script (data-dependent or non-additive), see the DBA-reviewed
-- script scripts/dba/V19_incremental_tm_billing_batches_dba.sql:
--   * uk_billing_acq_snapshot (fails if legacy acquisition rows share a snapshot)
--   * dropping uk_billing_snapshot_project_period and uk_billing_acq_config_period
--   * backfilling batch_sequence and expense claims
--
-- Until that DBA script has run, the old period-level unique keys stay in
-- place: the application keeps working exactly as before, and an attempt to
-- create a second batch for an already-acquired period fails cleanly with
-- nothing saved (the application reports that the DBA script is pending).
--
-- Idempotent: every step checks information_schema first, so it is safe after
-- Hibernate (ddl-auto=update) already created a column or index, and safe to
-- re-run.
-- =========================================================

-- STEP 1 - New columns on billing_snapshot (NULL on every existing row).
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
                  AND column_name = 'batch_sequence') = 0,
               'ALTER TABLE billing_snapshot ADD COLUMN batch_sequence INT NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
                  AND column_name = 'idempotency_key') = 0,
               'ALTER TABLE billing_snapshot ADD COLUMN idempotency_key VARCHAR(100) NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- STEP 2 - Batch-level uniqueness (replacement for the period-level key,
-- which stays until the DBA script removes it).
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
                  AND index_name = 'uk_billing_snapshot_period_batch') = 0,
               'ALTER TABLE billing_snapshot ADD CONSTRAINT uk_billing_snapshot_period_batch UNIQUE (project_id, billing_period_start, billing_period_end, batch_sequence)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- STEP 3 - A repeated acquisition request returns the batch it created.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
                  AND index_name = 'uk_billing_snapshot_config_idempotency') = 0,
               'ALTER TABLE billing_snapshot ADD CONSTRAINT uk_billing_snapshot_config_idempotency UNIQUE (billing_configuration_id, idempotency_key)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- STEP 4 - Plain (configuration, period) index on billing_acquisition.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_acquisition'
                  AND index_name = 'idx_billing_acq_config_period') = 0,
               'ALTER TABLE billing_acquisition ADD INDEX idx_billing_acq_config_period (billing_configuration_id, billing_period_start, billing_period_end)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
