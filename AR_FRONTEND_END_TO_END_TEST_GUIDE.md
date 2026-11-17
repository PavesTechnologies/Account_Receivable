# AR Frontend End-to-End Test and Verification Guide

This guide is an executable, step-by-step test manual for QA engineers, Frontend developers, and Finance UAT testers verifying the Accounts Receivable (AR) pipeline from UI navigation down to database assertions.

---

## 1. Prerequisites and Local Service Startup

### 1.1 Local Backend Startup
- **Prerequisites:**
  - JDK 21 installed (`java -version` returns 21.x)
  - MySQL 8.0 running locally on port `3306` with database `accounts_receivable`
  - Active configuration loaded from environment or `application-local.properties`
- **Execution Command:**
  ```powershell
  # Start AR backend in local profile
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
  ```
- **Context Path & Base URL:**
  - Backend Base URL: `http://localhost:8080/paves/ar`
  - Health Endpoint: `http://localhost:8080/paves/ar/actuator/health`

### 1.2 Local Frontend Startup
- **Frontend Repository Location:** External repository (clone into adjacent folder `../ar-frontend` or as provided by team).
- **Execution Commands:**
  ```bash
  cd ../ar-frontend
  npm install
  # Start development server targeting local backend
  VITE_API_BASE_URL="http://localhost:8080/paves/ar" npm run dev
  ```
- **Local Frontend UI URL:** Usually `http://localhost:5173` or `http://localhost:3000`.

---

## 2. Environment Variables & Configuration Checks (No Secrets)

Ensure non-production environment variables are configured before launching:

