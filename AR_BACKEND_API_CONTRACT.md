# AR Automatic Billing — Backend API Contract for the Frontend

| | |
|---|---|
| Branch / base commit | `feature/ar-automatic-billing` @ `2eeeb4a` + Phase 4A working-tree fixes (uncommitted) |
| Verified on | 2026-10-22 (Phase 4A backend integration audit) |
| Companion document | `AR_AUTOMATIC_BILLING_BACKEND_SPEC.md` — full endpoint inventory (§4.3), configuration DTOs (§4.4), status matrices (§6). This document re-verifies the invoice / delivery / occurrence contract against the controllers and supersedes the spec where they differ. |

**Evidence labels** used below:
**[HTTP-TEST]** asserted by a MockMvc/controller test · **[H2-TEST]** asserted by a Spring Boot test on an in-memory H2 database (MySQL mode) · **[MOCK-TEST]** asserted by a Mockito unit test · **[CODE]** read from source only, no test.
Nothing in this document was verified against a running backend with real MySQL, UMS, TMS or SMTP.

---

## 1. Conventions

### 1.1 Base URL
- Configure **one** base URL: `http://localhost:8080` locally, `https://<host>/paves/ar` in k8s. All paths below start with `/api/...` and are appended to it. `/paves/ar` is a servlet context path, not part of any controller mapping [CODE].
- `InvoiceResponseDto.documentUrl` is the relative path `/api/v1/invoices/{id}/document` — prefix it with the base URL [CODE].

### 1.2 Authentication
Every endpoint below requires `Authorization: Bearer <UMS JWT>` [HTTP-TEST `SecurityConfigurationTest`].
- 401 — missing/invalid/expired token (body `{"detail":"401: Token has expired"}` on expiry, otherwise `ApiResponse` "Authentication failed: …").
- Financial actions additionally need an authority configured on the backend (`AR_AUTH_*`, comma-separated `ROLE_<role>` or permission names). **Empty = nobody may perform the action** — the default in every profile. Missing authority → 403 [MOCK-TEST `FinancialActionAuthorizerTest`].

| Action | Backend setting |
|---|---|
| Invoice approve / reject / generate document | `AR_AUTH_INVOICE_APPROVAL` |
| Invoice send | `AR_AUTH_INVOICE_DELIVERY` |
| Recovery endpoints | `AR_AUTH_OPERATIONS_RECOVERY` |

### 1.3 Response envelope
Most endpoints return `ApiResponse<T>`:
```json
{ "success": true, "message": "Invoice retrieved successfully.", "data": { ... }, "timestamp": null }
```
**Exception in this contract:** `GET /api/billing-occurrences/{id}` returns the DTO **unwrapped** (no envelope). `GET /api/v1/invoices/{id}/document` returns raw PDF bytes.

### 1.4 Errors (`GlobalExceptionHandler`) [CODE; individual cases MOCK-TEST]
Error body: `{ "success": false, "message": "<reason>", "data": null, "timestamp": null }`.

| HTTP | When |
|---|---|
| 400 | Business validation (wrong status, missing data, duplicates — **`DuplicateResourceException` is 400, not 409**), `IllegalStateException`, `IllegalArgumentException`, bean validation (first field error only) |
| 403 | Missing financial authority; maker-checker (approver = submitter) |
| 404 | Unknown id; unmapped path |
| 409 | Record lock missing/expired/held by someone else; optimistic-lock conflict |
| 500 | Anything else — including malformed JSON, an invalid enum value or a non-UUID path id (not specially mapped). Validate ids/enums client-side. |

Display the `message` of 400/403/409 to the user; treat 500 as "contact support".

### 1.5 Types
UUIDs as strings; `LocalDate` = `"YYYY-MM-DD"`; `LocalDateTime` = `"YYYY-MM-DDTHH:mm:ss[.SSS]"` **without zone** (server JVM time zone — not configured; see BD-10); money = JSON number with 2 decimals (`BigDecimal`).

---

## 2. Endpoint contract (the seven endpoints from the frontend audit)

All seven exist with exactly the expected method and path. No differences in path were found.

