# Recurring Billing API Documentation

## Overview
The Recurring Billing API has been refactored to support both Normal Recurring Billing and Subscription-based Recurring Billing through the same endpoint structure.

## Endpoints
- `POST /api/billing-subscription/{billingConfigurationId}` - Create recurring configuration
- `PUT /api/billing-subscription/{subscriptionConfigurationId}` - Update recurring configuration
- `GET /api/billing-subscription/{subscriptionConfigurationId}` - Get recurring configuration
- `GET /api/billing-subscription/billing-configuration/{billingConfigurationId}` - Get configurations by billing configuration
- `DELETE /api/billing-subscription/{subscriptionConfigurationId}` - Delete recurring configuration
- `GET /api/billing-subscription/{subscriptionConfigurationId}/schedule` - Get billing schedule
- `GET /api/billing-subscription/billing-configuration/{billingConfigurationId}/schedule` - Get schedule by billing configuration

## Supported Billing Types

### NORMAL RECURRING
- Monthly
- Quarterly
- Half-Yearly
- Annually

### SUBSCRIPTION RECURRING
- Subscription with optional renewal configuration (AUTO or MANUAL)

---

## API Examples

### 1. Normal Monthly Recurring Billing

**POST /api/billing-subscription/{billingConfigurationId}**

**Request:**
```json
{
  "subscriptionName": null,
  "contractValue": 50000.00,
  "contractValueSource": "MANUAL",
  "subscriptionStartDate": "2026-08-05",
  "subscriptionEndDate": "2027-01-08",
  "renewalType": null,
  "renewalDurationType": null,
  "renewalDurationValue": null,
  "renewalDurationUnit": null,
  "renewalPricingType": null,
  "renewalContractValue": null,
  "renewalBillingFrequencyId": null,
  "renewalEffectiveFrom": null,
  "remarks": "Monthly recurring billing for project"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Recurring configuration created successfully.",
  "data": {
    "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
    "subscriptionName": null,
    "contractValue": 50000.00,
    "contractValueSource": "MANUAL",
    "subscriptionStartDate": "2026-08-05",
    "subscriptionEndDate": "2027-01-08",
    "renewalType": null,
    "renewalDurationType": null,
    "renewalDurationValue": null,
    "renewalDurationUnit": null,
    "renewalPricingType": null,
    "renewalContractValue": null,
    "renewalBillingFrequencyId": null,
    "renewalBillingFrequencyName": null,
    "renewalEffectiveFrom": null,
    "remarks": "Monthly recurring billing for project",
    "createdAt": "2026-08-25T11:30:00",
    "updatedAt": "2026-08-25T11:30:00"
  }
}
```

**Key Points:**
- `subscriptionName` is NOT required for normal recurring
- `renewalType` and all renewal fields are NOT required
- Only `contractValueSource`, `contractValue` (if MANUAL), `subscriptionStartDate`, `subscriptionEndDate` are required
- Billing schedule is automatically generated based on BillingFrequencyMaster

---

### 2. Normal Quarterly Recurring Billing

**POST /api/billing-subscription/{billingConfigurationId}**

**Request:**
```json
{
  "subscriptionName": null,
  "contractValue": 150000.00,
  "contractValueSource": "PMS_BUDGET",
  "subscriptionStartDate": "2026-08-05",
  "subscriptionEndDate": "2027-08-04",
  "renewalType": null,
  "renewalDurationType": null,
  "renewalDurationValue": null,
  "renewalDurationUnit": null,
  "renewalPricingType": null,
  "renewalContractValue": null,
  "renewalBillingFrequencyId": null,
  "renewalEffectiveFrom": null,
  "remarks": "Quarterly recurring using PMS budget"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Recurring configuration created successfully.",
  "data": {
    "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440001",
    "subscriptionName": null,
    "contractValue": 150000.00,
    "contractValueSource": "PMS_BUDGET",
    "subscriptionStartDate": "2026-08-05",
    "subscriptionEndDate": "2027-08-04",
    "renewalType": null,
    "renewalDurationType": null,
    "renewalDurationValue": null,
    "renewalDurationUnit": null,
    "renewalPricingType": null,
    "renewalContractValue": null,
    "renewalBillingFrequencyId": null,
    "renewalBillingFrequencyName": null,
    "renewalEffectiveFrom": null,
    "remarks": "Quarterly recurring using PMS budget",
    "createdAt": "2026-08-25T11:30:00",
    "updatedAt": "2026-08-25T11:30:00"
  }
}
```