| Variable | Recommended Non-Production Value | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Backend HTTP listening port |
| `SERVER_SERVLET_CONTEXT_PATH` | `/paves/ar` | Application context root |
| `SPRING_PROFILES_ACTIVE` | `local` (or `uat`) | Active Spring environment profile |
| `AR_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Allowed CORS origins for frontend |
| `AR_AUTOMATIC_BILLING_ENABLED` | `false` during manual UI test; `true` for auto test | Controls scheduled automatic billing daemon |
| `AR_AUTOMATIC_BILLING_INTERVAL_MS`| `60000` | Poll interval for automatic billing |
| `AR_TMS_BASE_URL` | Mock or UAT TMS URL | TMS API service integration |
| `AR_UMS_BASE_URL` | Mock or UAT UMS URL | User Management Service endpoint |
| `SPRING_MAIL_HOST` | `localhost` (MailHog/smtp4dev) | Non-production mail catcher |
| `SPRING_MAIL_PORT` | `1025` | Mail catcher SMTP port |

> **IMPORTANT:** Never set real client email addresses in test data. Route all test emails to an internal mail catcher like MailHog or smtp4dev.

---

## 3. Database Migration, Adoption & Test-Data Setup

### 3.1 Migration Verification
Ensure Flyway has migrated all scripts up to V15 (`V15__tax_calculation_supersede.sql`):
```sql
SELECT version, description, type, script, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 5;
```
*Expected:* Version `15` is present with `success = 1`.

### 3.2 Pre-populated Master Data Verification
Verify required lookup records exist in the database:
```sql
-- Verify Billing Types exist
SELECT billing_type_id, billing_type_name, is_active FROM billing_type_master;
-- Verify Billing Frequencies exist
SELECT billing_frequency_id, billing_frequency_name, duration_value, duration_unit FROM billing_frequency_master;
-- Verify Currencies exist
SELECT currency_id, currency_code, currency_name FROM currency_master;
-- Verify Tax Regions and Components exist
SELECT tax_region_id, region_name FROM tax_region_master;
SELECT component_id, component_name, rate FROM tax_component_master;
```

---

## 4. Login and Role Verification

| User Role | Persona | UMS Authorities Required | Permitted UI Actions |
|---|---|---|---|
| **Finance Executive** (Maker) | `fe_user` | `ROLE_FINANCE_EXECUTIVE`, `CONFIGURATION_CREATE`, `INVOICE_CREATE` | Create, edit, submit billing configurations; generate manual invoices; cannot approve. |
| **Finance Manager** (Checker) | `fm_user` | `ROLE_FINANCE_MANAGER`, `BILLING_CONFIGURATION_APPROVAL`, `INVOICE_APPROVAL` | Approve or reject configurations; approve or reject invoices; cannot approve own submissions. |
| **AR Operations** | `ops_user` | `ROLE_AR_OPERATIONS`, `OPERATIONS_RECOVERY`, `INVOICE_DELIVERY` | Dispatch invoices (`send`), resolve uncertain deliveries, recalculate stale tax, recover missing receivables. |

---

## 5. Master Data Setup (Client & Project)

1. Open UI: **Clients & Projects** menu.
2. Verify Client exists: `Acme Corp Global` (Tax State: Karnataka, GSTIN: `29AAAAA0000A1Z5`).
3. Verify Project exists: `Project Phoenix` (Project ID: `1001`, Budget: `INR 1,200,000.00`).
4. Ensure primary client billing contact email is configured as an internal test inbox (e.g. `billing-test@internal.domain`).

---

## 6. End-to-End Scenario Testing Matrix

---

### Scenario 1: Fixed Price Billing Configuration & Schedule Generation

- **ID:** `E2E-FP-01`
- **Role:** Finance Executive (`fe_user`)
- **Preconditions:** Client and Project exist; no active configuration for Project Phoenix.
- **UI Navigation & Action:**
  1. Navigate to **Billing Configurations** -> Click **+ New Configuration**.
  2. Select Context: `PROJECT`, Project: `Project Phoenix`, Billing Type: `Fixed Price`.
  3. Frequency: `MONTHLY`, Generation Type: `MANUAL`, Currency: `INR`.
  4. Effective From: `2027-01-01`, Effective To: `2027-04-30` (4 months).
  5. Contract Value: `400,000.00`, Retention: `0%`, Advance: `0.00`.
  6. Click **Save Draft** -> Click **Generate Occurrences**.
  7. Click **Submit for Approval**.
- **Expected UI Result:**
  - Status changes from `DRAFT` to `PENDING_APPROVAL`.
  - 4 contiguous occurrences displayed:
    - Period 1: `2027-01-01` to `2027-01-31`, Amount: `100,000.00`, Status: `SCHEDULED`.
    - Period 2: `2027-02-01` to `2027-02-28`, Amount: `100,000.00`, Status: `SCHEDULED`.
    - Period 3: `2027-03-01` to `2027-03-31`, Amount: `100,000.00`, Status: `SCHEDULED`.
    - Period 4: `2027-04-01` to `2027-04-30`, Amount: `100,000.00`, Status: `SCHEDULED`.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/billing-configurations` -> returns `201 Created`.
  - `PUT /paves/ar/api/billing-configurations/{id}/submit` -> returns `200 OK` with `approvalStatus = PENDING_APPROVAL`.
- **Database Verification:**
  ```sql
  SELECT billing_configuration_id, approval_status, billing_status, contract_value 
  FROM billing_configuration WHERE project_id = 1001;
  SELECT period_number, period_start_date, period_end_date, billing_amount, period_status 
  FROM billing_schedule WHERE billing_configuration_id = (SELECT billing_configuration_id FROM billing_configuration WHERE project_id = 1001)
  ORDER BY period_number;
  ```
- **Failure Diagnosis:** If occurrences overlap or fail to sum to 400,000.00, check `BillingOccurrenceServiceImpl`.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 2: Maker-Checker Enforcement on Configuration Approval

- **ID:** `E2E-AUTH-01`
- **Preconditions:** Configuration created by `fe_user` in `PENDING_APPROVAL`.
- **UI Navigation & Action:**
  1. Stay logged in as `fe_user` (the submitter).
  2. Attempt to click **Approve** button.
  3. Log out and log in as `fm_user` (Finance Manager).
  4. Open the same configuration -> Click **Approve**.
- **Expected UI Result:**
  - For `fe_user`: Approve button disabled or click results in an explicit error banner: *"A maker cannot approve their own submission."*
  - For `fm_user`: Approval succeeds. Banner: *"Billing configuration approved successfully."* Status badge changes to `APPROVED` (Green), `ACTIVE`.
