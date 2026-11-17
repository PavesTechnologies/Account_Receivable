-- =========================================================
-- One Invoice per Billing Snapshot
-- =========================================================
-- InvoiceServiceImpl.generateInvoice() treats billing_snapshot_id as
-- unique (existsByBillingSnapshotId check + flush to surface a duplicate
-- insert), but no database constraint backed it, so two concurrent or
-- retried generations for one snapshot could both insert. This adds the
-- missing constraint, mirroring uk_invoice_billing_schedule.
--
-- NULLs are not compared by a MySQL unique index, so schedule-path
-- invoices with no billing snapshot are unaffected.
--
-- Hibernate (ddl-auto=update) also tries to create this constraint from
-- the Invoice entity mapping and silently skips it when duplicates exist -
-- run STEP 1 and STEP 2 below either way.
--
-- This script deletes and modifies NO data.
-- =========================================================

-- STEP 1 - Duplicate check. MUST return zero rows before STEP 3.
-- Any row returned is a snapshot invoiced more than once: resolve it with
-- Finance (keep the invoice that was approved/sent, deal with the other
-- through the normal correction process) - do not delete invoices from
-- here.
SELECT billing_snapshot_id,
       COUNT(*)                                          AS invoice_count,
       GROUP_CONCAT(invoice_number ORDER BY generated_at) AS invoice_numbers,
       GROUP_CONCAT(status ORDER BY generated_at)         AS statuses
FROM invoice
WHERE billing_snapshot_id IS NOT NULL
GROUP BY billing_snapshot_id
HAVING COUNT(*) > 1;

-- STEP 2 - Already applied? (by Hibernate or an earlier run)
-- If this returns a row, skip STEP 3.
SELECT index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'invoice'
  AND index_name = 'uk_invoice_billing_snapshot';

-- STEP 3 - Add the constraint.
ALTER TABLE invoice
    ADD CONSTRAINT uk_invoice_billing_snapshot UNIQUE (billing_snapshot_id);