**Key Points:**
- When `contractValueSource` is `PMS_BUDGET`, the contract value is automatically fetched from the project budget
- No need to provide `contractValue` manually
- BillingFrequencyMaster should be configured with `durationValue: 3` and `durationUnit: MONTHS` for quarterly

---

### 3. Subscription with AUTO Renewal

**POST /api/billing-subscription/{billingConfigurationId}**

**Request:**
```json
{
  "subscriptionName": "Cloud Storage Premium",
  "contractValue": 12000.00,
  "contractValueSource": "MANUAL",
  "subscriptionStartDate": "2026-08-05",
  "subscriptionEndDate": "2027-08-04",
  "renewalType": "AUTO",
  "renewalDurationType": "SAME_DURATION",
  "renewalDurationValue": null,
  "renewalDurationUnit": null,
  "renewalPricingType": "SAME_PRICE",
  "renewalContractValue": null,
  "renewalBillingFrequencyId": "550e8400-e29b-41d4-a716-446655440002",
  "renewalEffectiveFrom": "2027-08-05",
  "remarks": "Annual subscription with auto-renewal"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Recurring configuration created successfully.",
  "data": {
    "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440003",
    "subscriptionName": "Cloud Storage Premium",
    "contractValue": 12000.00,
    "contractValueSource": "MANUAL",
    "subscriptionStartDate": "2026-08-05",
    "subscriptionEndDate": "2027-08-04",
    "renewalType": "AUTO",
    "renewalDurationType": "SAME_DURATION",
    "renewalDurationValue": null,
    "renewalDurationUnit": null,
    "renewalPricingType": "SAME_PRICE",
    "renewalContractValue": null,
    "renewalBillingFrequencyId": "550e8400-e29b-41d4-a716-446655440002",
    "renewalBillingFrequencyName": "Monthly",
    "renewalEffectiveFrom": "2027-08-05",
    "remarks": "Annual subscription with auto-renewal",
    "createdAt": "2026-08-25T11:30:00",
    "updatedAt": "2026-08-25T11:30:00"
  }
}
```

**Key Points:**
- `subscriptionName` IS REQUIRED when `renewalType` is `AUTO`
- When `renewalType` is `AUTO`, the following fields are required:
  - `renewalDurationType`
  - `renewalPricingType`
  - `renewalBillingFrequencyId`
  - `renewalEffectiveFrom`
- When `renewalDurationType` is `SAME_DURATION`, `renewalDurationValue` and `renewalDurationUnit` are automatically set to null
- When `renewalPricingType` is `SAME_PRICE`, `renewalContractValue` is automatically set to null

---

### 4. Subscription with AUTO Renewal and Custom Duration/Price

**POST /api/billing-subscription/{billingConfigurationId}**

**Request:**
```json
{
  "subscriptionName": "Software License Enterprise",
  "contractValue": 24000.00,
  "contractValueSource": "MANUAL",
  "subscriptionStartDate": "2026-08-05",
  "subscriptionEndDate": "2027-08-04",
  "renewalType": "AUTO",
  "renewalDurationType": "CUSTOM",
  "renewalDurationValue": 2,
  "renewalDurationUnit": "YEARS",
  "renewalPricingType": "REVISED_PRICE",
  "renewalContractValue": 26000.00,
  "renewalBillingFrequencyId": "550e8400-e29b-41d4-a716-446655440003",
  "renewalEffectiveFrom": "2027-08-05",
  "remarks": "Annual subscription with custom 2-year renewal and price increase"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Recurring configuration created successfully.",
  "data": {
    "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440004",
    "subscriptionName": "Software License Enterprise",
    "contractValue": 24000.00,
    "contractValueSource": "MANUAL",
    "subscriptionStartDate": "2026-08-05",
    "subscriptionEndDate": "2027-08-04",
    "renewalType": "AUTO",
    "renewalDurationType": "CUSTOM",
    "renewalDurationValue": 2,
    "renewalDurationUnit": "YEARS",
    "renewalPricingType": "REVISED_PRICE",
    "renewalContractValue": 26000.00,
    "renewalBillingFrequencyId": "550e8400-e29b-41d4-a716-446655440003",
    "renewalBillingFrequencyName": "Quarterly",
    "renewalEffectiveFrom": "2027-08-05",
    "remarks": "Annual subscription with custom 2-year renewal and price increase",
    "createdAt": "2026-08-25T11:30:00",
    "updatedAt": "2026-08-25T11:30:00"
  }
}
```