- **Expected API / Backend Behavior:**
  - For `fe_user`: `PUT /paves/ar/api/billing-configurations/{id}/approve` -> returns `403 Forbidden` (`MakerCheckerException`).
  - For `fm_user`: `PUT /paves/ar/api/billing-configurations/{id}/approve` -> returns `200 OK`.
- **Database Verification:**
  ```sql
  SELECT approval_status, approval_decided_by, approval_decided_by_user_id 
  FROM billing_configuration WHERE project_id = 1001;
  ```
  *Assertion:* `approval_status = 'APPROVED'`, `approval_decided_by` matches `fm_user`.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 3: Time & Material (T&M) Rate Card Resolution & Acquisition

- **ID:** `E2E-TM-01`
- **Role:** Finance Executive (`fe_user`)
- **Preconditions:** T&M Project exists; TMS has approved timesheets for 2026-10-01 to 2026-10-31:
  - Developer: 160 hours
  - QA Engineer: 80 hours
- **UI Navigation & Action:**
  1. Create T&M configuration for project. Pricing Model: `ROLE_BASED`.
  2. In Rate Cards tab, add:
     - Role: `Developer`, Rate: `1,500.00/hr`, Effective From: `2026-01-01`.
     - Competing card: Role: `Developer`, Rate: `1,800.00/hr`, Effective From: `2026-10-01`.
     - Role: `QA Engineer`, Rate: `1,200.00/hr`, Effective From: `2026-01-01`.
  3. Submit & Approve configuration (using `fm_user`).
  4. Navigate to **Billing Acquisition / Snapshots** -> Click **Acquire Timesheets**.
  5. Select Period: `2026-10-01` to `2026-10-31`. Click **Execute Acquisition**.
- **Expected UI Result:**
  - Snapshot generated with status `READY_FOR_TAX`.
  - Line 1: `Developer`, 160 hrs @ `1,800.00` (picks the more recent effective rate card deterministically) = `288,000.00`.
  - Line 2: `QA Engineer`, 80 hrs @ `1,200.00` = `96,000.00`.
  - Subtotal: `384,000.00`.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/billing-snapshots` returns `201 Created` with snapshot items.
- **Database Verification:**
  ```sql
  SELECT snapshot_id, status, subtotal, total_amount FROM billing_snapshot WHERE project_id = :projectId;
  SELECT role_name, hours, hourly_rate, total_amount FROM billing_snapshot_item WHERE snapshot_id = :snapshotId;
  ```
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 4: Tax Calculation and Validation

- **ID:** `E2E-TAX-01`
- **Role:** Finance Executive
- **Preconditions:** Snapshot from `E2E-TM-01` exists in `READY_FOR_TAX`. Project Tax Region is Karnataka (`KA`), Client billing address in Maharashtra (`MH`) (Inter-state).
- **UI Navigation & Action:**
  1. On Snapshot detail screen, click **Calculate Tax**.
  2. Verify Tax breakdown modal.
- **Expected UI Result:**
  - Tax Calculation status: `TAX_CALCULATED`.
  - Subtotal: `384,000.00`.
  - IGST (18%): `69,120.00`.
  - CGST: `0.00`, SGST: `0.00`.
  - Grand Total: `453,120.00`.
  - Display ordering follows configured component sequence.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/tax-calculations/calculate` -> `200 OK` with `TaxCalculationResponseDto`.
- **Database Verification:**
  ```sql
  SELECT tax_calculation_id, subtotal, tax_amount, total_amount, currency_code, tax_region_id 
  FROM tax_calculation WHERE billing_snapshot_id = :snapshotId;
  ```
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 5: Invoice Generation & PDF Document Storage

- **ID:** `E2E-INV-01`
- **Role:** Finance Executive
- **Preconditions:** Tax calculated for snapshot or occurrence.
- **UI Navigation & Action:**
  1. Click **Generate Invoice Draft**.
  2. Navigate to **Invoice Management Workspace** (`/invoices`).
  3. Verify the generated invoice row: Invoice Number (e.g. `INV-2026-0001`), Status: `GENERATED`.
  4. Click invoice row to preview.
  5. Click **Submit for Approval**.
