-- =====================================================================
-- DBA-REVIEWED SCRIPT - Incremental Time & Material billing (companion of V19)
-- =====================================================================
-- NOT a Flyway migration: it lives outside db/migration on purpose (G-27
-- allows only additive Flyway scripts from V12 on). It is run by hand by a
-- DBA, section by section, after reading each result.
--
-- What it does, in this order:
--   PART A  read-only pre-checks             (nothing changes)
--   PART B  batch_sequence backfill          (fills NULLs with 1)
--   PART C  billing_acquisition batch key    (add new unique key, then drop old one)
--   PART D  billing_snapshot period key      (drop old one; replacement added by V19)
--   PART E  expense claim backfill           (skips duplicates; reports them; logged)
--   PART F  read-only post-checks
--   PART G  rollback / recovery              (commented out; never run by default)
--
-- Prerequisites:
--   1. V19__incremental_tm_billing_batches.sql has been applied (A1 confirms).
--   2. The application build that contains incremental billing is deployed
--      (it reads batch_sequence/idempotency_key). The previous build also keeps
--      working until PART D has run - see PART G.
--   3. A backup / snapshot of the database was taken immediately before.
--   4. MySQL 8.0.21 or later (A0) - PART E uses CREATE TABLE ... SELECT, which
--      GTID-enforcing servers (e.g. managed MySQL) accept from 8.0.21 on.
--   5. Session: SET SQL_SAFE_UPDATES = 0 if the client enables safe updates
--      (PART B updates by a non-key column).
--
-- Guarantees:
--   * No DELETE, no TRUNCATE. Existing snapshots, items, acquisition records
--     and invoices are never removed. The only UPDATEs fill NULL columns.
--   * An old unique key is dropped only after its replacement exists, so
--     duplicate prevention never lapses.
--   * Every step is guarded and can be re-run; a re-run changes nothing that
--     was already done.
--   * Each ALTER TABLE is atomic in MySQL 8: if it fails, it changed nothing.
--     MySQL commits DDL implicitly, so PARTs are independent units.
-- =====================================================================


-- =====================================================================
-- PART A - PRE-CHECKS (read-only). Record the results.
-- =====================================================================

-- A0. Server version - expect 8.0.21 or later.
SELECT VERSION() AS mysql_version, @@sql_require_primary_key AS require_primary_key,
       @@enforce_gtid_consistency AS gtid_consistency;

-- A1. V19 applied? Expect 5 rows: batch_sequence, idempotency_key columns and
--     uk_billing_snapshot_period_batch, uk_billing_snapshot_config_idempotency,
--     idx_billing_acq_config_period indexes (index rows repeat per column).
SELECT 'column' AS object_type, table_name, column_name AS object_name
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
  AND column_name IN ('batch_sequence', 'idempotency_key')
UNION ALL
SELECT DISTINCT 'index', table_name, index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('uk_billing_snapshot_period_batch', 'uk_billing_snapshot_config_idempotency',
                     'idx_billing_acq_config_period');

-- A2. Period-level keys still present? (Before PART C/D: expect both.)
SELECT DISTINCT table_name, index_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('uk_billing_snapshot_project_period', 'uk_billing_acq_config_period',
                     'uk_billing_acq_snapshot');

-- A3. MUST return zero rows. More than one snapshot of one project + period
--     before PART D would mean the period-level key was missing; stop and
--     investigate with Finance.
SELECT project_id, billing_period_start, billing_period_end, COUNT(*) AS snapshots,
       GROUP_CONCAT(snapshot_number ORDER BY created_date) AS snapshot_numbers
FROM billing_snapshot
WHERE batch_sequence IS NULL
GROUP BY project_id, billing_period_start, billing_period_end
HAVING COUNT(*) > 1;

-- A4. MUST return zero rows before PART C. Two acquisition records that
--     reference the same snapshot - resolve with Finance (do not delete here).
SELECT snapshot_id, COUNT(*) AS acquisition_records,
       GROUP_CONCAT(CONCAT(billing_period_start, '..', billing_period_end)) AS periods
FROM billing_acquisition
WHERE snapshot_id IS NOT NULL
GROUP BY snapshot_id
HAVING COUNT(*) > 1;