**Key Points:**
- When `renewalDurationType` is `CUSTOM`, both `renewalDurationValue` and `renewalDurationUnit` are required
- When `renewalPricingType` is `REVISED_PRICE`, `renewalContractValue` is required
- `renewalEffectiveFrom` must be after `subscriptionEndDate`

---

### 5. Subscription with MANUAL Renewal

**POST /api/billing-subscription/{billingConfigurationId}**

**Request:**
```json
{
  "subscriptionName": "Support Service Basic",
  "contractValue": 6000.00,
  "contractValueSource": "MANUAL",
  "subscriptionStartDate": "2026-08-05",
  "subscriptionEndDate": "2027-08-04",
  "renewalType": "MANUAL",
  "renewalDurationType": null,
  "renewalDurationValue": null,
  "renewalDurationUnit": null,
  "renewalPricingType": null,
  "renewalContractValue": null,
  "renewalBillingFrequencyId": null,
  "renewalEffectiveFrom": null,
  "remarks": "Annual subscription with manual renewal"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Recurring configuration created successfully.",
  "data": {
    "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440005",
    "subscriptionName": "Support Service Basic",
    "contractValue": 6000.00,
    "contractValueSource": "MANUAL",
    "subscriptionStartDate": "2026-08-05",
    "subscriptionEndDate": "2027-08-04",
    "renewalType": "MANUAL",
    "renewalDurationType": null,
    "renewalDurationValue": null,
    "renewalDurationUnit": null,
    "renewalPricingType": null,
    "renewalContractValue": null,
    "renewalBillingFrequencyId": null,
    "renewalBillingFrequencyName": null,
    "renewalEffectiveFrom": null,
    "remarks": "Annual subscription with manual renewal",
    "createdAt": "2026-08-25T11:30:00",
    "updatedAt": "2026-08-25T11:30:00"
  }
}
```

**Key Points:**
- When `renewalType` is `MANUAL`, all renewal-specific fields are optional and can be null
- `subscriptionName` is still recommended for subscriptions even with manual renewal

---

## Billing Schedule API

### Get Billing Schedule by Configuration ID

**GET /api/billing-subscription/{subscriptionConfigurationId}/schedule**

**Response:**
```json
{
  "success": true,
  "message": "Billing schedule fetched successfully.",
  "data": [
    {
      "billingScheduleId": "550e8400-e29b-41d4-a716-446655440010",
      "billingConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
      "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
      "periodNumber": 1,
      "periodStartDate": "2026-08-05",
      "periodEndDate": "2026-09-04",
      "billingAmount": 7692.31,
      "scheduleType": "PRIMARY",
      "isPartialPeriod": false,
      "periodStatus": "PENDING",
      "isInvoiced": false,
      "invoiceDate": null,
      "remarks": null,
      "createdAt": "2026-08-25T11:30:00",
      "updatedAt": "2026-08-25T11:30:00"
    },
    {
      "billingScheduleId": "550e8400-e29b-41d4-a716-446655440011",
      "billingConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
      "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
      "periodNumber": 2,
      "periodStartDate": "2026-09-05",
      "periodEndDate": "2026-10-04",
      "billingAmount": 7692.31,
      "scheduleType": "PRIMARY",
      "isPartialPeriod": false,
      "periodStatus": "PENDING",
      "isInvoiced": false,
      "invoiceDate": null,
      "remarks": null,
      "createdAt": "2026-08-25T11:30:00",
      "updatedAt": "2026-08-25T11:30:00"
    },
    {
      "billingScheduleId": "550e8400-e29b-41d4-a716-446655440012",
      "billingConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
      "subscriptionConfigurationId": "550e8400-e29b-41d4-a716-446655440000",
      "periodNumber": 6,
      "periodStartDate": "2027-01-05",
      "periodEndDate": "2027-01-08",
      "billingAmount": 7692.13,
      "scheduleType": "PRIMARY",
      "isPartialPeriod": true,
      "periodStatus": "PENDING",
      "isInvoiced": false,
      "invoiceDate": null,
      "remarks": null,
      "createdAt": "2026-08-25T11:30:00",
      "updatedAt": "2026-08-25T11:30:00"
    }
  ]
}
```