| # | Method & path | Controller | Response | Authority |
|---|---|---|---|---|
| E1 | `GET /api/v1/invoices/{invoiceId}` | `InvoiceListController` | `ApiResponse<InvoiceResponseDto>` | JWT |
| E2 | `POST /api/v1/invoices/{invoiceId}/approve` | `InvoiceApprovalController` | `ApiResponse<InvoiceResponseDto>` | INVOICE_APPROVAL + APPROVAL record lock |
| E3 | `POST /api/v1/invoices/{invoiceId}/document` | `InvoiceApprovalController` | `ApiResponse<InvoiceResponseDto>` | INVOICE_APPROVAL |
| E4 | `GET /api/v1/invoices/{invoiceId}/document` | `InvoiceApprovalController` | `application/pdf` bytes | JWT |
| E5 | `GET /api/v1/invoices/{invoiceId}/deliveries` | `InvoiceDeliveryController` | `ApiResponse<List<InvoiceDeliveryResponseDto>>` | JWT |
| E6 | `POST /api/v1/invoices/{invoiceId}/send` | `InvoiceDeliveryController` | `ApiResponse<InvoiceDeliveryResponseDto>` | INVOICE_DELIVERY |
| E7 | `GET /api/billing-occurrences/{occurrenceId}` | `BillingOccurrenceController` | **unwrapped** `BillingOccurrenceResponseDto` | JWT |

None of them takes a request body.

### E1 — Get invoice
- 200 with the full invoice (header, `items`, `taxComponents`, totals, `documentAvailable`, `documentUrl`). 404 if unknown. [MOCK-TEST `InvoiceServiceImplTest`]
- Use it as the single source of truth after every action (approve, document, send) — the action responses are the same DTO but re-fetching avoids stale state.

### E2 — Approve
**Precondition — record lock:** the approving user must hold an APPROVAL lock:
```
POST   /api/record-locks/INVOICE/{invoiceId}   body {"actionType":"APPROVAL"}   (10 min)
PUT    /api/record-locks/INVOICE/{invoiceId}/heartbeat                          (extend)
DELETE /api/record-locks/INVOICE/{invoiceId}                                    (204, release — approve does NOT release it)
```
Behaviour [MOCK-TEST `InvoiceServiceImplDocumentFinalizationTest`, `InvoiceApprovalControllerDocumentTest`; H2-TEST `AutomaticBillingEndToEndTest`]:
1. Requires INVOICE_APPROVAL (else 403), the lock (else 409), status `PENDING_APPROVAL` (else 400 "Only invoices pending approval can be approved."), approver ≠ submitter (else 403).
2. Commits `APPROVED`. Then, in a **separate transaction**, renders the PDF and moves the invoice to `READY_TO_SEND`.
3. **Always 200 once the approval committed**, even if the PDF failed. Distinguish the two outcomes by `data`:

| `data.status` | `data.documentAvailable` | Meaning | UI |
|---|---|---|---|
| `READY_TO_SEND` | `true` | Approved and document generated | Enable **Send** |
| `APPROVED` | `false` | Approved, PDF failed; `message` explains | Show message; offer **Generate document** (E3) |

- Maker-checker: invoices **submitted by automation** have no human maker, so any user with INVOICE_APPROVAL may approve them [CODE `FinancialActionAuthorizer.requireDifferentFromMaker`, MOCK-TEST `_nullMaker_succeeds`].
- If the invoice came from an automatic billing run, the run is marked `COMPLETED` after the approval commits (not visible through any frontend API).

### E3 — Generate document (retry)
- `APPROVED` → `READY_TO_SEND`. Idempotent: on an invoice already `READY_TO_SEND` with a document it returns 200 unchanged [MOCK-TEST `alreadyFinalizedInvoice_isReturnedWithoutGeneratingAnotherDocument`].
- 400 when the invoice is not `APPROVED`/`READY_TO_SEND` or its data fails document validation (message explains); 403 without INVOICE_APPROVAL; 404 unknown; 500 on a PDF rendering error.

