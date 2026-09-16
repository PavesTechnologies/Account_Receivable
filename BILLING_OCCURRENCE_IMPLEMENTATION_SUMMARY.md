# Billing Occurrences and Tax Calculation Implementation Summary

## Overview
This implementation adds complete backend workflow support for Billing Occurrences and Tax Calculation integration for Fixed Price and Recurring billing configurations. The implementation leverages the existing `BillingSchedule` entity as the "Billing Occurrence" concept and integrates with the existing tax calculation service.

## Files Changed

### 1. Entity Changes

#### BillingPeriodStatus.java
- **Path**: `src/main/java/com/AccountReceivableManagement/entity_enums/projectbilling_config/BillingPeriodStatus.java`
- **Changes**: Added `TAX_PENDING` and `TAX_CALCULATED` to support the tax calculation lifecycle
- **New Values**: `PENDING, SCHEDULED, TAX_PENDING, TAX_CALCULATED, INVOICED, CANCELLED`

#### BillingSchedule.java
- **Path**: `src/main/java/com/AccountReceivableManagement/entity/projectbilling_config/BillingSchedule.java`
- **Changes**:
  - Added unique constraint on `(billing_configuration_id, period_start_date, period_end_date)` to prevent duplicate occurrences
  - Added `billingDate` column (billing_date = billing_period_end as per requirements)
  - Added `taxStatus` column with default `PENDING`
- **Purpose**: Enhanced to support tax calculation workflow

#### TaxCalculation.java
- **Path**: `src/main/java/com/AccountReceivableManagement/entity/tax_calculation/TaxCalculation.java`
- **Changes**:
  - Added `billingScheduleId` column (unique, nullable)
  - Made `billingSnapshotId` nullable (removed `nullable = false` constraint)
- **Purpose**: Support tax calculation for both BillingSnapshot (Time & Material) and BillingSchedule (Fixed Price/Recurring)

### 2. Repository Changes

#### BillingScheduleRepository.java
- **Path**: `src/main/java/com/AccountReceivableManagement/repo/projectbilling_config/BillingScheduleRepository.java`
- **New Methods**:
  - `findByTaxStatusAndIsActiveTrue(BillingPeriodStatus taxStatus)`
  - `findByBillingConfigurationAndTaxStatusAndIsActiveTrue(...)`
  - `findByRecurringConfigurationAndTaxStatusAndIsActiveTrue(...)`
  - `findByBillingDateBeforeAndTaxStatusAndIsActiveTrue(...)`
  - `findByBillingConfigurationAndPeriodStatusAndTaxStatusAndIsActiveTrue(...)`
  - `findByRecurringConfigurationAndPeriodStatusAndTaxStatusAndIsActiveTrue(...)`
  - `existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(...)`
  - `existsByRecurringConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(...)`
- **Purpose**: Query methods for occurrence filtering and idempotency checks

#### TaxCalculationRepository.java
- **Path**: `src/main/java/com/AccountReceivableManagement/repo/tax_calculation/TaxCalculationRepository.java`
- **New Methods**:
  - `findByBillingScheduleId(UUID billingScheduleId)`
  - `existsByBillingScheduleId(UUID billingScheduleId)`
- **Purpose**: Support tax calculation queries for BillingSchedule

### 3. Service Layer

#### BillingOccurrenceServiceImpl.java (NEW)
- **Path**: `src/main/java/com/AccountReceivableManagement/service_Imple/projectbilling_config/BillingOccurrenceServiceImpl.java`
- **Purpose**: Core service for occurrence generation and reconciliation
- **Key Methods**:
  - `generateOccurrencesForFixedPrice(UUID billingConfigurationId)`: Generates occurrences for Fixed Price configurations (One-Time or Recurring frequency)
  - `generateOccurrencesForRecurring(UUID billingConfigurationId)`: Generates occurrences for Recurring/Subscription configurations
  - `reconcileOccurrencesOnConfigurationUpdate(UUID billingConfigurationId)`: Reconciles occurrences when configuration changes (deletes SCHEDULED, preserves TAX_CALCULATED/INVOICED)
  - `transitionScheduledToTaxPending()`: Transitions SCHEDULED occurrences to TAX_PENDING when billing_date is reached