-- A5. Expense lines that PART E will SKIP (informational - these are never
--     changed; the application already refuses to bill them again because it
--     checks live expense lines by lineItemId).
--     reason DUPLICATE_LIVE_LINES : the lineItemId is on more than one live
--                                   (non-cancelled) snapshot
--     reason ALREADY_CLAIMED      : another line already holds the claim
SELECT 'DUPLICATE_LIVE_LINES' AS reason, i.source_reference_id AS line_item_id,
       COUNT(*) AS live_lines,
       GROUP_CONCAT(s.snapshot_number ORDER BY s.created_date) AS snapshot_numbers
FROM billing_snapshot_item i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
WHERE i.item_type = 'EXPENSE' AND i.source_reference_id IS NOT NULL AND s.status <> 'CANCELLED'
GROUP BY i.source_reference_id
HAVING COUNT(*) > 1
UNION ALL
SELECT 'ALREADY_CLAIMED', i.source_reference_id, COUNT(*),
       GROUP_CONCAT(s.snapshot_number ORDER BY s.created_date)
FROM billing_snapshot_item i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
WHERE i.item_type = 'EXPENSE' AND i.billed_source_reference_id IS NULL AND s.status <> 'CANCELLED'
  AND EXISTS (SELECT 1 FROM billing_snapshot_item c
              WHERE c.item_type = 'EXPENSE' AND c.billed_source_reference_id = i.source_reference_id)
GROUP BY i.source_reference_id;

-- A6. Baseline counts - keep for PART F.
SELECT (SELECT COUNT(*) FROM billing_snapshot)                               AS snapshots,
       (SELECT COUNT(*) FROM billing_snapshot_item)                          AS snapshot_items,
       (SELECT COUNT(*) FROM billing_snapshot_item
          WHERE billed_source_reference_id IS NOT NULL)                      AS claimed_items,
       (SELECT COUNT(*) FROM billing_acquisition)                            AS acquisition_records,
       (SELECT COUNT(*) FROM invoice)                                        AS invoices,
       (SELECT COUNT(*) FROM billing_snapshot WHERE batch_sequence IS NULL)  AS snapshots_without_batch;


-- =====================================================================
-- PART B - batch_sequence backfill (every pre-existing snapshot is batch 1)
-- =====================================================================
-- Safe while uk_billing_snapshot_project_period still exists (one snapshot per
-- project + period) and also on a re-run after it is gone: a period that
-- already has a batch 1 is skipped, and the application never writes NULL.
-- Optional for correctness (the application treats NULL as batch 1); it makes
-- the data explicit. If A3 returned rows this statement fails as a whole on
-- uk_billing_snapshot_period_batch and changes nothing.
UPDATE billing_snapshot s
LEFT JOIN (SELECT project_id, billing_period_start, billing_period_end
           FROM billing_snapshot
           WHERE batch_sequence = 1
           GROUP BY project_id, billing_period_start, billing_period_end) taken
       ON taken.project_id = s.project_id
      AND taken.billing_period_start = s.billing_period_start
      AND taken.billing_period_end = s.billing_period_end
SET s.batch_sequence = 1
WHERE s.batch_sequence IS NULL
  AND taken.project_id IS NULL;


-- =====================================================================
-- PART C - billing_acquisition: one record per batch
-- =====================================================================
-- C1. Replacement key first. Fails (changing nothing) if A4 returned rows.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_acquisition'
                  AND index_name = 'uk_billing_acq_snapshot') = 0,
               'ALTER TABLE billing_acquisition ADD CONSTRAINT uk_billing_acq_snapshot UNIQUE (snapshot_id)',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- C2. Old key removed ONLY when the replacement key and the plain
--     (configuration, period) index (V19 STEP 4, serves the foreign key) exist.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_acquisition'
                  AND index_name = 'uk_billing_acq_config_period') > 0
               AND (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_acquisition'
                  AND index_name = 'uk_billing_acq_snapshot') > 0
               AND (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_acquisition'
                  AND index_name = 'idx_billing_acq_config_period') > 0,
               'ALTER TABLE billing_acquisition DROP INDEX uk_billing_acq_config_period',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- PART D - billing_snapshot: several batches per project + period
-- =====================================================================
-- Run last: this is the step that lets a period take a second batch.
-- Old key removed ONLY when uk_billing_snapshot_period_batch (V19 STEP 2) exists.
-- Duplicate source billing stays prevented throughout by
-- uk_billing_snapshot_item_billed_source, which this script never touches.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
                  AND index_name = 'uk_billing_snapshot_project_period') > 0
               AND (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'billing_snapshot'
                  AND index_name = 'uk_billing_snapshot_period_batch') > 0,
               'ALTER TABLE billing_snapshot DROP INDEX uk_billing_snapshot_project_period',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================
