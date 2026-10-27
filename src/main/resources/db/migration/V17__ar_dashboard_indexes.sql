-- =========================================================
-- AR dashboard read indexes                                              (manual script - NOT applied automatically)
--   1. invoice          : idx_invoice_status_date      (status, invoice_date)
--   2. billing_snapshot : idx_billing_snapshot_status  (status)
--   3. billing_schedule : idx_billing_schedule_due     (is_invoiced, billing_date)
-- =========================================================
-- Why
--   GET /api/v1/dashboard/summary groups invoices by status and invoice month, snapshots by status, and filters
--   schedules on "not invoiced and due by the as-of date". These columns have no index today (see the @Table
--   definitions of Invoice, BillingSnapshot and BillingSchedule). The dashboard works without this script; it only
--   keeps those aggregates cheap as the tables grow.
--
-- Effect on existing data
--   * Indexes only - no column, constraint or row changes. Each index is built in place (online) on MySQL 8.
--   * Idempotent: every index is created only if an index of that name does not already exist, so the script can be
--     re-run safely. MySQL has no CREATE INDEX IF NOT EXISTS, hence the information_schema checks below.
--
-- Rollback
--   DROP INDEX idx_invoice_status_date ON invoice;
--   DROP INDEX idx_billing_snapshot_status ON billing_snapshot;
--   DROP INDEX idx_billing_schedule_due ON billing_schedule;
-- =========================================================

SET @schema := DATABASE();

-- 1. invoice (status, invoice_date)
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = @schema AND table_name = 'invoice' AND index_name = 'idx_invoice_status_date') = 0,
    'CREATE INDEX idx_invoice_status_date ON invoice (status, invoice_date)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. billing_snapshot (status)
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = @schema AND table_name = 'billing_snapshot' AND index_name = 'idx_billing_snapshot_status') = 0,
    'CREATE INDEX idx_billing_snapshot_status ON billing_snapshot (status)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. billing_schedule (is_invoiced, billing_date)
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = @schema AND table_name = 'billing_schedule' AND index_name = 'idx_billing_schedule_due') = 0,
    'CREATE INDEX idx_billing_schedule_due ON billing_schedule (is_invoiced, billing_date)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Verify
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
  FROM information_schema.statistics
 WHERE table_schema = @schema
   AND index_name IN ('idx_invoice_status_date', 'idx_billing_snapshot_status', 'idx_billing_schedule_due')
 GROUP BY table_name, index_name;