- **Features**:
  - Idempotent occurrence generation using unique constraint checks
  - Calendar-based date arithmetic for all frequencies (Weekly, Bi-Weekly, Monthly, Quarterly, Half-Yearly, Annually)
  - Partial period handling with proration
  - Preservation of historical processed records (TAX_CALCULATED, INVOICED)
  - billing_date = period_end_date as per requirements

#### BillingOccurrenceStatusScheduler.java (NEW)
- **Path**: `src/main/java/com/AccountReceivableManagement/service_Imple/projectbilling_config/BillingOccurrenceStatusScheduler.java`
- **Purpose**: Daily scheduler to transition SCHEDULED occurrences to TAX_PENDING
- **Schedule**: Runs daily at midnight (`@Scheduled(cron = "0 0 0 * * *")`)
- **Logic**: Finds occurrences where `billingDate <= today` and `taxStatus = PENDING`, transitions to `TAX_PENDING`

#### TaxCalculationServiceImpl.java
- **Path**: `src/main/java/com/AccountReceivableManagement/service_Imple/tax_calculation/TaxCalculationServiceImpl.java`
- **Changes**:
  - Added `calculateTaxForSchedule(UUID billingScheduleId)` method for Fixed Price/Recurring tax calculation
  - Added `getTaxCalculationByScheduleId(UUID billingScheduleId)` method
  - Added `mapToResponseForSchedule()` helper method
  - Injected `BillingScheduleRepository`
- **Features**:
  - Validates schedule is in `TAX_PENDING` status before calculation
  - Uses existing tax configuration logic (tax region, billing period)
  - Transitions schedule to `TAX_CALCULATED` on success
  - Returns tax calculation response with schedule details

#### BillingFixedPriceServiceImpl.java
- **Path**: `src/main/java/com/AccountReceivableManagement/service_Imple/projectbilling_config/BillingFixedPriceServiceImpl.java`
- **Changes**:
  - Injected `BillingOccurrenceServiceImpl`
  - Added occurrence generation call in `create()` method
  - Added occurrence reconciliation call in `update()` method
- **Purpose**: Automatically generate/reconcile occurrences on Fixed Price configuration changes

#### RecurringBillingServiceImpl.java
- **Path**: `src/main/java/com/AccountReceivableManagement/service_Imple/projectbilling_config/RecurringBillingServiceImpl.java`
- **Changes**:
  - Injected `BillingOccurrenceServiceImpl`
  - Added `billingDate` field to schedule generation (set to `periodEndDate`)
  - Changed `periodStatus` from `PENDING` to `SCHEDULED`
  - Added `taxStatus` field with `PENDING`
  - Added occurrence generation call in `create()` method
  - Added occurrence reconciliation call in `update()` method
- **Purpose**: Automatically generate/reconcile occurrences on Recurring configuration changes

### 4. Service Interface

#### TaxCalculationService.java
- **Path**: `src/main/java/com/AccountReceivableManagement/service_interface/tax_calculation/TaxCalculationService.java`
- **New Methods**:
  - `calculateTaxForSchedule(UUID billingScheduleId)`
  - `getTaxCalculationByScheduleId(UUID billingScheduleId)`

### 5. DTOs

#### BillingOccurrenceResponseDto.java (NEW)
- **Path**: `src/main/java/com/AccountReceivableManagement/dto/projectbilling_config/BillingOccurrenceResponseDto.java`
- **Fields**: All billing schedule fields plus tax calculation details (taxCalculationId, taxCalculationStatus, taxableAmount, totalTaxAmount, grandTotal, taxCalculatedAt, taxComponents) and configuration details (projectName, clientName, currencyCode, taxRegionName, taxRegionCode)