### E4 — Download PDF
- 200 `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="<file>.pdf"` (header is CORS-exposed) [MOCK-TEST `downloadDocument_returnsTheStoredPdf`].
- **404** when the invoice is not `READY_TO_SEND` or has no stored document [CODE]. Fetch it with the bearer token (e.g. `fetch` → `Blob`), not a plain `<a href>`.
- The stored PDF is exactly what is emailed on send; it remains downloadable after sending.

### E5 — Delivery history
- 200 with every delivery attempt for the invoice, **newest first** (`createdAt` desc); the current delivery state is element `[0]`; empty list = never sent. 404 unknown invoice [MOCK-TEST `getDeliveryHistory_returnsAttemptsNewestFirst`, `getDeliveryHistory_invoiceNotFound_throwsResourceNotFoundException`].
- This is the source of truth for "has this invoice been sent" — `Invoice.status` stays `READY_TO_SEND` after sending (there is no `SENT` invoice status).

### E6 — Send to client
Sends the stored PDF by email (SMTP) to the invoice email, else the client email.

**Pre-send refusals — 4xx, nothing sent, no delivery row** [MOCK-TEST `InvoiceDeliveryServiceImplTest`; H2-TEST `InvoiceDeliveryPersistenceTest`]:
- 400: invoice not `READY_TO_SEND`, no stored document, no recipient email, no active company profile, inconsistent totals; **already SENT** ("already been sent" — resend is not supported, BD-06); a delivery `PENDING` or `UNCERTAIN` ("in progress or its outcome is unknown").
- 403 without INVOICE_DELIVERY; 404 unknown invoice.

**After the attempt — always HTTP 200**; read `data.deliveryStatus` (and `success`):

| `deliveryStatus` | `success` | Meaning | UI |
|---|---|---|---|
| `SENT` | `true` | Email accepted by the mail server; receivable created | Show sent; link to receivable |
| `FAILED` | `false` | Definitely not delivered; `failureReason` set | Show reason; **Send** may be retried |
| `UNCERTAIN` | `false` | Mail server may have accepted it (e.g. read timeout after transmission) | "Outcome unknown — do not resend; operations must resolve". Block Send. |

- Double-click / concurrent send: exactly one email and one receivable [H2-TEST `concurrentSends_ofTheSameInvoice_sendOneEmail_andCreateOneReceivable`]; the second call gets 400.
- If the email left but the backend could not record the outcome, the delivery stays `PENDING` and the call returns **400** with an "outcome unknown" message (not 5xx) [MOCK-TEST `sendInvoice_outcomeCannotBeRecorded_staysPendingAndIsNotResent`]. Treat like `UNCERTAIN`.
- Operators resolve `UNCERTAIN` / stale `PENDING` (>10 min) with `POST /api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` `{ "reason": "...", "outcome": "SENT" | "FAILED" }` (OPERATIONS_RECOVERY).

**Receivable on success** [H2-TEST `InvoiceDeliveryPersistenceTest`, `AutomaticBillingEndToEndTest`; MOCK-TEST `ReceivableServiceImplTest`]:
- Exactly one receivable per invoice (unique key `uk_receivable_invoice`), created right after `SENT` is recorded, in its own transaction.
- `invoiceId`/`invoiceNumber` reference the invoice; `invoiceAmount` = `outstandingBalance` = invoice `grandTotal`; `currencyCode`, `invoiceDate`, `dueDate` copied; `status = OUTSTANDING`.
- A `FAILED`/`UNCERTAIN` delivery never creates one. Resolving a delivery to `SENT` creates it. If creation fails after a successful send, a job retries every 15 min (operator endpoint `POST /api/v1/recovery/invoices/{invoiceId}/receivable` also available).
- Read it with `GET /api/v1/receivables/outstanding?…` or `GET /api/v1/receivables/{receivableId}`. There is **no** "receivable by invoice id" endpoint — filter the outstanding list client-side by `invoiceId`.
- No payment/settlement endpoints exist (`PARTIALLY_PAID`/`SETTLED` are never set).