-- PART E - expense claim backfill (legacy expense lines only)
-- =====================================================================
-- Expense lines written before incremental billing carry no claim
-- (billed_source_reference_id NULL). This gives each one its claim - the XMS
-- lineItemId it already carries in source_reference_id - so the database
-- unique key uk_billing_snapshot_item_billed_source guards it like a new line.
--
-- A line is backfilled only when ALL hold:
--   * EXPENSE line on a live (non-CANCELLED) snapshot, no claim yet;
--   * its lineItemId is on exactly ONE live expense line (no duplicate);
--   * no line anywhere already claims that lineItemId.
-- Everything else is SKIPPED and listed by A5 / E1 - never overwritten, never
-- failing the backfill. An existing claim is never changed (only NULLs are set).
-- Every backfilled line is logged in dba_v19_expense_claim_backfill, which
-- also makes the backfill reversible (PART G).
--
-- E1. Skipped lines - same report as A5. Hand to Finance; nothing is done to them.
--     (Re-run A5 here.)

-- E2. Log table (not a JPA entity; Hibernate ignores it). Column types are
--     copied from billing_snapshot_item, so it matches the UUID storage in use;
--     the explicit primary key satisfies sql_require_primary_key.
CREATE TABLE IF NOT EXISTS dba_v19_expense_claim_backfill (PRIMARY KEY (billing_snapshot_item_id)) AS
SELECT i.billing_snapshot_item_id, i.billing_snapshot_id, i.source_reference_id,
       CAST(NULL AS DATETIME(6)) AS backfilled_at
FROM billing_snapshot_item i
WHERE 1 = 0;

-- E3. Select and claim in one transaction. If the UPDATE fails, run ROLLBACK
--     and re-run E3 (the log insert is undone with it).
START TRANSACTION;

SET @backfill_run_at := NOW(6);

INSERT INTO dba_v19_expense_claim_backfill
    (billing_snapshot_item_id, billing_snapshot_id, source_reference_id, backfilled_at)
SELECT i.billing_snapshot_item_id, i.billing_snapshot_id, i.source_reference_id, @backfill_run_at
FROM billing_snapshot_item i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
JOIN (SELECT x.source_reference_id
      FROM billing_snapshot_item x
      JOIN billing_snapshot y ON y.billing_snapshot_id = x.billing_snapshot_id
      WHERE x.item_type = 'EXPENSE' AND x.source_reference_id IS NOT NULL AND y.status <> 'CANCELLED'
      GROUP BY x.source_reference_id
      HAVING COUNT(*) = 1) single_live ON single_live.source_reference_id = i.source_reference_id
LEFT JOIN (SELECT DISTINCT c.billed_source_reference_id
           FROM billing_snapshot_item c
           WHERE c.item_type = 'EXPENSE' AND c.billed_source_reference_id IS NOT NULL) claimed
       ON claimed.billed_source_reference_id = i.source_reference_id
WHERE i.item_type = 'EXPENSE'
  AND i.billed_source_reference_id IS NULL
  AND s.status <> 'CANCELLED'
  AND claimed.billed_source_reference_id IS NULL;

-- The UPDATE reads its rows from the log table, not from a sub-select of the
-- table being updated.
UPDATE billing_snapshot_item i
JOIN dba_v19_expense_claim_backfill l
  ON l.billing_snapshot_item_id = i.billing_snapshot_item_id
 AND l.backfilled_at = @backfill_run_at
SET i.billed_source_reference_id = i.source_reference_id
WHERE i.billed_source_reference_id IS NULL;

-- Expect: last_statement_rows (lines just claimed by the UPDATE) = logged_now.
SELECT (SELECT COUNT(*) FROM dba_v19_expense_claim_backfill WHERE backfilled_at = @backfill_run_at) AS logged_now,
       ROW_COUNT() AS last_statement_rows;

COMMIT;


-- =====================================================================
-- PART F - POST-CHECKS (read-only)
-- =====================================================================

-- F1. Keys: expect uk_billing_snapshot_period_batch, uk_billing_snapshot_config_idempotency,
--     uk_billing_acq_snapshot, idx_billing_acq_config_period, uk_billing_snapshot_item_billed_source;
--     expect NO uk_billing_snapshot_project_period and NO uk_billing_acq_config_period.
SELECT DISTINCT table_name, index_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('billing_snapshot', 'billing_acquisition', 'billing_snapshot_item')
ORDER BY table_name, index_name;

