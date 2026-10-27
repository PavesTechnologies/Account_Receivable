-- =========================================================
-- Invoice items: frozen T&M rate audit (base rate + discount) for TIME_ENTRY lines
-- =========================================================
-- invoice_item.rate stays the effective NET rate and invoice_item.amount the final line amount. These columns only
-- describe how the net rate came about (copied from billing_snapshot_item at generation) so the invoice can show
-- base rate / discount / net without the UI recomputing anything. EXPENSE lines never carry them. Existing rows stay NULL.
--
-- MySQL 8. NOTE: manual script - the application has no migration runner.
-- IDEMPOTENT: each column is added only when missing, so it is safe to run where Hibernate
-- (spring.jpa.hibernate.ddl-auto=update) already created them, and more than once. It never drops or rewrites anything.
--
-- Run:  mysql --database=<ar_db> < V13__invoice_item_tm_rate_audit.sql
-- =========================================================

DROP PROCEDURE IF EXISTS add_invoice_item_column_if_missing;

DELIMITER //
CREATE PROCEDURE add_invoice_item_column_if_missing(IN column_name_in VARCHAR(64), IN column_definition VARCHAR(100))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'invoice_item' AND column_name = column_name_in) THEN
        SET @ddl = CONCAT('ALTER TABLE invoice_item ADD COLUMN ', column_name_in, ' ', column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_invoice_item_column_if_missing('base_rate', 'DECIMAL(19,2) NULL');
CALL add_invoice_item_column_if_missing('discount_type', 'VARCHAR(20) NULL');
CALL add_invoice_item_column_if_missing('discount_value', 'DECIMAL(19,2) NULL');
CALL add_invoice_item_column_if_missing('discount_amount', 'DECIMAL(19,2) NULL');
CALL add_invoice_item_column_if_missing('discount_period_name', 'VARCHAR(150) NULL');

DROP PROCEDURE add_invoice_item_column_if_missing;

-- Verify:
-- SELECT column_name, column_type FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 'invoice_item'
--    AND column_name IN ('base_rate','discount_type','discount_value','discount_amount','discount_period_name');
