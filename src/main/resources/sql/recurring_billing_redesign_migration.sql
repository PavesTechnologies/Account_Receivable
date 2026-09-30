-- Recurring Billing Redesign Migration
-- This migration adds support for:
-- 1. Project-based and Product/Service-based recurring billing contexts
-- 2. Manual renewal with renewal chain tracking
-- 3. Per-occurrence recurring amount model

-- Step 1: Make project_id nullable in billing_configuration
-- This allows creating billing configurations without a project (for standalone products/services)
ALTER TABLE billing_configuration MODIFY COLUMN project_id VARCHAR(255) NULL;

-- Step 2: Add billing context fields to billing_configuration
-- billing_context: distinguishes between PROJECT and PRODUCT_SERVICE contexts
-- product_name: stores the name of the product/application/service for standalone billing
-- product_description: stores the description of the product/application/service
ALTER TABLE billing_configuration ADD COLUMN billing_context VARCHAR(30);
ALTER TABLE billing_configuration ADD COLUMN product_name VARCHAR(200);
ALTER TABLE billing_configuration ADD COLUMN product_description VARCHAR(500);

-- Step 3: Set default context for existing records
-- All existing records are project-based, so set billing_context to 'PROJECT'
UPDATE billing_configuration SET billing_context = 'PROJECT' WHERE billing_context IS NULL;

-- Step 4: Add recurring purpose and renewal relationship to billing_subscription_configuration
-- recurring_purpose: distinguishes between PROJECT_DELIVERY and PROJECT_SUPPORT for project-based recurring
-- renewed_from_id: tracks the parent recurring configuration for renewal chain
ALTER TABLE billing_subscription_configuration ADD COLUMN recurring_purpose VARCHAR(30);
ALTER TABLE billing_subscription_configuration ADD COLUMN renewed_from_id BINARY(16);

-- Step 5: Add foreign key constraint for renewal relationship
ALTER TABLE billing_subscription_configuration ADD CONSTRAINT fk_renewed_from 
    FOREIGN KEY (renewed_from_id) REFERENCES billing_subscription_configuration(subscription_configuration_id);

-- Step 6: Add index for renewal relationship lookups
CREATE INDEX idx_renewed_from ON billing_subscription_configuration(renewed_from_id);

-- Step 7: Add index for billing context lookups
CREATE INDEX idx_billing_context ON billing_configuration(billing_context);
