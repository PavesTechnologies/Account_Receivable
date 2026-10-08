-- =========================================================
-- Invoice Project Duration Migration
-- =========================================================
-- This migration adds project start and end dates to the invoice
-- table to display Project Duration separately from Billing Period.
--
-- Project Duration: projectStartDate → projectEndDate
-- Billing Period: billingPeriodStart → billingPeriodEnd
--
-- These dates are sourced from BillingConfigurationResponseDto
-- which in turn gets them from ProjectMasterReference (CDC-synced
-- from external project management system).
-- =========================================================

-- Add project_start_date column to invoice table
ALTER TABLE invoice
ADD COLUMN project_start_date DATE NULL AFTER project_name;

-- Add project_end_date column to invoice table
ALTER TABLE invoice
ADD COLUMN project_end_date DATE NULL AFTER project_start_date;