### E7 — Get billing occurrence
- **Unwrapped** 200 `BillingOccurrenceResponseDto`; 404 `{success:false,…}` if unknown [CODE]. Returns inactive occurrences too (check `isActive`).
- Occurrences exist for Fixed Price, Milestone Plan (full payment and installments) and Recurring — created when the configuration is **approved**. **Time & Material ("Timesheet Based") has no occurrences**; its unit of billing is the billing snapshot (`/api/v1/billing-snapshots/...`).
- Link to the invoice: `InvoiceResponseDto.billingScheduleId` = occurrence id (null for T&M invoices, which carry `billingSnapshotId`). There is no "invoice by occurrence" endpoint; `GET /api/v1/invoices` lists summaries.

---

## 3. DTOs (verified field lists)

**`InvoiceResponseDto`**: `invoiceId`, `invoiceNumber`, `status`, `billingSnapshotId`, `billingScheduleId`, `billingSnapshotNumber`, `taxCalculationId`, `clientId`, `generated`, `projectCode`, `clientName`, `countryCode`, `clientCountryName`, `clientCountryCode`, `billingAddress`, `gstinOrTaxId`, `contact`, `projectId`, `projectName`, `projectStartDate`, `projectEndDate`, `billingPeriodStart`, `billingPeriodEnd`, `currencyCode`, `paymentTermCode`, `paymentTermName`, `email`, `phone`, `sellerLegalName`, `sellerAddressLine1`, `sellerAddressLine2`, `sellerCity`, `sellerState`, `sellerPostalCode`, `sellerCountry`, `sellerGstin`, `sellerEmail`, `sellerPhone`, `sellerLogoReference`, `invoiceNotes`, `termsAndConditions`, `paymentInstructions`, `taxRegionCode`, `sourceTaxJurisdictionCode`, `destinationTaxJurisdictionCode`, `invoiceDate`, `dueDate` (may be **null** if the configuration has no payment term), `items[]`, `taxComponents[]`, `subtotal`, `totalTaxAmount`, `grandTotal`, `generatedAt`, `correctionRequired`, `lastCorrectedAt`, `documentAvailable`, `documentUrl`.
- `items[]` (`InvoiceItemResponseDto`): `invoiceItemId`, `lineNumber`, `itemType`, `itemName`, `sourceReferenceId`, `resourceName`, `quantity`, `rate`, `amount`, `workDate`, `role`.
- `taxComponents[]` (`InvoiceTaxComponentResponseDto`): `invoiceTaxComponentId`, `taxTypeId`, `taxTypeCode`, `taxTypeName`, `appliedRate`, `taxAmount`, `applicabilityType`.
- `documentAvailable` = `status == READY_TO_SEND` **and** a stored document exists.
- No field says whether the invoice was created automatically.

**`InvoiceDeliveryResponseDto`**: `deliveryId`, `invoiceId`, `invoiceNumber`, `recipientEmail`, `deliveryStatus`, `sentAt`, `failedAt`, `failureReason`, `createdAt`, `requestedBy`, `resolvedAt`, `resolvedBy`, `resolutionReason`.

**`BillingOccurrenceResponseDto`**: `billingScheduleId`, `billingConfigurationId`, `recurringConfigurationId`, `periodNumber`, `periodStartDate`, `periodEndDate`, `billingDate`, `billingAmount`, `scheduleType`, `isPartialPeriod`, `periodStatus`, `taxStatus`, `isInvoiced`, `invoiceDate`, `remarks`, `isActive`, `createdAt`, `updatedAt`, `taxCalculationId`, `taxCalculationStatus`, `taxableAmount`, `totalTaxAmount`, `grandTotal`, `taxCalculatedAt`, `taxComponents[]`, `projectName`, `clientName`, `currencyCode`, `taxRegionName`, `taxRegionCode`, `clientCountryName`, `clientCountryCode`, `projectStartDate`, `projectEndDate`.

**`ReceivableResponseDto`**: `receivableId`, `invoiceId`, `invoiceNumber`, `clientId`, `clientName`, `projectId`, `projectName`, `currencyCode`, `invoiceDate`, `dueDate`, `invoiceAmount`, `outstandingBalance`, `status`, `daysOutstanding`, `daysOverdue`, `createdAt`, `updatedAt`.

## 4. Status enums (exact values)