#### TaxCalculationResponseDto.java
- **Path**: `src/main/java/com/AccountReceivableManagement/dto/tax_calculation/TaxCalculationResponseDto.java`
- **Changes**: Added `billingScheduleId` field to support schedule-based tax calculations

### 6. Controller

#### BillingOccurrenceController.java (NEW)
- **Path**: `src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingOccurrenceController.java`
- **Endpoints**:
  - `GET /api/billing-occurrences/{occurrenceId}`: Get single occurrence with tax details
  - `GET /api/billing-occurrences`: List occurrences with filters (billingConfigurationId, recurringConfigurationId, periodStatus, taxStatus, billingDateBefore, billingDateAfter)
  - `GET /api/billing-occurrences/{occurrenceId}/tax-calculation`: Get tax calculation for occurrence
  - `POST /api/billing-occurrences/{occurrenceId}/calculate-tax`: Trigger tax calculation for occurrence
- **Features**:
  - Comprehensive filtering capabilities
  - Includes tax calculation details in response
  - Validates status before allowing tax calculation

## Workflow

### 1. Occurrence Generation
- **Trigger**: Fixed Price or Recurring configuration creation/update
- **Process**:
  1. Service calls `BillingOccurrenceServiceImpl.generateOccurrencesForFixedPrice()` or `generateOccurrencesForRecurring()`
  2. Validates configuration (effective dates, frequency, contract value)
  3. Calculates billing periods based on frequency using calendar-based date arithmetic
  4. Creates `BillingSchedule` records with:
     - `periodStartDate`, `periodEndDate`
     - `billingDate = periodEndDate`
     - `billingAmount` (prorated for partial periods)
     - `periodStatus = SCHEDULED`
     - `taxStatus = PENDING`
  5. Unique constraint prevents duplicate occurrences

### 2. Status Transition (SCHEDULED → TAX_PENDING)
- **Trigger**: Daily scheduler at midnight
- **Process**:
  1. `BillingOccurrenceStatusScheduler.transitionScheduledToTaxPending()` runs
  2. Finds occurrences where `billingDate <= today` and `taxStatus = PENDING`
  3. Transitions `periodStatus` and `taxStatus` to `TAX_PENDING`

### 3. Tax Calculation
- **Trigger**: Manual API call or automated process
- **Process**:
  1. API call to `POST /api/billing-occurrences/{occurrenceId}/calculate-tax`
  2. Validates occurrence is in `TAX_PENDING` status
  3. Calls `TaxCalculationService.calculateTaxForSchedule()`
  4. Resolves tax configuration based on tax region and billing period
  5. Calculates tax using existing tax component logic
  6. Creates `TaxCalculation` record with `billingScheduleId`
  7. Transitions occurrence to `TAX_CALCULATED` status

### 4. Configuration Update Reconciliation
- **Trigger**: Fixed Price or Recurring configuration update
- **Process**:
  1. Service calls `BillingOccurrenceServiceImpl.reconcileOccurrencesOnConfigurationUpdate()`
  2. Deletes only `SCHEDULED` occurrences (preserves TAX_CALCULATED and INVOICED)
  3. Regenerates occurrences from last processed date
  4. Ensures historical data is preserved

## Business Rules Implemented

### Frequency-Based Occurrence Generation
- **One-Time**: Single occurrence covering entire effective period
- **Weekly**: 7-day periods
- **Bi-Weekly**: 14-day periods
- **Monthly**: Calendar month periods (not fixed 30 days)
- **Quarterly**: 3-month periods
- **Half-Yearly**: 6-month periods
- **Annually**: 12-month periods

### Date Handling
- `billing_date = billing_period_end` (as per requirements)
- Calendar-based date arithmetic (no fixed day assumptions)
- Partial period handling with proration
- Date boundary validation