- **Expected UI Result:**
  - Invoice status changes to `PENDING_APPROVAL`.
  - Preview displays client details, line items, tax breakdown, and bank details.
  - PDF document download link is **not yet active** (marked available only upon approval).
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/v1/invoices/generate/snapshot/{id}` -> `200 OK`.
  - `GET /paves/ar/api/v1/invoices/{id}` shows `documentAvailable = false`.
- **Database Verification:**
  ```sql
  SELECT invoice_id, invoice_number, invoice_status, subtotal, tax_amount, grand_total 
  FROM invoice WHERE invoice_number = 'INV-2026-0001';
  ```
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 6: Invoice Approval and `READY_TO_SEND` Transition

- **ID:** `E2E-INV-02`
- **Role:** Finance Manager (`fm_user`)
- **Preconditions:** Invoice `INV-2026-0001` in `PENDING_APPROVAL`.
- **UI Navigation & Action:**
  1. Log in as `fm_user`.
  2. Open invoice `INV-2026-0001` -> Click **Approve Invoice**.
- **Expected UI Result:**
  - Status updates to `READY_TO_SEND`.
  - "Download PDF" button becomes active and clickable.
  - "Send to Client" button appears (enabled for operations/manager roles).
  - KPI counter `readyToSendCount` increments by 1.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/v1/invoices/{id}/approve` -> `200 OK`.
  - Generated PDF is saved in `invoice_document` table.
  - `GET /paves/ar/api/v1/invoices/{id}` returns `invoiceStatus = READY_TO_SEND` and `documentAvailable = true`.
- **Database Verification:**
  ```sql
  SELECT invoice_status, approved_by, approved_at FROM invoice WHERE invoice_id = :invoiceId;
  SELECT document_id, file_name, mime_type FROM invoice_document WHERE invoice_id = :invoiceId;
  ```
  *Assertion:* `invoice_status = 'READY_TO_SEND'`, and `invoice_document` record exists.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 7: Client Dispatch & Automatic Receivable Creation

- **ID:** `E2E-DISP-01`
- **Role:** AR Operations / Finance Manager
- **Preconditions:** Invoice in `READY_TO_SEND`; test SMTP mail catcher active.
- **UI Navigation & Action:**
  1. Open invoice `INV-2026-0001` -> Click **Send to Client**.
  2. Confirm destination email dialog.
  3. Observe delivery status update.
- **Expected UI Result:**
  - Delivery status shows `SENT`.
  - Send button becomes disabled. Banner: *"Invoice dispatched to client."*
  - An invoice that has been sent cannot be sent again.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/v1/invoices/{id}/send` executes SMTP call outside database transaction.
  - On SMTP success, writes `invoice_delivery` with status `SENT`.
  - Exactly one `receivable` record is created for `grand_total`.
- **Database Verification:**
  ```sql
  SELECT delivery_id, delivery_status, recipient_email, sent_at FROM invoice_delivery WHERE invoice_id = :invoiceId;
  SELECT receivable_id, invoice_id, total_amount, outstanding_balance, receivable_status 
  FROM receivable WHERE invoice_id = :invoiceId;
  ```
  *Assertion:* `delivery_status = 'SENT'`, `receivable.outstanding_balance = invoice.grand_total`.
  Constraint `uk_receivable_invoice` guarantees exactly one receivable row.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 8: Tax Recalculation Recovery (Superseded History)

- **ID:** `E2E-REC-01`
- **Role:** AR Operations (`ops_user`)
- **Preconditions:** Occurrence has `TAX_CALCULATED`. Configuration currency or tax region is modified and re-approved, making existing tax calculation stale. Invoicing attempts are refused with `400` (G-12).
- **UI Navigation & Action:**
  1. Navigate to **Operations Recovery** -> **Stale Tax Recalculation**.
  2. Select blocked occurrence.
  3. Enter mandatory Reason: *"Client tax registration moved from Karnataka to Maharashtra"*.
  4. Click **Recalculate Tax**.
- **Expected UI Result:**
  - Old tax calculation is superseded, not deleted.
  - New tax calculation is generated with updated tax region/currency.
  - Occurrence is returned to `TAX_CALCULATED` with updated amounts.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/v1/recovery/billing-occurrences/{id}/recalculate-tax` -> returns `200 OK` with `RecoveryResultDto`.
