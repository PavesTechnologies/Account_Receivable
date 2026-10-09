-- =========================================================
-- Invoice Notes / Terms & Conditions / Payment Instructions
-- =========================================================
-- company_profile holds the configurable defaults; invoice holds the
-- frozen copy taken at generation. All columns are nullable and are NOT
-- backfilled - existing invoices keep NULL (no invented content).
-- =========================================================

ALTER TABLE company_profile
    ADD COLUMN default_invoice_notes TEXT NULL,
    ADD COLUMN default_terms_and_conditions TEXT NULL,
    ADD COLUMN default_payment_instructions TEXT NULL;

ALTER TABLE invoice
    ADD COLUMN invoice_notes TEXT NULL,
    ADD COLUMN terms_and_conditions TEXT NULL,
    ADD COLUMN payment_instructions TEXT NULL;
