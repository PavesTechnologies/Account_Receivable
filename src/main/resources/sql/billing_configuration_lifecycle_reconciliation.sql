-- =========================================================
-- READ-ONLY reconciliation: billing configuration lifecycle (project -> configuration -> occurrence ->
-- tax calculation -> invoice -> receivable) when a project's configuration is replaced
-- =========================================================
-- Approval now refuses a configuration whose effective period overlaps another live configuration of the same
-- project (live = APPROVED, not manually deactivated, not EXPIRED). That rule only guards new approvals; these
-- queries find records that already break it or the relationships it protects.
--
-- SELECT only: no INSERT/UPDATE/DELETE/DDL. Still, run it ONLY against an approved isolated/staging copy of
-- the data (never the shared/production database without explicit authorization), with a read-only account:
--   mysql --database=<staging_ar_db> --user=<readonly_user> -p < billing_configuration_lifecycle_reconciliation.sql
--
-- ids are BINARY(16); BIN_TO_UUID() needs MySQL 8.0. Every query should return no rows. For each row found,
-- record the ids and decide the correction with Finance; correct through the application (deactivation,
-- re-approval, the recovery APIs) - never by editing financial rows directly.
-- =========================================================

-- 1. Projects with two live configurations whose effective periods overlap (NULL from/to = open-ended).
--    Both are billed, and GET /api/billing-configurations/project/{projectId} fails while both are ACTIVE.
SELECT a.project_id,
       BIN_TO_UUID(a.billing_configuration_id) AS a_configuration_id, a.billing_status AS a_status,
       a.effective_from AS a_from, a.effective_to AS a_to,
       BIN_TO_UUID(b.billing_configuration_id) AS b_configuration_id, b.billing_status AS b_status,
       b.effective_from AS b_from, b.effective_to AS b_to
FROM billing_configuration a
JOIN billing_configuration b
  ON b.project_id = a.project_id AND a.billing_configuration_id < b.billing_configuration_id
WHERE a.project_id IS NOT NULL
  AND a.approval_status = 'APPROVED' AND b.approval_status = 'APPROVED'
  AND a.manually_deactivated = 0 AND b.manually_deactivated = 0
  AND a.billing_status <> 'EXPIRED' AND b.billing_status <> 'EXPIRED'
  AND (a.effective_from IS NULL OR b.effective_to IS NULL OR a.effective_from <= b.effective_to)
  AND (b.effective_from IS NULL OR a.effective_to IS NULL OR b.effective_from <= a.effective_to)
ORDER BY a.project_id;

-- 2. Recurring occurrences whose subscription belongs to a different configuration than the occurrence.
SELECT BIN_TO_UUID(s.billing_schedule_id) AS billing_schedule_id,
       BIN_TO_UUID(s.billing_configuration_id) AS schedule_configuration_id,
       BIN_TO_UUID(r.billing_configuration_id) AS subscription_configuration_id
FROM billing_schedule s
JOIN billing_subscription_configuration r ON r.subscription_configuration_id = s.subscription_configuration_id
WHERE r.billing_configuration_id <> s.billing_configuration_id;

-- 3. Occurrence tax calculations whose occurrence no longer exists.
SELECT BIN_TO_UUID(t.tax_calculation_id) AS tax_calculation_id, BIN_TO_UUID(t.billing_schedule_id) AS billing_schedule_id
FROM tax_calculation t
LEFT JOIN billing_schedule s ON s.billing_schedule_id = t.billing_schedule_id
WHERE t.billing_schedule_id IS NOT NULL AND s.billing_schedule_id IS NULL;

-- 4. Snapshot tax calculations whose snapshot no longer exists.
SELECT BIN_TO_UUID(t.tax_calculation_id) AS tax_calculation_id, BIN_TO_UUID(t.billing_snapshot_id) AS billing_snapshot_id
FROM tax_calculation t
LEFT JOIN billing_snapshot b ON b.billing_snapshot_id = t.billing_snapshot_id
WHERE t.billing_snapshot_id IS NOT NULL AND b.billing_snapshot_id IS NULL;

-- 5. Occurrence invoices whose tax calculation belongs to another occurrence, or whose project differs from
--    the project of the occurrence's configuration.
SELECT BIN_TO_UUID(i.invoice_id) AS invoice_id, i.invoice_number,
       BIN_TO_UUID(i.billing_schedule_id) AS invoice_schedule_id,
       BIN_TO_UUID(t.billing_schedule_id) AS tax_calculation_schedule_id,
       i.project_id AS invoice_project_id, c.project_id AS configuration_project_id,
       BIN_TO_UUID(c.billing_configuration_id) AS billing_configuration_id
FROM invoice i
JOIN billing_schedule s ON s.billing_schedule_id = i.billing_schedule_id
JOIN billing_configuration c ON c.billing_configuration_id = s.billing_configuration_id
LEFT JOIN tax_calculation t ON t.tax_calculation_id = i.tax_calculation_id
WHERE i.billing_schedule_id IS NOT NULL
  AND ((t.billing_schedule_id IS NOT NULL AND t.billing_schedule_id <> i.billing_schedule_id)
       OR (c.project_id IS NOT NULL AND c.project_id <> i.project_id));

-- 6. Invoices whose occurrence no longer exists.
SELECT BIN_TO_UUID(i.invoice_id) AS invoice_id, i.invoice_number, BIN_TO_UUID(i.billing_schedule_id) AS billing_schedule_id
FROM invoice i
LEFT JOIN billing_schedule s ON s.billing_schedule_id = i.billing_schedule_id
WHERE i.billing_schedule_id IS NOT NULL AND s.billing_schedule_id IS NULL;

-- 7. Receivables without their invoice, or whose project or amount differs from the invoice's.
SELECT BIN_TO_UUID(r.receivable_id) AS receivable_id, BIN_TO_UUID(r.invoice_id) AS invoice_id,
       r.project_id AS receivable_project_id, i.project_id AS invoice_project_id,
       r.invoice_amount, i.grand_total
FROM receivable r
LEFT JOIN invoice i ON i.invoice_id = r.invoice_id
WHERE i.invoice_id IS NULL OR r.project_id <> i.project_id OR r.invoice_amount <> i.grand_total;

-- 8. Not a defect - pending business decision: tax-calculated, not yet invoiced occurrences kept active on
--    manually deactivated configurations. Automatic billing skips them; manual invoicing still allows them.
SELECT BIN_TO_UUID(c.billing_configuration_id) AS billing_configuration_id, c.project_id,
       BIN_TO_UUID(s.billing_schedule_id) AS billing_schedule_id, s.billing_date, s.billing_amount
FROM billing_schedule s
JOIN billing_configuration c ON c.billing_configuration_id = s.billing_configuration_id
WHERE c.manually_deactivated = 1 AND s.is_active = 1 AND s.is_invoiced = 0
  AND s.period_status = 'TAX_CALCULATED'
ORDER BY c.project_id, s.billing_date;
