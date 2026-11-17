-- =====================================================================
-- V12: currency recorded on a tax calculation (audit finding G-12)
-- =====================================================================
-- The currency the taxable amount was expressed in when tax was calculated.
-- Invoice generation for an occurrence uses it, and refuses to invoice when
-- the billing configuration's currency (or tax region) changed after tax was
-- calculated - the frozen amounts would otherwise be invoiced in a currency
-- they were never calculated in.
--
-- Additive and nullable: calculations made before V12 keep NULL and are
-- checked on their tax region only. No backfill is attempted - the currency
-- in force when those were calculated is not recorded anywhere.

ALTER TABLE tax_calculation
    ADD COLUMN currency_code VARCHAR(10) NULL;
