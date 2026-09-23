-- =====================================================
-- CDC Field Extension Migration
-- Adds new fields to support RMS and PMS CDC synchronization
-- =====================================================

-- Add new columns to client table for RMS CDC
ALTER TABLE client 
ADD COLUMN IF NOT EXISTS country_code VARCHAR(255) NULL AFTER assets;

ALTER TABLE client 
ADD COLUMN IF NOT EXISTS email VARCHAR(255) NULL AFTER country_code;

ALTER TABLE client 
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(255) NULL AFTER email;

-- Add new column to project_master_reference table for PMS CDC
ALTER TABLE project_master_reference 
ADD COLUMN IF NOT EXISTS project_code VARCHAR(255) NULL AFTER project_name;

-- =====================================================
-- Verification Queries
-- =====================================================

-- Verify client table columns
-- SELECT client_id, country_code, email, phone_number FROM client LIMIT 10;

-- Verify project_master_reference table columns
-- SELECT pms_project_id, project_code FROM project_master_reference LIMIT 10;