- **Database Verification:**
  ```sql
  SELECT tax_calculation_id, billing_schedule_id, superseded_billing_schedule_id, 
         superseded_at, superseded_by, superseded_reason 
  FROM tax_calculation 
  WHERE superseded_billing_schedule_id = :occurrenceId OR billing_schedule_id = :occurrenceId;
  ```
  *Assertion:* Exactly one active calculation with `billing_schedule_id = :occurrenceId`, and one historical calculation with `superseded_billing_schedule_id = :occurrenceId` and populated audit columns.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 9: Missing Receivable Reconciliation

- **ID:** `E2E-REC-02`
- **Role:** AR Operations (`ops_user`)
- **Preconditions:** Simulate crash where delivery completed (`SENT`) but server crashed before receivable creation.
- **UI Navigation & Action:**
  1. Navigate to **Operations Recovery** -> **Receivable Reconciliation**.
  2. Search for invoice `INV-2026-0001`.
  3. Provide Reason: *"Recovering missed receivable after server restart."*
  4. Click **Reconcile Receivable**.
- **Expected UI Result:**
  - Banner: *"Receivable successfully created with opening balance."*
  - No duplicate email is sent.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/v1/recovery/invoices/{invoiceId}/receivable` -> returns `200 OK`.
- **Database Verification:**
  ```sql
  SELECT COUNT(*) FROM receivable WHERE invoice_id = :invoiceId;
  ```
  *Assertion:* Count is exactly `1`.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

### Scenario 10: Recurring Subscription Renewal Lifecycle

- **ID:** `E2E-RC-01`
- **Role:** Finance Executive (`fe_user`) & Finance Manager (`fm_user`)
- **Preconditions:** Standalone Product/Service recurring configuration exists, approved and active for `2026-01-01` to `2026-06-30` (6 months @ 50,000 = 300,000).
- **UI Navigation & Action:**
  1. Open the recurring configuration -> Click **Renew Configuration**.
  2. Option: `SAME_AS_PREVIOUS`.
  3. Dates: Effective From: `2026-07-01`, Effective To: `2026-12-31`. Click **Submit Renewal**.
  4. Verify renewal status is `PENDING_APPROVAL`, `is_active = false`.
  5. Log in as `fm_user` -> Click **Approve Renewal**.
- **Expected UI Result:**
  - Renewal term is approved and activated.
  - 6 new occurrences generated for July through December.
  - Original occurrences (Jan-Jun) remain intact and unchanged.
  - Zero date overlap between terms.
- **Expected API / Backend Behavior:**
  - `POST /paves/ar/api/billing-recurring/{id}/renew` creates child recurring config.
  - `PUT /paves/ar/api/billing-configurations/{id}/approve` activates renewal.
- **Database Verification:**
  ```sql
  SELECT recurring_configuration_id, recurring_start_date, recurring_end_date, 
         renewal_approval_status, is_active 
  FROM billing_recurring_configuration WHERE renewed_from_id = :originalId;
  ```
  *Assertion:* `renewal_approval_status = 'APPROVED'`, `is_active = 1`.
- **Pass/Fail:** `[ ] PASS  [ ] FAIL`

---

## 7. Known Blockers and Production Restrictions

> [!CAUTION]
> **DO NOT TEST THE FOLLOWING IN PRODUCTION:**
> 1. Do NOT enable `AR_AUTOMATIC_BILLING_ENABLED=true` in production until Finance UAT sign-off and BD decisions are closed.
> 2. Do NOT configure Fixed Price contracts with `retentionPercentage > 0` or `advanceReceived > 0` (blocked by fail-closed guard BD-01/02).
> 3. Do NOT test email dispatch with live client email addresses.
> 4. Do NOT attempt to test live UMS integration until UMS roles and JWT audience (BD-09) are provisioned.