| Enum | Values | Notes |
|---|---|---|
| `InvoiceStatus` | `GENERATED`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `READY_TO_SEND` | No `SENT` — use E5 |
| `InvoiceDeliveryStatus` | `PENDING`, `SENT`, `FAILED`, `UNCERTAIN` | |
| `BillingPeriodStatus` (`periodStatus`, `taxStatus`) | `SCHEDULED`, `TAX_PENDING`, `TAX_CALCULATED`, `INVOICED` (+ `PENDING`, `CANCELLED` defined, not written) | Handle unknowns defensively |
| `ReceivableStatus` | `OUTSTANDING` (+ `PARTIALLY_PAID`, `SETTLED` never set) | |

## 5. Lifecycle the UI drives

```
[automation, if enabled + config AUTOMATIC]  or  [user, manual endpoints]
  occurrence/snapshot → tax → invoice GENERATED → PENDING_APPROVAL
Finance Manager:  lock (POST /api/record-locks/INVOICE/{id})
                  → E2 approve → READY_TO_SEND (documentAvailable=true)
                                 or APPROVED (documentAvailable=false) → E3 → READY_TO_SEND
                  → release lock
Sender:           E6 send → SENT (receivable OUTSTANDING) | FAILED (retry E6) | UNCERTAIN (operations)
Anyone:           E1 invoice, E4 PDF, E5 delivery history
```
**"Ready to send" in the UI** = `status == READY_TO_SEND && documentAvailable && (deliveries empty || deliveries[0].deliveryStatus == FAILED)`.

Automation stops at `PENDING_APPROVAL`; it never approves or sends. The feature flag (`AR_AUTOMATIC_BILLING_ENABLED`, default `false`) and automatic-run state are **not exposed** to the frontend.

## 6. Known backend issues affecting the frontend (Phase 4A)

| ID | Status | Frontend impact |
|---|---|---|
| AUD-4A-01 | **Fixed** (regression test) | Rejecting an edit of an approved configuration failed with HTTP 500 after a previous edit had been approved. |
| AUD-4A-02 | **Fixed** (regression tests) | That rejection could silently revert the earlier *approved* edit. |
| AUD-4A-03 | **Fixed** (regression tests) | An SMTP read timeout after transmission was shown as `FAILED` (resend allowed → possible duplicate email); now `UNCERTAIN`. |
| AUD-4A-04 | Open (design) | Rejecting an edit restores only top-level configuration fields and milestone payment entries. Fixed Price, Recurring and T&M rate-card edits are **not** restored, and the configuration ends `REJECTED/INACTIVE` (automatic billing stops) rather than returning to its last approved state. Show a warning before editing an approved configuration. |
| AUD-4A-05 | Open (red test, Finance decision) | A Milestone/Installment plan can be edited after an installment was invoiced; re-approval then bills more or less than the contract. UI should block/warn editing a milestone plan with any `isInvoiced` occurrence. |
| AUD-4A-06 | Open | Rejecting after several milestone edits in one approval cycle can reactivate entries from earlier edits (inferred from code; untested). |
| — | Open | `POST /send` returns 200 for `FAILED`/`UNCERTAIN`; never treat HTTP 200 as "sent". |
| — | Open | Malformed ids / enums → 500 (validate client-side). |

## 7. Test evidence (2026-10-22, local, `./mvnw test`, H2 only)

| Run | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| HEAD `2eeeb4a` before Phase 4A changes | 923 | 1 (`effectiveFromChange_WithInvoiced_ThrowsException`, deliberately red, BD-05) | 0 | 3 (`MySqlIntegrationTest` — Docker unavailable) |
| After Phase 4A fixes and regression tests | 930 | 2 (BD-05 above + `installmentEditAfterAnInvoice_neverBillsMoreOrLessThanTheContract`, deliberately red, AUD-4A-05) | 0 | 3 (same) |

Not tested anywhere: real MySQL (constraints, migrations, row locks), UMS-issued JWTs and role names (BD-09), TMS (`GET {TMS_API_BASE_URL}/api/timesheets/billing`, service token), SMTP provider, CDC, the running HTTP stack end to end.
