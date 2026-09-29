-- Recurring Billing Redesign - Remove Recurring Purpose
-- This migration removes the recurring_purpose field as it is no longer needed.
-- The recurring configuration now uses budgetSource (PMS_BUDGET or MANUAL) instead.

-- Step 1: Drop the recurring_purpose column from billing_subscription_configuration
-- This column is no longer used after the redesign
ALTER TABLE billing_subscription_configuration DROP COLUMN recurring_purpose;

-- Note: The contract_value_source column already exists and is used to distinguish
-- between PMS_BUDGET and MANUAL budget sources, so no additional changes are needed.