-- F2. Expect 0 / 0: no snapshot without a batch number, no duplicate batch.
SELECT (SELECT COUNT(*) FROM billing_snapshot WHERE batch_sequence IS NULL) AS without_batch,
       (SELECT COUNT(*) FROM (SELECT 1 FROM billing_snapshot
                              GROUP BY project_id, billing_period_start, billing_period_end, batch_sequence
                              HAVING COUNT(*) > 1) d) AS duplicate_batches;

-- F3. Compare with A6: snapshots, snapshot_items, acquisition_records and
--     invoices are UNCHANGED; claimed_items grew by exactly the PART E log count.
SELECT (SELECT COUNT(*) FROM billing_snapshot)                               AS snapshots,
       (SELECT COUNT(*) FROM billing_snapshot_item)                          AS snapshot_items,
       (SELECT COUNT(*) FROM billing_snapshot_item
          WHERE billed_source_reference_id IS NOT NULL)                      AS claimed_items,
       (SELECT COUNT(*) FROM dba_v19_expense_claim_backfill)                 AS expense_claims_backfilled,
       (SELECT COUNT(*) FROM billing_acquisition)                            AS acquisition_records,
       (SELECT COUNT(*) FROM invoice)                                        AS invoices;

-- F4. Expect zero rows: no source record claimed twice.
SELECT item_type, billed_source_reference_id, COUNT(*) AS claims
FROM billing_snapshot_item
WHERE billed_source_reference_id IS NOT NULL
GROUP BY item_type, billed_source_reference_id
HAVING COUNT(*) > 1;

-- F5. Live expense lines still without a claim: expect exactly the lines A5 reported.
SELECT i.source_reference_id, s.snapshot_number, s.status
FROM billing_snapshot_item i
JOIN billing_snapshot s ON s.billing_snapshot_id = i.billing_snapshot_id
WHERE i.item_type = 'EXPENSE' AND i.billed_source_reference_id IS NULL AND s.status <> 'CANCELLED'
ORDER BY i.source_reference_id;

-- F6. Expect zero rows: an invoice per snapshot at most, and every snapshot
--     an invoice points to still exists.
SELECT billing_snapshot_id, COUNT(*) AS invoices
FROM invoice WHERE billing_snapshot_id IS NOT NULL
GROUP BY billing_snapshot_id HAVING COUNT(*) > 1;
SELECT v.invoice_number
FROM invoice v
LEFT JOIN billing_snapshot s ON s.billing_snapshot_id = v.billing_snapshot_id
WHERE v.billing_snapshot_id IS NOT NULL AND s.billing_snapshot_id IS NULL;


-- =====================================================================
-- PART G - ROLLBACK / RECOVERY (commented out - run only on a decision)
-- =====================================================================
-- G1. Undo the expense claim backfill (only the lines this script claimed):
-- UPDATE billing_snapshot_item i
-- JOIN dba_v19_expense_claim_backfill l ON l.billing_snapshot_item_id = i.billing_snapshot_item_id
-- SET i.billed_source_reference_id = NULL
-- WHERE i.billed_source_reference_id = l.source_reference_id;
--     Then rename the log table (e.g. to dba_v19_expense_claim_backfill_undone)
--     before ever re-running PART E, or its primary key rejects the same lines.
--
-- G2. Restore the period-level keys - ONLY possible while no project/period
--     (and no configuration/period) has more than one row. Check first; any
--     row returned means a second batch exists and the old key cannot return:
-- SELECT project_id, billing_period_start, billing_period_end, COUNT(*)
-- FROM billing_snapshot GROUP BY project_id, billing_period_start, billing_period_end HAVING COUNT(*) > 1;
-- SELECT billing_configuration_id, billing_period_start, billing_period_end, COUNT(*)
-- FROM billing_acquisition GROUP BY billing_configuration_id, billing_period_start, billing_period_end
-- HAVING COUNT(*) > 1;
-- ALTER TABLE billing_snapshot ADD CONSTRAINT uk_billing_snapshot_project_period
--     UNIQUE (project_id, billing_period_start, billing_period_end);
-- ALTER TABLE billing_acquisition ADD CONSTRAINT uk_billing_acq_config_period
--     UNIQUE (billing_configuration_id, billing_period_start, billing_period_end);
--
-- G3. The V19 additions (columns, new keys, index) need no rollback: the
--     previous application build ignores them, and NULLs never conflict.
-- =====================================================================
