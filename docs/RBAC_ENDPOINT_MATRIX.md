# RBAC Endpoint Matrix – Account Receivable Backend

Analysis only. **No code was changed.** This document proposes which role may call each endpoint.

- Scope: all 48 controllers (205 endpoints, GET/POST/PUT/PATCH/DELETE) under `src/main/java/com/AccountReceivableManagement/controller`. Every endpoint is listed individually; section 11 is a per-controller index you can tick against the code
- Roles: **Finance Executive (FE)** and **Finance Manager (FM)**
- Legend for the *Role* column:
  - **FE** = Finance Executive only
  - **FM** = Finance Manager only
  - **BOTH** = either role
  - **PUBLIC** = no login needed
- Legend for the *Lock* column: **LOCK-OWNER** = caller must hold the record's lock; **NOT-LOCKED** = rejected if *another* user holds the lock; `–` = no lock check

---

## 1. Role model

| Role | Responsibility |
|---|---|
| **Finance Executive (FE)** | Day-to-day operator. Creates and edits billing configurations, rate cards, milestones, recurring plans. Runs billing acquisition, tax calculation, invoice generation, previews. **Raises requests** to the manager: submits a billing configuration or an invoice for approval. Fixes rejected items and resubmits. |
| **Finance Manager (FM)** | Approver. **Approves or rejects** what FE raised (billing configurations, invoices), finalizes the invoice document, performs operational recovery, and owns high-impact reference data (tax, currency, exchange rates, company profile). Takes the **APPROVAL lock** before acting. |

Workflow the matrix protects:

```
FE: draft config ─► submit ─────────────► FM: lock(APPROVAL) ─► approve / reject
FE: generate invoice ─► submit-for-approval ─► FM: lock(APPROVAL) ─► approve / reject ─► finalize document
FE: send approved invoice to client (email + PDF)  [D1 – Finance Executive sends; Finance Manager does not]
FE: rejected? ─► correct ─► refresh / re-acquire ─► resubmit
```

Rules that apply everywhere:

1. **A GET is FE-only unless the Finance Manager needs it to take an action.** FE owns the work (configurations, rate/plan details, master data, acquisition, tools, occurrences, invoice lists), so those GETs are FE-only. FM only gets the reads that its screens call to approve / reject or to manage receivables; each is listed with its reason in section 5.6. FM-only views (approval queues, operations reports, recovery event log, receivables) are hidden from FE (section 5.5).
2. **FM does not inherit FE write access by default.** Executive work stays with FE. If you want FM to also create/edit (cover for absence), add FM to the FE write lists (see section 9, decision D2).
3. **Maker-checker** (already implemented, `ar.security.authorization.maker-checker=true`): the user who submitted a record cannot approve it, even if they hold the FM role.
4. **A lock never grants permission.** It only prevents two people working on the same record. Role check first, lock check second.

---

## 2. What the backend already enforces today

This matters because part of the RBAC exists and must not be duplicated or contradicted.

| Mechanism | Where | What it does |
|---|---|---|
| Authentication on `/api/**` | `SecurityConfig` | Any valid JWT. **No role check.** Roles come from the JWT `roles` claim as `ROLE_<role>`, permissions as-is. `@EnableMethodSecurity` is on, but no `@PreAuthorize` is used anywhere. |
| `FinancialActionAuthorizer` | Called **inside services** | Four actions, each backed by a configurable authority list: `BILLING_CONFIGURATION_APPROVAL`, `INVOICE_APPROVAL`, `INVOICE_DELIVERY`, `OPERATIONS_RECOVERY`. Empty list = nobody. |
| `OperationsReportAccess` | `SecurityConfig` URL rule on `/api/v1/operations/**` | Allows only authorities in `ar.operations.report-authorities`. Empty = nobody. |
| `FinanceExecutiveAuthorizer` | `CompanySignatureController` | Separate JWT verification; requires role `FINANCE_EXECUTIVE` for upload/delete signature. |
| Record locks | `RecordActionLockServiceImpl` | `APPROVAL` lock acquisition itself requires the approval permission (config or invoice). `EDIT` lock has no role check. |

**Configuration keys** (set per environment, comma-separated, e.g. `ROLE_FINANCE_MANAGER`):

| Env var | Action | Proposed value |
|---|---|---|
| `AR_AUTH_CONFIGURATION_APPROVAL` | approve/reject billing configuration | `ROLE_FINANCE_MANAGER` |
| `AR_AUTH_INVOICE_APPROVAL` | approve/reject invoice, finalize document | `ROLE_FINANCE_MANAGER` |
| `AR_AUTH_INVOICE_DELIVERY` | send invoice to client | `ROLE_FINANCE_EXECUTIVE` (D1 – Finance Executive sends the email) |
| `AR_AUTH_OPERATIONS_RECOVERY` | recovery endpoints | `ROLE_FINANCE_MANAGER` |
| `AR_OPERATIONS_REPORT_AUTHORITIES` | operations reports | `ROLE_FINANCE_MANAGER` (D4) |

Role-name matching is case/space/hyphen/underscore-insensitive (`Finance Manager` = `FINANCE_MANAGER`), so the exact UMS role name only needs to match in meaning.

> Important: today an environment with those variables empty means **nobody** can approve or send. The matrix below tells you what to set, and which *other* endpoints currently have no role check at all (everything marked "**Gap**").

---

## 3. Summary

| Category | Count | Role |
|---|---|---|
| Manager-only (approve / reject, receivables, recovery, manager views) | 20 | FM |
| Shared (only the reads FM needs to act, lock endpoints, dashboard) | 23 | BOTH |
| Executive (everything else: create / edit / generate / submit / send invoice / all master data and their reads) | 161 | FE |
| Public | 1 | PUBLIC |
| **Total** | **205** | |

Of the manager-only set, **7 are already enforced in the service layer** (approve/reject config, approve/reject/finalize invoice, APPROVAL lock) and 5 recovery POSTs are enforced by `OPERATIONS_RECOVERY`. The rest are gaps. Method mix: 101 GET, 50 POST, 29 PUT, 10 PATCH, 15 DELETE.

---

## 4. Concurrency lock endpoints

`/api/record-locks` · `RecordActionLockController`  
Resource types: `BILLING_CONFIGURATION`, `INVOICE`. Action types: `EDIT`, `APPROVAL`.

| Method | Endpoint | Purpose | Role | Notes |
|---|---|---|---|---|
| POST | `/api/record-locks/{resourceType}/{resourceId}` | Acquire a lock. Body `actionType` = `EDIT` or `APPROVAL`. | **BOTH** (by action, see below) | `EDIT` → FE (BOTH allowed). `APPROVAL` → **FM only**, already enforced: needs `BILLING_CONFIGURATION_APPROVAL` or `INVOICE_APPROVAL`. |
| GET | `/api/record-locks/{resourceType}/{resourceId}` | Lock status (who holds it, expiry) | **BOTH** | Read-only. Used to show "locked by X" in UI. |
| PUT | `/api/record-locks/{resourceType}/{resourceId}/heartbeat` | Keep the lock alive | **BOTH** | Must be the lock owner (service checks). |
| DELETE | `/api/record-locks/{resourceType}/{resourceId}` | Release the lock | **BOTH** | Must be the lock owner. |