### Idempotency
- Unique constraint on `(billing_configuration_id, period_start_date, period_end_date)`
- Repository existence checks before creation
- Idempotent generation methods

### Historical Data Preservation
- Only `SCHEDULED` occurrences are deleted on reconciliation
- `TAX_CALCULATED` and `INVOICED` occurrences are preserved
- Historical invoiced records are never silently modified

### Concurrency Safety
- Database unique constraint prevents duplicates
- Transactional service methods
- Optimistic locking through version fields (if needed)

## Database Schema Changes

### billing_schedule Table
- **New Column**: `billing_date` (DATE, NOT NULL)
- **New Column**: `tax_status` (ENUM, DEFAULT 'PENDING')
- **New Constraint**: `uk_billing_schedule_period` UNIQUE (billing_configuration_id, period_start_date, period_end_date)

### tax_calculation Table
- **New Column**: `billing_schedule_id` (UUID, UNIQUE, NULLABLE)
- **Modified Column**: `billing_snapshot_id` (removed NOT NULL constraint)

## API Endpoints

### Billing Occurrence APIs
- `GET /api/billing-occurrences/{occurrenceId}` - Get occurrence by ID
- `GET /api/billing-occurrences?billingConfigurationId=...&periodStatus=...&taxStatus=...&billingDateBefore=...&billingDateAfter=...` - List occurrences with filters
- `GET /api/billing-occurrences/{occurrenceId}/tax-calculation` - Get tax calculation
- `POST /api/billing-occurrences/{occurrenceId}/calculate-tax` - Trigger tax calculation

## Testing Recommendations

### Unit Tests
1. Test occurrence generation for each frequency (One-Time, Weekly, Bi-Weekly, Monthly, Quarterly, Half-Yearly, Annually)
2. Test partial period handling and proration
3. Test date boundary conditions (leap years, month ends)
4. Test idempotency (duplicate prevention)
5. Test reconciliation logic (preservation of historical records)
6. Test status transitions (SCHEDULED → TAX_PENDING → TAX_CALCULATED)

### Integration Tests
1. Test end-to-end workflow: config creation → occurrence generation → status transition → tax calculation
2. Test configuration update reconciliation
3. Test concurrent occurrence generation
4. Test tax calculation integration
5. Test API endpoints with various filters

## Migration Strategy

### For Existing Data
1. Run database migration to add new columns and constraints
2. Backfill `billing_date` for existing `BillingSchedule` records (set to `period_end_date`)
3. Set `tax_status` to `PENDING` for existing records
4. Existing `TaxCalculation` records remain linked to `BillingSnapshot` (no impact)

### For New Configurations
1. Occurrences are automatically generated on configuration creation
2. Scheduler will transition to TAX_PENDING on billing_date
3. Tax calculation can be triggered manually or via automation

## Assumptions and Unresolved Decisions

### Assumptions
1. Tax region is configured at the `BillingConfiguration` level (not per occurrence)
2. Tax calculation uses the same tax configuration logic as Time & Material
3. Billing frequency master data is pre-seeded with correct duration values
4. Timezone handling uses application default (no explicit timezone in requirements)

### Unresolved Decisions
1. **Automated Tax Calculation Trigger**: Should tax calculation be triggered automatically when occurrence reaches TAX_PENDING, or remain manual?
2. **Retry Logic**: Should failed tax calculations be retried automatically? If so, how many retries and at what interval?
3. **Tax Calculation Async vs Sync**: Should tax calculation be asynchronous (queue-based) or synchronous (immediate)?
4. **Invoice Integration**: How does the INVOICED status transition work? This is outside the scope of this implementation.

## Notes

- The implementation does NOT break existing Time & Material or Milestone Based billing
- The implementation does NOT duplicate existing logic or services
- The implementation preserves historical billing and invoice data
- The implementation integrates with existing tax calculation service (no hardcoded tax rates)
- The implementation follows the existing codebase patterns and conventions
