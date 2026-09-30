-- Migration: Support multi-currency client budget summaries
-- Description: Change unique constraint from client_id to (client_id, currency) and add project_count column

-- Step 1: Add the new project_count column
ALTER TABLE client_budget_summary 
ADD COLUMN project_count BIGINT DEFAULT 0;

-- Step 2: Drop the old unique constraint on client_id only
-- Note: The constraint name may vary depending on your database. 
-- Common names are: uk_client_id, unique_client_id, or similar.
-- You may need to check the actual constraint name in your database.
ALTER TABLE client_budget_summary 
DROP INDEX uk_client_id;

-- Step 3: Add the new unique constraint on (client_id, currency)
ALTER TABLE client_budget_summary 
ADD CONSTRAINT uk_client_currency UNIQUE (client_id, currency);

-- Step 4: Update currency column to be NOT NULL
ALTER TABLE client_budget_summary 
MODIFY COLUMN currency VARCHAR(255) NOT NULL;

-- Step 5: Populate project_count for existing records
UPDATE client_budget_summary cbs
SET project_count = (
    SELECT COUNT(*)
    FROM project_master_reference pmr
    WHERE pmr.client_id = cbs.client_id
    AND pmr.project_budget_currency = cbs.currency
);

-- Note: If you have existing data where a client has projects in multiple currencies,
-- you will need to split those into separate rows. This migration assumes
-- the old single-currency constraint was enforced, so each client should only
-- have one currency row. If you have data that violates this, you'll need to
-- handle it manually before running this migration.