Because one POST endpoint serves two roles, the rule is **action-based, not URL-based**: do not put a `hasRole` on the URL; keep the existing check on `actionType == APPROVAL`. Suggested hardening: restrict `EDIT` locks to FE (and FM if D2 = yes) so an unrelated authenticated user cannot hold a record.

### Where locks are enforced today

| Lock check | Endpoint(s) it protects |
|---|---|
| **LOCK-OWNER (APPROVAL)** | `PUT /api/billing-configurations/{id}/approve`, `PUT …/{id}/reject`, `POST /api/v1/invoices/{id}/approve`, `POST /api/v1/invoices/{id}/reject` |
| **NOT-LOCKED** (another user's lock blocks you) | Billing configuration `PUT /{id}`, `PUT /{id}/draft`, `PUT /{id}/submit`, `PATCH /{id}/deactivate`, `DELETE /{id}`; Fixed price create/update/delete; Milestone plan create/update/delete; TM rate card create/update/delete/save; TM rate discount update; Recurring create/update/delete/renew |

Note: the NOT-LOCKED mapping for the configuration endpoints is taken from the service methods that call `validateNotLockedByAnotherUser`; confirm the exact endpoint-to-method mapping for `PUT /{id}` before wiring tests.

---

## 5. Manager workflow endpoints (the approve / reject / act-on-request set)

These are the endpoints FM uses when FE has raised a request.

### 5.1 Billing configuration approval

| Method | Endpoint | Purpose | Role | Lock | Enforced today? |
|---|---|---|---|---|---|
| GET | `/api/billing-configurations/pending-approvals` | Queue of configs waiting for approval | **FM** (hidden from FE) | – | **Gap** |
| PUT | `/api/billing-configurations/{id}/approve` | Approve a submitted config | **FM** | LOCK-OWNER (APPROVAL) | Yes – `BILLING_CONFIGURATION_APPROVAL` + maker-checker |
| PUT | `/api/billing-configurations/{id}/reject` | Reject with reason | **FM** | LOCK-OWNER (APPROVAL) | Yes – `BILLING_CONFIGURATION_APPROVAL` + maker-checker |
| PATCH | `/api/billing-configurations/{id}/deactivate` | Deactivate an **approved** config | **FM** | NOT-LOCKED | **Gap** – changes an already-approved record, so it bypasses the approval step if FE can do it |

### 5.2 Invoice approval

| Method | Endpoint | Purpose | Role | Lock | Enforced today? |
|---|---|---|---|---|---|
| GET | `/api/v1/invoices/pending-approval` | Invoices awaiting approval | **FM** (hidden from FE) | – | **Gap** |
| GET | `/api/v1/invoices/approval-workspace` | Manager approval workspace list | **FM** (hidden from FE) | – | **Gap** |
| POST | `/api/v1/invoices/{invoiceId}/approve` | Approve invoice | **FM** | LOCK-OWNER (APPROVAL) | Yes – `INVOICE_APPROVAL` + maker-checker |
| POST | `/api/v1/invoices/{invoiceId}/reject` | Reject invoice with reason | **FM** | LOCK-OWNER (APPROVAL) | Yes – `INVOICE_APPROVAL` |
| POST | `/api/v1/invoices/{invoiceId}/document` | Finalize the invoice document (PDF) after approval | **FM** | – | Yes – `INVOICE_APPROVAL` |

### 5.3 Operational recovery (all `OPERATIONS_RECOVERY`, already enforced in the services)

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/requeue` | Re-queue a failed automatic billing run | **FM** |
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval` | Reconcile a run with its approved invoice | **FM** |
| GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | Run event log (manager recovery view) | **FM** (hidden from FE; **Gap**, no check today) |
| POST | `/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax` | Recalculate stale tax | **FM** |
| POST | `/api/v1/recovery/invoices/{invoiceId}/receivable` | Create a sent invoice's missing receivable | **FM** |
| POST | `/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` | Resolve an uncertain delivery | **FM** |

### 5.4 Reference / master data – Finance Executive (decided, D5)

Master and reference data are **not** manager actions. The Finance Manager's job is to *take action* on requests (approve / reject billing configurations and invoices) and to own the receivables. Everything else, including all master data, belongs to the Finance Executive.

| Group | Endpoints | Write role |
|---|---|---|
| Billing types, billing frequency, currency, payment terms, proration rules | section 7.4 | **FE** |
| Tax regime, region, component, type, rate configuration, configuration components | section 7.4 | **FE** |
| Exchange rates (`POST /api/v1/exchange-rates`, `/batch`) | section 7.4 | **FE** |
| Company profile text and invoice-content defaults (`POST`, `PUT`) | section 7.3 | **FE** |

The GETs on these masters are **FE-only**: the manager's review screens receive names (type, frequency, currency, tax region) inside the configuration / invoice response and never call the master endpoints.

---

### 5.5 Finance Manager views – hidden from Finance Executive

A Finance Executive must **not** be able to open these, by URL or by UI. Enforce with `hasRole('FINANCE_MANAGER')` on the controller method (or the URL rule for `/api/v1/operations/**`), so a direct API call returns 403, not only a hidden menu item.

| Method | Endpoint | What the manager sees |
|---|---|---|
| GET | `/api/billing-configurations/pending-approvals` | Configurations waiting for approval |
| GET | `/api/v1/invoices/pending-approval` | Invoices waiting for approval |
| GET | `/api/v1/invoices/approval-workspace` | Manager approval workspace |
| GET | `/api/v1/operations/billing-summary` | Billing operations summary |
| GET | `/api/v1/operations/reconciliation` | Reconciliation checks |
| GET | `/api/v1/operations/alerts` | Operational alerts |
| GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | Automatic billing run event log |

Related FM-only actions that FE must also not reach: approve / reject (config and invoice), finalize document (`POST /api/v1/invoices/{invoiceId}/document`), deactivate an approved configuration, every `/api/v1/recovery/**` POST, reference-data writes, and the `APPROVAL` lock. FE keeps the shared screens: lists, details, `approval-history`, `deliveries` and downloads.

---

### 5.6 Reads the Finance Manager needs (and why)

These are the only executive-area GETs opened to FM, because the manager's approval, invoice-review or lock screens call them. Derived from what `BillingApprovals`, `InvoiceApproval` and `InvoiceDetail` request. Every other GET in the executive areas (exchange rates, billing types / frequency / currency / payment terms and the other masters, recurring, fixed price, milestone single-record reads, rate-card list, billing occurrence list, data acquisition, snapshots list, invoice list, software / tool billing, project milestones, client budget) is **FE-only**.

| Method | Endpoint | Why FM needs it |
|---|---|---|
| GET | `/api/billing-configurations` | Approvals page list (`getBillingConfigurations`) |
| GET | `/api/billing-configurations/{id}` | Review screen loads the full configuration (`getBillingConfigurationForApproval`); fixed-price, recurring and T&M rate-card details come nested in this response |
| GET | `/api/billing-configurations/projects/{clientId}` | Review screen resolves the project of the configuration (`resolveConfigurationProject`) |
| GET | `/api/billing-configurations/available-projects` | Same project-resolution call as above |
| GET | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | Review screen loads the milestone plan for milestone-type configurations |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | Review screen loads the discounts of each T&M rate card |
| GET | `/api/v1/invoices/{invoiceId}` | Invoice detail page opened from the approval queue |
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice` | Invoice detail page opened by snapshot id |
| GET | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | Invoice detail shows the tax result the manager is approving |
| GET | `/api/billing-occurrences/{occurrenceId}` | Invoice detail resolves the occurrence behind the invoice |
| GET | `/api/billing-occurrences/{occurrenceId}/tax-calculation` | Tax result for occurrence-based invoices |
| GET | `/api/v1/invoices/{invoiceId}/approval-history` | Shown on the invoice before approve / reject |
| GET | `/api/v1/invoices/{invoiceId}/document` | Manager finalizes the document, then downloads / views it |
| GET | `/api/v1/invoices/{invoiceId}/deliveries` | Manager resolves uncertain deliveries (`recovery/invoice-deliveries/{id}/resolve`) and must see them |
| GET | `/api/v1/company-profile` | Seller details printed on the invoice the manager reviews |
| GET | `/api/v1/company-profile/{companyProfileId}/signature` | Signature shown on the invoice document |
| GET | `/api/v1/company-profile/signature-assets/{signatureReference}` | Signature image on the invoice document |
| GET | `/api/record-locks/{resourceType}/{resourceId}` | Lock status, needed before the manager takes the APPROVAL lock |
| GET | `/api/v1/dashboard/summary` | AR dashboard, kept shared (decision D7) |
| GET | `/api/v1/dashboard/invoices` | AR dashboard ledger, kept shared (decision D7) |

If a manager screen later breaks with 403 on a GET that is not listed here, add that one endpoint to this table; do not open the whole controller.

---

---

## 6. Executive endpoints (operational work)

### 6.1 Billing configuration lifecycle – `/api/billing-configurations`

| Method | Endpoint | Purpose | Role | Lock |
|---|---|---|---|---|
| POST | `/api/billing-configurations` | Create configuration | **FE** | – |
| POST | `/api/billing-configurations/draft` | Create draft | **FE** | – |
| PUT | `/api/billing-configurations/{id}` | Update configuration | **FE** | NOT-LOCKED |
| PUT | `/api/billing-configurations/{id}/draft` | Save draft (wizard step) | **FE** | NOT-LOCKED |
| PUT | `/api/billing-configurations/{id}/submit` | **Submit for approval** (raises the request to FM) | **FE** | NOT-LOCKED |
| POST | `/api/billing-configurations/preview-schedule` | Preview billing schedule (no save) | **FE** | – |
| DELETE | `/api/billing-configurations/{id}` | Delete a **DRAFT** configuration only | **FE** | NOT-LOCKED |
| GET | `/api/billing-configurations` | List all | **BOTH** | – |
| GET | `/api/billing-configurations/{id}` | Get one | **BOTH** | – |
| GET | `/api/billing-configurations/project/{projectId}` | Approved config of a project | **FE** | – |
| GET | `/api/billing-configurations/clients` | Client dropdown | **FE** | – |
| GET | `/api/billing-configurations/projects/{clientId}` | Projects of a client | **BOTH** | – |
| GET | `/api/billing-configurations/available-projects` | Projects without a configuration | **BOTH** | – |

### 6.2 Fixed price – `/api/billing-fixed-price`

| Method | Endpoint | Purpose | Role | Lock |
|---|---|---|---|---|
| POST | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | Create | **FE** | NOT-LOCKED |
| PUT | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | Update | **FE** | NOT-LOCKED |
| DELETE | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | Delete | **FE** | NOT-LOCKED |
| GET | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | Get one | **FE** | – |
| GET | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | List for a configuration | **FE** | – |

### 6.3 Milestone plan – `/api/billing-milestone-plan`

| Method | Endpoint | Purpose | Role | Lock |
|---|---|---|---|---|
| POST | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | Create | **FE** | NOT-LOCKED |
| PUT | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | Update | **FE** | NOT-LOCKED |
| DELETE | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | Delete | **FE** | NOT-LOCKED |
| GET | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | Get one | **FE** | – |
| GET | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | List for a configuration | **BOTH** | – |
| GET | `/api/projects/{pmsProjectId}/milestones` | Project milestones (from PMS) | **FE** | – |

### 6.4 Recurring billing – `/api/billing-recurring`

| Method | Endpoint | Purpose | Role | Lock |
|---|---|---|---|---|
| POST | `/api/billing-recurring/{billingConfigurationId}` | Create | **FE** | NOT-LOCKED |
| PUT | `/api/billing-recurring/{recurringConfigurationId}` | Update | **FE** | NOT-LOCKED |
| DELETE | `/api/billing-recurring/{recurringConfigurationId}` | Delete | **FE** | NOT-LOCKED |
| POST | `/api/billing-recurring/{recurringConfigurationId}/renew` | Renew (goes through approval again) | **FE** | NOT-LOCKED |
| GET | `/api/billing-recurring/{recurringConfigurationId}` | Get one | **FE** | – |
| GET | `/api/billing-recurring/billing-configuration/{billingConfigurationId}` | By configuration | **FE** | – |
| GET | `/api/billing-recurring/{recurringConfigurationId}/schedule` | Billing schedule | **FE** | – |
| GET | `/api/billing-recurring/{recurringConfigurationId}/renewal-history` | Renewal history | **FE** | – |

### 6.5 Time & Material rate cards and discounts – `/api/billing-tm-rate-card`

| Method | Endpoint | Purpose | Role | Lock |
|---|---|---|---|---|
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | Add rate card | **FE** | NOT-LOCKED |
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards/save` | Save rate card (bulk) | **FE** | NOT-LOCKED |
| PUT | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | Update rate card | **FE** | NOT-LOCKED |
| DELETE | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | Delete rate card | **FE** | NOT-LOCKED |
| GET | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | List | **FE** | – |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | Get one | **FE** | – |
| POST | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | Add discount | **FE** | – |
| PUT | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | Update discount | **FE** | NOT-LOCKED |
| DELETE | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | Delete discount | **FE** | – |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | List discounts | **BOTH** | – |
| GET | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | Get discount | **FE** | – |

### 6.6 Billing occurrences – `/api/billing-occurrences`

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/billing-occurrences` | List occurrences | **FE** |
| GET | `/api/billing-occurrences/{occurrenceId}` | Get one | **BOTH** |
| GET | `/api/billing-occurrences/{occurrenceId}/tax-calculation` | Tax result for an occurrence | **BOTH** |
| POST | `/api/billing-occurrences/{occurrenceId}/calculate-tax` | Calculate tax | **FE** |
| POST | `/api/billing-occurrences/{occurrenceId}/invoice` | Generate invoice from occurrence | **FE** |

### 6.7 Billing data acquisition and snapshots

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/billing-data-acquisition/active-configurations` | Configurations ready for acquisition | **FE** |
| POST | `/api/billing-data-acquisition/acquire` | Pull timesheet/project data for billing | **FE** |
| POST | `/api/v1/billing-snapshots` | Create billing snapshot | **FE** |
| GET | `/api/v1/billing-snapshots` | Snapshots by configuration | **FE** |
| GET | `/api/v1/billing-snapshots/by-period` | Snapshot by project and period | **FE** |
| POST | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | Calculate tax for a snapshot | **FE** |
| GET | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | Read tax result | **BOTH** |

### 6.8 Invoice generation, preview, workspace, list

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/v1/billing-snapshots/{snapshotId}/invoice` | Generate invoice | **FE** |
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice` | Get the generated invoice | **BOTH** |
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice-preview` | Preview invoice | **FE** |
| GET | `/api/v1/invoice-generation/workspace` | Invoice generation workspace | **FE** |
| GET | `/api/v1/invoices` | List all invoices | **FE** |
| GET | `/api/v1/invoices/{invoiceId}` | Get invoice | **BOTH** |

### 6.9 Invoice request, correction, and delivery (FE side of the approval cycle)

| Method | Endpoint | Purpose | Role | Lock |
|---|---|---|---|---|
| POST | `/api/v1/invoices/{invoiceId}/submit-for-approval` | **Raise the approval request to FM** | **FE** | – |
| POST | `/api/v1/invoices/{invoiceId}/refresh-after-correction` | Re-copy financial snapshot of a REJECTED invoice | **FE** | – |
| PATCH | `/api/v1/invoices/{invoiceId}/non-financial-correction` | Fix non-financial fields on a rejected invoice | **FE** | – |
| POST | `/api/v1/invoices/{invoiceId}/financial-correction/reacquire` | Re-acquire data for a financial correction | **FE** | – |
| GET | `/api/v1/invoices/{invoiceId}/approval-history` | Approval / rejection history | **BOTH** | – |
| GET | `/api/v1/invoices/{invoiceId}/document` | Download the finalized PDF | **BOTH** | – |
| POST | `/api/v1/invoices/{invoiceId}/send` | Send approved invoice to client (email + PDF) | **FE** (D1) | – |
| GET | `/api/v1/invoices/{invoiceId}/deliveries` | Delivery history | **BOTH** | – |

`send` is enforced today by `INVOICE_DELIVERY`; set `AR_AUTH_INVOICE_DELIVERY=ROLE_FINANCE_EXECUTIVE` per D1. The Finance Manager approves and finalizes the document; the Finance Executive emails it to the client. To let FM send as cover, add `ROLE_FINANCE_MANAGER` to that variable.

### 6.10 Software / tool billing

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/invoice/software-selection/projects/{projectId}` | Assets selectable for an invoice | **FE** |
| POST | `/api/invoice/software-charge-generation` | Generate software charge lines | **FE** |
| POST | `/api/tool-charge-acquisition/preview` | Preview tool charges | **FE** |
| GET | `/api/software-billing-history/assets/{assetId}` | Billing history of an asset | **FE** |
| GET | `/api/rms-assets/projects/{projectId}/billable-assets` | Billable assets from RMS | **FE** |
| GET | `/api/ar/projects/{projectId}/tool-billing` | Tool billing config | **FE** |
| POST | `/api/ar/projects/{projectId}/tool-billing` | Create tool billing config | **FE** |
| PUT | `/api/ar/projects/{projectId}/tool-billing` | Update tool billing config | **FE** |
| GET | `/api/project-tool-assignments` | List assignments | **FE** |
| GET | `/api/project-tool-assignments/project/{projectId}` | By project | **FE** |
| GET | `/api/project-tool-assignments/{id}` | Get one | **FE** |
| POST | `/api/project-tool-assignments` | Create assignment | **FE** |
| PUT | `/api/project-tool-assignments/{id}` | Update | **FE** |
| DELETE | `/api/project-tool-assignments/{id}` | Delete | **FE** |
| POST | `/api/project-tool-assignments/{id}/renew` | Renew | **FE** |
| GET | `/api/tool-catalog` | List tools | **FE** |
| GET | `/api/tool-catalog/active` | Active tools | **FE** |
| GET | `/api/tool-catalog/{toolId}` | Get tool | **FE** |
| POST | `/api/tool-catalog` | Create tool | **FE** |
| PUT | `/api/tool-catalog/{toolId}` | Update tool | **FE** |
| DELETE | `/api/tool-catalog/{toolId}` | Delete tool | **FE** |

### 6.11 Client budget

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/client-budget-summary/{clientId}` | Client budget summary | **FE** |
| POST | `/client-budget-summary/refresh/{clientId}` | Refresh budget from source | **FE** |

Note: this controller's path has no `/api` prefix, so it falls under `anyRequest().authenticated()` rather than the `/api/**` rule. Same effect today; keep it in mind if URL-based rules are added.

---

## 7. Shared and read-only endpoints

### 7.1 Dashboard and receivables

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | AR dashboard KPIs | **BOTH** |
| GET | `/api/v1/dashboard/invoices` | Dashboard invoice list | **BOTH** |
| GET | `/api/v1/receivables/outstanding` | Outstanding receivables | **FM** |
| GET | `/api/v1/receivables/{receivableId}` | Receivable detail | **FM** |

Receivables (`/api/v1/receivables/**`) are managed by the Finance Manager: **FM only**, together with the receivable-recovery POST. The dashboard stays shared (**BOTH**); say if the dashboard should also be FM-only.

Receivables (`/api/v1/receivables/**`) are managed by the Finance Manager: **FM only**, together with the receivable-recovery POST. The dashboard stays shared (**BOTH**); say if the dashboard should also be FM-only.

### 7.2 Operations reports (URL rule already in `SecurityConfig`)

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/v1/operations/billing-summary` | Billing operations summary | **FM** (D4) |
| GET | `/api/v1/operations/reconciliation` | Reconciliation checks | **FM** (D4) |
| GET | `/api/v1/operations/alerts` | Operational alerts | **FM** (D4) |

Enforced by authority list `AR_OPERATIONS_REPORT_AUTHORITIES`. Empty today = nobody can read them.

### 7.3 Company profile and signature

| Method | Endpoint | Purpose | Role | Enforced today? |
|---|---|---|---|---|
| GET | `/api/v1/company-profile` | Active company profile | **BOTH** | Authenticated only |
| GET | `/api/v1/company-profile/{companyProfileId}` | Profile by id | **FE** | Authenticated only |
| GET | `/api/v1/company-profile/invoice-content-defaults` | Invoice content defaults | **FE** | Authenticated only |
| GET | `/api/v1/company-profile/{companyProfileId}/signature` | Signature metadata | **BOTH** | `requireAuthenticated` |
| GET | `/api/v1/company-profile/signature-assets/{signatureReference}` | Signature image | **BOTH** | None (see note) |
| POST | `/api/v1/company-profile` | Create seller company profile | **FE** | Authenticated only (**Gap**) |
| PUT | `/api/v1/company-profile/{companyProfileId}` | Update company profile | **FE** | Authenticated only (**Gap**) |
| PUT | `/api/v1/company-profile/invoice-content-defaults` | Change default invoice content (terms, footer) | **FE** | Authenticated only (**Gap**) |
| PUT | `/api/v1/company-profile/{companyProfileId}/signature` | Upload authorized signature | **FE** | Yes – `FINANCE_EXECUTIVE` |
| DELETE | `/api/v1/company-profile/{companyProfileId}/signature` | Remove signature | **FE** | Yes – `FINANCE_EXECUTIVE` |

Note: signature upload/delete is already FE-only through a separate JWT verifier (`app.security.jwt.*`). Company profile text/default writes and the signature are now both FE (D5). The signature-assets image endpoint is reachable by any authenticated caller; it is read-only.

### 7.4 Master / reference data (every endpoint listed)

All are CRUD on reference tables. **Gap** today (authenticated only). GETs are **FE-only** (the manager screens never call them). Writes are **FE** per D5 (decided: master data belongs to the Finance Executive). Includes every PATCH (activate / deactivate).


**ExchangeRateController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/v1/exchange-rates` | List all | **FE** |
| POST | `/api/v1/exchange-rates/batch` | Create (batch) | **FE** |
| POST | `/api/v1/exchange-rates` | Create | **FE** |
| GET | `/api/v1/exchange-rates/{id}` | Get one | **FE** |
| GET | `/api/v1/exchange-rates/lookup` | Look up a rate | **FE** |

**BillingFrequencyMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/billing-frequency` | Create | **FE** |
| PUT | `/api/billing-frequency/{billingFrequencyId}` | Update | **FE** |
| GET | `/api/billing-frequency/{billingFrequencyId}` | Get one | **FE** |
| GET | `/api/billing-frequency` | List all | **FE** |
| GET | `/api/billing-frequency/active` | List active records | **FE** |
| DELETE | `/api/billing-frequency/{billingFrequencyId}` | Delete | **FE** |
| PATCH | `/api/billing-frequency/{billingFrequencyId}/activate` | Activate | **FE** |

**BillingTypeMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/billing-types` | Create | **FE** |
| PUT | `/api/billing-types/{billingTypeId}` | Update | **FE** |
| GET | `/api/billing-types/{billingTypeId}` | Get one | **FE** |
| GET | `/api/billing-types` | List all | **FE** |
| GET | `/api/billing-types/active` | List active records | **FE** |
| DELETE | `/api/billing-types/{billingTypeId}` | Delete | **FE** |
| PATCH | `/api/billing-types/{billingTypeId}/activate` | Activate | **FE** |

**CurrencyMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/currency` | Create | **FE** |
| PUT | `/api/currency/{currencyId}` | Update | **FE** |
| GET | `/api/currency/{currencyId}` | Get one | **FE** |
| GET | `/api/currency` | List all | **FE** |
| GET | `/api/currency/active` | List active records | **FE** |
| DELETE | `/api/currency/{currencyId}` | Delete | **FE** |

**PaymentTermsMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/payment-terms` | Create | **FE** |
| PUT | `/api/payment-terms/{paymentTermId}` | Update | **FE** |
| GET | `/api/payment-terms/{paymentTermId}` | Get one | **FE** |
| GET | `/api/payment-terms` | List all | **FE** |
| GET | `/api/payment-terms/active` | List active records | **FE** |
| PUT | `/api/payment-terms/{paymentTermId}/activate` | Activate | **FE** |
| DELETE | `/api/payment-terms/{paymentTermId}` | Delete | **FE** |

**ProrationRuleMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/proration-rule` | Create | **FE** |
| PUT | `/api/proration-rule/{prorationRuleId}` | Update | **FE** |
| GET | `/api/proration-rule/{prorationRuleId}` | Get one | **FE** |
| GET | `/api/proration-rule` | List all | **FE** |
| GET | `/api/proration-rule/active` | List active records | **FE** |
| DELETE | `/api/proration-rule/{prorationRuleId}` | Delete | **FE** |

**TaxComponentMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/tax-component-master` | Create | **FE** |
| PUT | `/api/tax-component-master/{taxComponentId}` | Update | **FE** |
| GET | `/api/tax-component-master/{taxComponentId}` | Get one | **FE** |
| GET | `/api/tax-component-master` | List all | **FE** |
| GET | `/api/tax-component-master/regime/{taxRegimeId}` | Get one | **FE** |
| PATCH | `/api/tax-component-master/{taxComponentId}/deactivate` | Deactivate | **FE** |

**TaxConfigurationComponentController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | Create under a tax configuration | **FE** |
| PUT | `/api/tax-configuration-components/{taxConfigurationComponentId}` | Update | **FE** |
| GET | `/api/tax-configuration-components/{taxConfigurationComponentId}` | Get one | **FE** |
| GET | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | Get one | **FE** |
| GET | `/api/tax-configuration-components` | List all | **FE** |
| PATCH | `/api/tax-configuration-components/{taxConfigurationComponentId}/deactivate` | Deactivate | **FE** |

**TaxConfigurationController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/v1/tax-rate-configurations` | Create | **FE** |
| PUT | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | Update | **FE** |
| GET | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | Get one | **FE** |
| GET | `/api/v1/tax-rate-configurations` | List all | **FE** |
| GET | `/api/v1/tax-rate-configurations/active` | List active records | **FE** |
| GET | `/api/v1/tax-rate-configurations/tax-region/{taxRegionId}` | Get one | **FE** |
| PATCH | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}/deactivate` | Deactivate | **FE** |

**TaxRegimeMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/tax-regime-master` | Create | **FE** |
| PUT | `/api/tax-regime-master/{taxRegimeId}` | Update | **FE** |
| GET | `/api/tax-regime-master/{taxRegimeId}` | Get one | **FE** |
| GET | `/api/tax-regime-master` | List all | **FE** |
| GET | `/api/tax-regime-master/active` | List active records | **FE** |
| PATCH | `/api/tax-regime-master/{taxRegimeId}/deactivate` | Deactivate | **FE** |

**TaxRegionMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/tax-region` | Create | **FE** |
| PUT | `/api/tax-region/{taxRegionId}` | Update | **FE** |
| GET | `/api/tax-region/{taxRegionId}` | Get one | **FE** |
| GET | `/api/tax-region` | List all | **FE** |
| GET | `/api/tax-region/active` | List active records | **FE** |
| PATCH | `/api/tax-region/{taxRegionId}/deactivate` | Deactivate | **FE** |

**TaxStructureController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| GET | `/api/tax-structure/regions/{taxRegionId}` | Get one | **FE** |

**TaxTypeMasterController**

| Method | Endpoint | Purpose | Role |
|---|---|---|---|
| POST | `/api/tax-type-master` | Create | **FE** |
| PUT | `/api/tax-type-master/{taxTypeId}` | Update | **FE** |
| GET | `/api/tax-type-master/{taxTypeId}` | Get one | **FE** |
| GET | `/api/tax-type-master` | List all | **FE** |
| GET | `/api/tax-type-master/active` | List active records | **FE** |
| PATCH | `/api/tax-type-master/{taxTypeId}/deactivate` | Deactivate | **FE** |

### 7.5 Public

| Method | Endpoint | Role | Notes |
|---|---|---|---|
| GET | `/health` | **PUBLIC** | Already `permitAll`. Also public: `/actuator/health*`, `/swagger-ui/**`, `/v3/api-docs/**`. Consider restricting Swagger outside dev. |

---

## 8. How the proposed roles would be applied (when you implement)

Nothing is implemented here. For when you do:

1. **Create two authority constants** matching the UMS role names: `ROLE_FINANCE_EXECUTIVE`, `ROLE_FINANCE_MANAGER`.
2. **Use method security** (`@EnableMethodSecurity` is already on). Controller-level examples:
   - FE write endpoints: `@PreAuthorize("hasAnyRole('FINANCE_EXECUTIVE')")` (add `'FINANCE_MANAGER'` if D2 = yes)
   - FM endpoints: `@PreAuthorize("hasRole('FINANCE_MANAGER')")`
   - BOTH endpoints: `@PreAuthorize("hasAnyRole('FINANCE_EXECUTIVE','FINANCE_MANAGER')")`
3. **Keep the service-level `FinancialActionAuthorizer` checks.** They protect against non-controller entry points (automatic billing, schedulers) and apply maker-checker. The controller annotations are the first gate, not a replacement.
4. **Set the `AR_AUTH_*` env vars** from section 2. Without them approvals and recovery stay closed to everyone.
5. **Lock endpoint:** keep the `actionType`-based check for `APPROVAL`; add an FE/FM check for `EDIT`.
6. **Return codes:** 401 unauthenticated, 403 wrong role (existing `CustomAccessDeniedHandler`), 409 lock conflict (existing).
7. **Tests:** a role × endpoint test per row group (FE denied on FM endpoints, FM denied on FE writes if D2 = no, maker cannot approve own, non-owner cannot approve without lock).

---

## 9. Open decisions

| ID | Question | Recommended default | Alternative |
|---|---|---|---|
| **D1** | Who sends an approved invoice to the client? | **DECIDED: Finance Executive (FE).** The manager signs off at approval; FE sends the email to the client. | BOTH, or FM only, if you later want cover or a second control on the outward step. |
| **D2** | May FM also do FE's write work (cover for absence)? | **No.** Keeps duties separate and maker-checker meaningful. | Yes, add FM to every FE write row. |
| **D7** | Is the AR dashboard (`/api/v1/dashboard/**`) shared or FM-only? | **BOTH** (kept shared; the overview page is used by both). | FM-only, if "complete receivables" should include the dashboard. |
| **D3** | May FE see the manager queues (`pending-approvals`, `pending-approval`, `approval-workspace`)? | **DECIDED: FM only.** FE tracks their own items through the list/detail/history GETs. | BOTH, so FE can see what is waiting. |
| **D4** | Operations reports (`/api/v1/operations/**`): FM only or also FE? | **FM only** (they carry client and financial data). | BOTH. |
| **D5** | Reference data writes (tax, currency, billing types, exchange rates, company profile text): FM or FE? | **DECIDED: FE.** The manager only takes action (approve / reject) and owns receivables; all master data is the executive's. | FM, if you later want a second control on tax and currency changes. |
| **D6** | Does the UMS role name match `FINANCE_EXECUTIVE` / `FINANCE_MANAGER`? | Matching ignores case, spaces, hyphens. | If UMS sends permissions (not roles), list those in the `AR_AUTH_*` variables instead. |

---

## 10. Gaps found during the analysis

1. **Only four actions are role-checked today** (config approval, invoice approval, delivery, recovery). The other ~190 endpoints accept any authenticated user, including FE-only writes and the manager queues.
2. **`PATCH /api/billing-configurations/{id}/deactivate`** acts on an approved configuration with no role check, so it can undo an approval without the manager.
3. **Reference-data writes have no role check**, so any logged-in user can change tax rates, currencies, or exchange rates.
4. **EDIT locks have no role check**, so any authenticated user can hold a record and block the executive working on it.
5. **`InvoiceApprovalController` javadoc says** "no Finance-Manager-specific restriction exists yet". That is true only for the controller; the service does enforce `INVOICE_APPROVAL` on approve, reject, and finalize. The submit, refresh, correction, and queue endpoints have no check.
6. **Empty authority lists mean nobody**: until `AR_AUTH_*` are set, approval and recovery fail with 403 for everyone, including the manager.
7. **`/client-budget-summary/**`** is outside the `/api/**` prefix; harmless today but easy to miss if a future rule targets `/api/**` only.
8. **Two role-verification paths coexist** (Spring JWT roles vs. `FinanceExecutiveAuthorizer` with its own key config). Signature endpoints use the second. Worth consolidating when RBAC is implemented.

---

---

## 11. Complete endpoint index (per controller, generated from the code)

Use this to tick off coverage. Role is the value used in the sections above.


**ArDashboardController** (2)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/dashboard/summary` | BOTH |
| GET | `/api/v1/dashboard/invoices` | BOTH |

**AutomaticBillingRunRecoveryController** (3)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/requeue` | FM |
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval` | FM |
| GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | FM |

**BillingConfigurationController** (17)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-configurations` | FE |
| PUT | `/api/billing-configurations/{billingConfigurationId}` | FE |
| POST | `/api/billing-configurations/preview-schedule` | FE |
| GET | `/api/billing-configurations/project/{projectId}` | FE |
| PUT | `/api/billing-configurations/{billingConfigurationId}/approve` | FM |
| GET | `/api/billing-configurations/clients` | FE |
| GET | `/api/billing-configurations/projects/{clientId}` | BOTH |
| GET | `/api/billing-configurations/{billingConfigurationId}` | BOTH |
| GET | `/api/billing-configurations` | BOTH |
| PATCH | `/api/billing-configurations/{billingConfigurationId}/deactivate` | FM |
| PUT | `/api/billing-configurations/{billingConfigurationId}/reject` | FM |
| DELETE | `/api/billing-configurations/{billingConfigurationId}` | FE |
| POST | `/api/billing-configurations/draft` | FE |
| PUT | `/api/billing-configurations/{billingConfigurationId}/draft` | FE |
| PUT | `/api/billing-configurations/{billingConfigurationId}/submit` | FE |
| GET | `/api/billing-configurations/pending-approvals` | FM |
| GET | `/api/billing-configurations/available-projects` | BOTH |

**BillingDataAcquisitionController** (2)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/billing-data-acquisition/active-configurations` | FE |
| POST | `/api/billing-data-acquisition/acquire` | FE |

**BillingFixedPriceController** (5)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | FE |
| PUT | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | FE |
| GET | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | FE |
| GET | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | FE |
| DELETE | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | FE |

**BillingFrequencyMasterController** (7)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-frequency` | FE |
| PUT | `/api/billing-frequency/{billingFrequencyId}` | FE |
| GET | `/api/billing-frequency/{billingFrequencyId}` | FE |
| GET | `/api/billing-frequency` | FE |
| GET | `/api/billing-frequency/active` | FE |
| DELETE | `/api/billing-frequency/{billingFrequencyId}` | FE |
| PATCH | `/api/billing-frequency/{billingFrequencyId}/activate` | FE |

**BillingMilestonePlanController** (5)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | FE |
| PUT | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | FE |
| GET | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | FE |
| GET | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | BOTH |
| DELETE | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | FE |

**BillingOccurrenceController** (5)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/billing-occurrences/{occurrenceId}` | BOTH |
| GET | `/api/billing-occurrences` | FE |
| GET | `/api/billing-occurrences/{occurrenceId}/tax-calculation` | BOTH |
| POST | `/api/billing-occurrences/{occurrenceId}/calculate-tax` | FE |
| POST | `/api/billing-occurrences/{occurrenceId}/invoice` | FE |

**BillingRecurringController** (8)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-recurring/{billingConfigurationId}` | FE |
| PUT | `/api/billing-recurring/{recurringConfigurationId}` | FE |
| GET | `/api/billing-recurring/{recurringConfigurationId}` | FE |
| GET | `/api/billing-recurring/billing-configuration/{billingConfigurationId}` | FE |
| GET | `/api/billing-recurring/{recurringConfigurationId}/schedule` | FE |
| DELETE | `/api/billing-recurring/{recurringConfigurationId}` | FE |
| POST | `/api/billing-recurring/{recurringConfigurationId}/renew` | FE |
| GET | `/api/billing-recurring/{recurringConfigurationId}/renewal-history` | FE |

**BillingSnapshotController** (3)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/billing-snapshots` | FE |
| GET | `/api/v1/billing-snapshots/by-period` | FE |
| GET | `/api/v1/billing-snapshots` | FE |

**BillingTMRateCardController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | FE |
| PUT | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | FE |
| GET | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | FE |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | FE |
| DELETE | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | FE |
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards/save` | FE |

**BillingTMRateDiscountController** (5)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | FE |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | BOTH |
| GET | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | FE |
| PUT | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | FE |
| DELETE | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | FE |

**BillingTypeMasterController** (7)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/billing-types` | FE |
| PUT | `/api/billing-types/{billingTypeId}` | FE |
| GET | `/api/billing-types/{billingTypeId}` | FE |
| GET | `/api/billing-types` | FE |
| GET | `/api/billing-types/active` | FE |
| DELETE | `/api/billing-types/{billingTypeId}` | FE |
| PATCH | `/api/billing-types/{billingTypeId}/activate` | FE |

**ClientBudgetSummaryController** (2)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/client-budget-summary/{clientId}` | FE |
| POST | `/client-budget-summary/refresh/{clientId}` | FE |

**CompanyProfileController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/company-profile` | FE |
| PUT | `/api/v1/company-profile/{companyProfileId}` | FE |
| GET | `/api/v1/company-profile/{companyProfileId}` | FE |
| GET | `/api/v1/company-profile/invoice-content-defaults` | FE |
| PUT | `/api/v1/company-profile/invoice-content-defaults` | FE |
| GET | `/api/v1/company-profile` | BOTH |

**CompanySignatureController** (4)

| Method | Endpoint | Role |
|---|---|---|
| PUT | `/api/v1/company-profile/{companyProfileId}/signature` | FE |
| GET | `/api/v1/company-profile/{companyProfileId}/signature` | BOTH |
| DELETE | `/api/v1/company-profile/{companyProfileId}/signature` | FE |
| GET | `/api/v1/company-profile/signature-assets/{signatureReference}` | BOTH |

**CurrencyMasterController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/currency` | FE |
| PUT | `/api/currency/{currencyId}` | FE |
| GET | `/api/currency/{currencyId}` | FE |
| GET | `/api/currency` | FE |
| GET | `/api/currency/active` | FE |
| DELETE | `/api/currency/{currencyId}` | FE |

**ExchangeRateController** (5)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/exchange-rates` | FE |
| POST | `/api/v1/exchange-rates/batch` | FE |
| POST | `/api/v1/exchange-rates` | FE |
| GET | `/api/v1/exchange-rates/{id}` | FE |
| GET | `/api/v1/exchange-rates/lookup` | FE |

**HealthController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/health` | PUBLIC |

**InvoiceApprovalController** (11)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/invoices/{invoiceId}/submit-for-approval` | FE |
| POST | `/api/v1/invoices/{invoiceId}/approve` | FM |
| POST | `/api/v1/invoices/{invoiceId}/document` | FM |
| GET | `/api/v1/invoices/{invoiceId}/document` | BOTH |
| POST | `/api/v1/invoices/{invoiceId}/reject` | FM |
| POST | `/api/v1/invoices/{invoiceId}/refresh-after-correction` | FE |
| PATCH | `/api/v1/invoices/{invoiceId}/non-financial-correction` | FE |
| POST | `/api/v1/invoices/{invoiceId}/financial-correction/reacquire` | FE |
| GET | `/api/v1/invoices/{invoiceId}/approval-history` | BOTH |
| GET | `/api/v1/invoices/pending-approval` | FM |
| GET | `/api/v1/invoices/approval-workspace` | FM |

**InvoiceController** (2)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/billing-snapshots/{snapshotId}/invoice` | FE |
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice` | BOTH |

**InvoiceDeliveryController** (2)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/invoices/{invoiceId}/send` | FE |
| GET | `/api/v1/invoices/{invoiceId}/deliveries` | BOTH |

**InvoiceGenerationWorkspaceController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/invoice-generation/workspace` | FE |

**InvoiceListController** (2)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/invoices` | FE |
| GET | `/api/v1/invoices/{invoiceId}` | BOTH |

**InvoicePreviewController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice-preview` | FE |

**InvoiceRecoveryController** (3)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax` | FM |
| POST | `/api/v1/recovery/invoices/{invoiceId}/receivable` | FM |
| POST | `/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` | FM |

**InvoiceSoftwareSelectionController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/invoice/software-selection/projects/{projectId}` | FE |

**OperationsReportController** (3)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/operations/billing-summary` | FM |
| GET | `/api/v1/operations/reconciliation` | FM |
| GET | `/api/v1/operations/alerts` | FM |

**PaymentTermsMasterController** (7)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/payment-terms` | FE |
| PUT | `/api/payment-terms/{paymentTermId}` | FE |
| GET | `/api/payment-terms/{paymentTermId}` | FE |
| GET | `/api/payment-terms` | FE |
| GET | `/api/payment-terms/active` | FE |
| PUT | `/api/payment-terms/{paymentTermId}/activate` | FE |
| DELETE | `/api/payment-terms/{paymentTermId}` | FE |

**ProjectMilestoneController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/projects/{pmsProjectId}/milestones` | FE |

**ProjectToolAssignmentController** (7)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/project-tool-assignments` | FE |
| GET | `/api/project-tool-assignments/project/{projectId}` | FE |
| GET | `/api/project-tool-assignments/{id}` | FE |
| POST | `/api/project-tool-assignments` | FE |
| PUT | `/api/project-tool-assignments/{id}` | FE |
| DELETE | `/api/project-tool-assignments/{id}` | FE |
| POST | `/api/project-tool-assignments/{id}/renew` | FE |

**ProjectToolBillingConfigController** (3)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/ar/projects/{projectId}/tool-billing` | FE |
| POST | `/api/ar/projects/{projectId}/tool-billing` | FE |
| PUT | `/api/ar/projects/{projectId}/tool-billing` | FE |

**ProrationRuleMasterController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/proration-rule` | FE |
| PUT | `/api/proration-rule/{prorationRuleId}` | FE |
| GET | `/api/proration-rule/{prorationRuleId}` | FE |
| GET | `/api/proration-rule` | FE |
| GET | `/api/proration-rule/active` | FE |
| DELETE | `/api/proration-rule/{prorationRuleId}` | FE |

**ReceivableController** (2)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/v1/receivables/outstanding` | FM |
| GET | `/api/v1/receivables/{receivableId}` | FM |

**RecordActionLockController** (4)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/record-locks/{resourceType}/{resourceId}` | BOTH |
| GET | `/api/record-locks/{resourceType}/{resourceId}` | BOTH |
| PUT | `/api/record-locks/{resourceType}/{resourceId}/heartbeat` | BOTH |
| DELETE | `/api/record-locks/{resourceType}/{resourceId}` | BOTH |

**RmsAssetIntegrationController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/rms-assets/projects/{projectId}/billable-assets` | FE |

**SoftwareBillingHistoryController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/software-billing-history/assets/{assetId}` | FE |

**SoftwareChargeGenerationController** (1)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/invoice/software-charge-generation` | FE |

**TaxCalculationController** (2)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | FE |
| GET | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | BOTH |

**TaxComponentMasterController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tax-component-master` | FE |
| PUT | `/api/tax-component-master/{taxComponentId}` | FE |
| GET | `/api/tax-component-master/{taxComponentId}` | FE |
| GET | `/api/tax-component-master` | FE |
| GET | `/api/tax-component-master/regime/{taxRegimeId}` | FE |
| PATCH | `/api/tax-component-master/{taxComponentId}/deactivate` | FE |

**TaxConfigurationComponentController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | FE |
| PUT | `/api/tax-configuration-components/{taxConfigurationComponentId}` | FE |
| GET | `/api/tax-configuration-components/{taxConfigurationComponentId}` | FE |
| GET | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | FE |
| GET | `/api/tax-configuration-components` | FE |
| PATCH | `/api/tax-configuration-components/{taxConfigurationComponentId}/deactivate` | FE |

**TaxConfigurationController** (7)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/v1/tax-rate-configurations` | FE |
| PUT | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | FE |
| GET | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | FE |
| GET | `/api/v1/tax-rate-configurations` | FE |
| GET | `/api/v1/tax-rate-configurations/active` | FE |
| GET | `/api/v1/tax-rate-configurations/tax-region/{taxRegionId}` | FE |
| PATCH | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}/deactivate` | FE |

**TaxRegimeMasterController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tax-regime-master` | FE |
| PUT | `/api/tax-regime-master/{taxRegimeId}` | FE |
| GET | `/api/tax-regime-master/{taxRegimeId}` | FE |
| GET | `/api/tax-regime-master` | FE |
| GET | `/api/tax-regime-master/active` | FE |
| PATCH | `/api/tax-regime-master/{taxRegimeId}/deactivate` | FE |

**TaxRegionMasterController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tax-region` | FE |
| PUT | `/api/tax-region/{taxRegionId}` | FE |
| GET | `/api/tax-region/{taxRegionId}` | FE |
| GET | `/api/tax-region` | FE |
| GET | `/api/tax-region/active` | FE |
| PATCH | `/api/tax-region/{taxRegionId}/deactivate` | FE |

**TaxStructureController** (1)

| Method | Endpoint | Role |
|---|---|---|
| GET | `/api/tax-structure/regions/{taxRegionId}` | FE |

**TaxTypeMasterController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tax-type-master` | FE |
| PUT | `/api/tax-type-master/{taxTypeId}` | FE |
| GET | `/api/tax-type-master/{taxTypeId}` | FE |
| GET | `/api/tax-type-master` | FE |
| GET | `/api/tax-type-master/active` | FE |
| PATCH | `/api/tax-type-master/{taxTypeId}/deactivate` | FE |

**ToolCatalogController** (6)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tool-catalog` | FE |
| PUT | `/api/tool-catalog/{toolId}` | FE |
| GET | `/api/tool-catalog/{toolId}` | FE |
| GET | `/api/tool-catalog` | FE |
| GET | `/api/tool-catalog/active` | FE |
| DELETE | `/api/tool-catalog/{toolId}` | FE |

**ToolChargeAcquisitionController** (1)

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/tool-charge-acquisition/preview` | FE |

### PATCH endpoints (all 10)

| Endpoint | Role |
|---|---|
| `PATCH /api/v1/invoices/{invoiceId}/non-financial-correction` | FE |
| `PATCH /api/billing-configurations/{billingConfigurationId}/deactivate` | FM |
| `PATCH /api/billing-frequency/{billingFrequencyId}/activate` | FE |
| `PATCH /api/billing-types/{billingTypeId}/activate` | FE |
| `PATCH /api/tax-component-master/{taxComponentId}/deactivate` | FE |
| `PATCH /api/tax-configuration-components/{taxConfigurationComponentId}/deactivate` | FE |
| `PATCH /api/v1/tax-rate-configurations/{taxRateConfigurationId}/deactivate` | FE |
| `PATCH /api/tax-regime-master/{taxRegimeId}/deactivate` | FE |
| `PATCH /api/tax-region/{taxRegionId}/deactivate` | FE |
| `PATCH /api/tax-type-master/{taxTypeId}/deactivate` | FE |

---

## Appendix: how this was produced

Every `@RestController` (48 classes) was scanned for class- and method-level mappings (205 endpoints; GET/POST/PUT/PATCH/DELETE all counted) and the result was diffed against this file, so each endpoint appears at least once. Approval, lock, delivery, and recovery behaviour was confirmed by reading `FinancialActionAuthorizer`, `FinancialAction`, `RecordActionLockServiceImpl`, `BillingConfigurationServiceImpl`, `InvoiceServiceImpl`, `InvoiceDeliveryServiceImpl`, and `SecurityConfig`. Role assignments for endpoints with no existing check are **proposals** based on your described workflow; the "Enforced today?" columns state what is real.