**Key Points:**
- Billing schedule is automatically generated based on:
  - `subscriptionStartDate` and `subscriptionEndDate`
  - `BillingFrequencyMaster.durationValue` and `durationUnit`
  - Total `contractValue`
- The final period is marked as `isPartialPeriod: true` when the end date doesn't align perfectly with the frequency
- Amounts are distributed evenly across periods with the last period getting the remainder to ensure total matches contract value

---

## Conditional Validation Rules

### Normal Recurring (renewalType is null or MANUAL)
- ✅ `subscriptionName`: Optional
- ✅ `contractValueSource`: Required
- ✅ `contractValue`: Required if `contractValueSource` is MANUAL
- ✅ `subscriptionStartDate`: Required
- ✅ `subscriptionEndDate`: Required
- ✅ `renewalType`: Optional
- ✅ All renewal fields: Optional

### Subscription with AUTO Renewal
- ✅ `subscriptionName`: Required
- ✅ `contractValueSource`: Required
- ✅ `contractValue`: Required if `contractValueSource` is MANUAL
- ✅ `subscriptionStartDate`: Required
- ✅ `subscriptionEndDate`: Required
- ✅ `renewalType`: Required (must be AUTO)
- ✅ `renewalDurationType`: Required
- ✅ `renewalPricingType`: Required
- ✅ `renewalBillingFrequencyId`: Required
- ✅ `renewalEffectiveFrom`: Required (must be after subscriptionEndDate)
- ✅ `renewalDurationValue`: Required if `renewalDurationType` is CUSTOM
- ✅ `renewalDurationUnit`: Required if `renewalDurationType` is CUSTOM
- ✅ `renewalContractValue`: Required if `renewalPricingType` is REVISED_PRICE

---

## Database Changes

### Entity Changes
- `BillingSubscriptionConfiguration.subscriptionName`: Changed from `nullable = false` to nullable
- No other entity changes required
- No migration needed for existing data (column already allows nulls in most databases)

### New Files Created
1. `RecurringBillingRequestDto.java` - New DTO with conditional validation
2. `RecurringBillingResponseDto.java` - New response DTO
3. `RecurringBillingService.java` - New service interface
4. `RecurringBillingServiceImpl.java` - New service implementation with conditional validation logic

### Modified Files
1. `BillingSubscriptionController.java` - Updated to use new service and DTOs
2. `BillingSubscriptionConfiguration.java` - Made subscriptionName nullable

### Preserved Files (Backward Compatible)
- `BillingSubscriptionRequestDto.java` - Kept for potential backward compatibility
- `BillingSubscriptionResponseDto.java` - Kept for potential backward compatibility
- `BillingSubscriptionService.java` - Kept for potential backward compatibility
- `BillingSubscriptionServiceImpl.java` - Kept for potential backward compatibility
- All Fixed Price, Timesheet, Milestone controllers remain unchanged

---

## Backward Compatibility

### Existing APIs Preserved
- ✅ Fixed Price Billing API (`/api/billing-fixed-price/*`)
- ✅ Time & Material Rate Card API (`/api/billing-tm-rate-card/*`)
- ✅ Billing Configuration API (`/api/billing-configurations/*`)
- ✅ All master data APIs (BillingType, BillingFrequency, Currency, etc.)

### Endpoint Pattern Preserved
The endpoint pattern `/api/billing-subscription/*` is preserved to maintain backward compatibility with existing frontend integrations. Only the internal DTOs and service layer have been refactored.

---

## Master Data Reused

The refactoring reuses existing master data entities:
- `BillingTypeMaster` - For billing type validation
- `BillingFrequencyMaster` - For frequency configuration (MONTHLY, QUARTERLY, HALF_YEARLY, ANNUALLY)
- `CurrencyMaster` - For currency configuration
- `PaymentTermsMaster` - For payment terms
- `TaxRegionMaster` - For tax region configuration

### Enums Reused
- `ContractValueSource` - PMS_BUDGET, MANUAL
- `RenewalType` - MANUAL, AUTO
- `RenewalDurationType` - SAME_DURATION, CUSTOM
- `RenewalDurationUnit` - DAYS, MONTHS, YEARS
- `RenewalPricingType` - SAME_PRICE, REVISED_PRICE

No new enums or master tables were created.
