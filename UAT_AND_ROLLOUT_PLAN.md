# Automatic Billing — UAT, Finance Sign-Off and Controlled Rollout

Audience: Finance (billing operations, Finance Managers), release approvers, AR support.
Status: **UAT not yet executed by Finance. No sign-off has been given. Nothing has been deployed.**
Updated 2026-10-21 for Batches 4–6 (recovery, delivery safety, tax inputs): §10 lists the UAT scenarios with the
fields Finance must record; open Finance policies are in `BUSINESS_DECISIONS.md`.
This pack contains what Finance needs to run UAT, the evidence already produced by automated
business-scenario tests, the defects found so far, and the rollout plan. Operational detail
(configuration keys, SQL, incident steps) is in `AUTOMATIC_BILLING_RUNBOOK.md`.

---

## 1. Readiness review

| Area | State | Evidence |
|---|---|---|
| Supported automatic types | T&M (TMS timesheets), Fixed Price, Milestone Plan (single payment and installments), Recurring/Subscription | Phases 6–9; end-to-end tests below |
| Configuration approval | Existing workflow (`PUT /api/billing-configurations/{id}/submit` → `/approve` / `/reject`) | unchanged |
| Scheduler, run tracking, retry, recovery, backfill | Implemented; off unless `AR_AUTOMATIC_BILLING_ENABLED=true` | Phases 5, 11, 13 |
| Tax, invoice, approval, document, `READY_TO_SEND`, manual send, receivable | Implemented | Phases 4, 10; end-to-end tests |
| Monitoring / health / runbook | Implemented | Phase 13 |
| **Blockers for production UAT and rollout** | **UAT-D1** (approval authorization) and **UAT-D3** (TMS service authentication), §5 | see register |

**Environment limits of the evidence so far:** all automated evidence ran against an in-memory database (H2,
MySQL mode) with TMS, SMTP and the logged-in user simulated. It has **not** been run against MySQL, the real TMS,
UMS, SMTP or the React frontend; the frontend is not in this repository, so no screen has been verified.

---

## 2. Scenario matrix

"Automated" = executed against the real AR services (`AutomaticBillingEndToEndTest` and the unit/persistence
suites). "Finance UAT" = to be executed by Finance in the UAT environment with controlled test configurations;
**use test clients whose e-mail addresses are internal mailboxes — never real client addresses.**

| ID | Scenario | Expected result | Automated | Finance UAT |
|---|---|---|---|---|
| CFG-1 | Create, submit, approve a configuration of each type | Only APPROVED + AUTOMATIC configurations are picked up | PASS (eligibility) | Pending |
| CFG-2 | Reject a configuration / edit an approved one | Not processed; edit returns it to PENDING_APPROVAL | PASS (eligibility queries) | Pending |
| TM-1 | Approved, billable timesheets, two roles | Lines = hours × role rate; subtotal = Σ lines | PASS (₹22,000 / ₹29,803.53 cases) | Pending |
| TM-2 | Unapproved / non-billable timesheet in the period | Whole period rejected, no invoice; run FAILED with the reason | PASS | Pending |
| TM-3 | Timesheet already billed (other / overlapping period, manual snapshot) | Rejected; never billed twice (DB-enforced) | PASS (incl. concurrent) | Pending |
| TM-4 | Role with only a non-hourly (daily/monthly) rate card | Rejected ("no active hourly rate"), never hours × daily rate | PASS | Pending |
| FP-1 | One-time contract | One invoice for the contract value | PASS | Pending |
| FP-2 | Contract split over periods (1,000,000 / 3) | 333,333.33 + 333,333.33 + 333,333.34 = 1,000,000.00 | PASS | Pending |
| FP-3 | Schedule over-allocated after a frequency change | Not invoiced; run FAILED with the reason | PASS | Pending |
| MS-1 | Installments, two due and one future | Two invoices, own amounts, "Installment n of 3 — remarks"; future not billed | PASS | Pending |
| MS-2 | Installment amount/date changed after it left SCHEDULED | Not invoiced at the stale value | PASS | Pending |
| RC-1 | Monthly retainer | Each due month = its prorated share and period; current month not billed early | PASS | Pending |
| RC-2 | Renewal overlapping an invoiced period | Not invoiced | PASS | Pending |
| TAX-1 | Same-state project (KA → KA) | CGST 9% + SGST 9%, HALF_UP to paisa | PASS | Pending |
| TAX-2 | Inter-state project (KA → MH) | IGST 18% only | PASS | Pending |
| WF-1 | Approve an invoice (with record lock) | `READY_TO_SEND`, document downloadable, no e-mail, no receivable | PASS | Pending |
| WF-2 | Reject an invoice | Cannot be finalized or sent | PASS | Pending |
| WF-3 | Send a `READY_TO_SEND` invoice | Delivery SENT; exactly one receivable = grand total | PASS | Pending (internal mailbox) |
| WF-4 | Send again | Still one receivable | PASS | Pending |
| DUP-1 | Run the scheduler repeatedly | No duplicate run, tax calculation, invoice or line | PASS | Pending |
| DUP-2 | Occurrence already invoiced manually | Not invoiced again by automation | PASS | Pending |
| REC-1 | Temporary failure (TMS 5xx / DB timeout) | Retried after backoff, resumes, no duplicates; stops after max attempts | PASS | Pending (needs fault injection in UAT env) |
| REC-2 | Interrupted run (pod restart mid-run) | Recovered after its claim expires; an active run is not taken over | PASS | Pending |
| REC-3 | Missed due events (scheduler off for a month) | Each billed once, oldest first, within the backfill window | PASS | Pending |
| REC-4 | Configuration expired, final occurrence due | Still billed | PASS | Pending |
| AUTH-1 | A user **without** Finance Manager rights approves an invoice | Must be refused | **FAILS — UAT-D1** | Blocked |

---

## 3. Financial reconciliation — expected vs actual (automated run)

Expected values are hand calculations; actual values are the persisted invoice/receivable. Rounding: HALF_UP to
2 decimals per line and per tax component (the tax engine's convention).

| Scenario | Field | Expected | Actual | Result |
|---|---|---:|---:|---|
| T&M fractional hours (7.25 h × 1,500.50 ×2; 3.50 h × 999.99) | Σ line amounts | 25,257.23 | 25,257.23 | Match |
| | Subtotal | 25,257.23 | 25,257.23 | Match *(was 25,257.22 — UAT-D2, fixed)* |
| | CGST 9% / SGST 9% | 2,273.15 / 2,273.15 | 2,273.15 / 2,273.15 | Match |
| | Grand total | 29,803.53 | 29,803.53 | Match |
| Fixed Price inter-state 123,456.78 | IGST 18% (CGST/SGST 0) | 22,222.22 | 22,222.22 | Match |
| | Grand total | 145,679.00 | 145,679.00 | Match |
| Fixed Price 1,000,000.00 / 3 payments | Payments | 333,333.33 / 333,333.33 / 333,333.34 | same | Match |
| | Σ payments | 1,000,000.00 | 1,000,000.00 | Match |
| Delivered invoice 87,654.32 + 18% | Grand total / receivable amount / outstanding | 103,432.10 | 103,432.10 | Match |
| Fixed Price 6 × 100,000 (Phase 12) | Grand total / receivable | 118,000.00 | 118,000.00 | Match |
| T&M 8 h × 1,500 + 5 h × 2,000 (Phase 12) | Subtotal | 22,000.00 | 22,000.00 | Match |

Discounts: not supported by the billing model — no scenario. Currency: INR in all scenarios (the invoice takes the
configuration's currency; no conversion exists).

---

## 4. Finance workflow (to be validated by Finance users)

| Step | Where (API; the React screens call these) |
|---|---|
| Configurations and approval status | `GET /api/billing-configurations`, `/pending-approvals`, `/{id}` |
| Occurrences of a configuration | `GET /api/billing-occurrences`, `/{occurrenceId}`, `/{occurrenceId}/tax-calculation` |
| Automatic billing status / failed runs | `GET /api/v1/operations/billing-summary`, `/reconciliation` (Phase 15; authorized roles only, no screen) — per-run detail via runbook SQL (§5) |
| Invoices awaiting approval | `GET /api/v1/invoices/pending-approval`, `/approval-workspace`, `/api/v1/invoice-generation/workspace` |
| Review an invoice and its lines | `GET /api/v1/invoices/{invoiceId}` |
| Approve / reject (needs the record lock: `POST /api/record-locks/INVOICE/{invoiceId}`) | `POST /api/v1/invoices/{invoiceId}/approve` / `/reject` |
| Finalized document | `GET /api/v1/invoices/{invoiceId}/document` (`documentAvailable`, `documentUrl` on the invoice); regenerate after a failure: `POST …/document` |
| Ready to send | invoice `status = READY_TO_SEND` |
| Send manually | `POST /api/v1/invoices/{invoiceId}/send`; history `GET …/deliveries` |
| Receivable and balance | `GET /api/v1/receivables/outstanding`, `/api/v1/receivables/{receivableId}` |

Frontend changes needed (from Phase 10/13): show `READY_TO_SEND`; enable "Send" only for it; "Generate document"
for `APPROVED` invoices without a document; download link. Not verified — the frontend is outside this repository.

---

## 5. Defect register

Severity per this plan's categories (replace with the organization's policy if one exists).

| ID | Area / type | Description | Impact | Sev. | Status | Evidence |
|---|---|---|---|---|---|---|
| **UAT-D1** | Authorization, all types | Invoice approve/reject and configuration approve were open to any authenticated user. | Unauthorized approvals possible | **Critical** | **Code fixed (G-08, efb234f): services enforce configurable UMS authorities + maker-checker; empty = nobody. Open until the UMS role/permission names are configured per environment (BD-09).** | `FinancialActionAuthorizer`, `SecurityConfigurationTest` |
| UAT-D2 | T&M, amounts | Subtotal = rounded Σ of unrounded lines; lines rounded separately → invoice lines ≠ subtotal by a paisa per line; tax on the wrong base | Incorrect invoice totals | Critical | **Fixed** — lines rounded once (HALF_UP, 2 dp), subtotal = Σ rounded lines (`BillingSnapshotBuilder.lineAmount`, `BillingSnapshotServiceImpl.calculateAmounts`) | `uat_timeAndMaterial_fractionalHoursAndRates_reconcileToThePaisa` fails before, passes after |
| **UAT-D3** | T&M, integration | No service-to-service TMS authentication: scheduled T&M has no JWT → TMS 401, run FAILED (not retried) | Automatic T&M impossible; other types unaffected | **High** | **Open — needs the UMS/TMS service-token mechanism** | Phase 6/12 |
| UAT-D4 | Fixed Price / Recurring, manual flow | Regenerating a schedule after some periods were billed spreads the **full** contract again (over-allocation). Automation refuses such occurrences; the manual flow does not | Manual over-billing risk | High | Open — business decision on reconciliation rule | Phase 7/9 tests |
| UAT-D5 | Compliance | Invoice numbers are 25 characters (`INV-yyyyMMddHHmmss-XXXXXX`; previously 18) — GST invoice numbers are limited to 16 characters | E-invoicing / GST filing | High (compliance) | Open — numbering scheme to be decided by Finance/tax | Phase 12 |
| UAT-D6 | Configuration | No time zone set; in UTC containers "due today" starts 05:30 IST | Billing a few hours late/early around midnight | Medium | Open — deployment decision (`TZ`) | Phase 12 |
| UAT-D7 | Frontend | CORS allowed only `http://localhost:5173` | Production frontend blocked unless same-origin | Medium | **Code fixed (G-25): `AR_CORS_ALLOWED_ORIGINS` per environment, wildcards refused.** Open until the production origin is configured | `SecurityConfigurationTest` |
| UAT-D8 | T&M, precision | Line quantity is stored with 2 decimals; if TMS sends more (e.g. 7.333 h) the printed quantity × rate differs from the line amount | Document readability | Low | Open — confirm TMS hour precision | code review |
| UAT-D9 | Quality gate | Legacy tests in 8 classes failed | CI gate cannot be blocking yet | Medium | **40 of 41 resolved (G-26), incl. 4 production defects fixed. 1 remains: it encodes undecided policy BD-05.** | commits e51d267, d218e4f, a47b70d |
| UAT-D10 | Delivery | The same invoice could be emailed repeatedly; SMTP ran inside a DB transaction | Duplicate client emails; email without a record | High | **Fixed (G-19/G-20)**: one ordinary delivery per invoice, SMTP outside transactions, `UNCERTAIN` outcomes resolved by operations | `InvoiceDeliveryPersistenceTest` |
| UAT-D11 | Fixed Price | Retention % and advance received are captured but not applied to invoices | Possible over-invoicing | High | **Open — Finance decision BD-01/BD-02.** Automatic billing refuses such configurations | `AutomaticBillingOrchestratorTest` |
| UAT-D12 | MySQL | All automated evidence ran on H2 | DB-specific behaviour unverified | High | Open — run `MySqlIntegrationTest` with Docker | runbook §7 |

No financial-integrity defect is waived. D1 and D3 must be closed (or, for D3, T&M explicitly excluded from the
pilot) before production rollout.

---

## 6. Controlled rollout

Existing controls — no new feature flag is needed:
- **Global switch:** `AR_AUTOMATIC_BILLING_ENABLED` (default off).
- **Per-configuration scope:** automation only processes configurations that are **APPROVED and
  `invoiceGenerationType = AUTOMATIC`**. Everything else stays manual. The pilot is therefore defined by which
  configurations are set to AUTOMATIC (changing it on an approved configuration sends it back for approval — the
  existing control).
- **Volume:** `AR_AUTOMATIC_BILLING_BATCH_SIZE`; **history:** `AR_AUTOMATIC_BILLING_BACKFILL_MAX_AGE_DAYS`.

**Before the first activation — inventory (read-only):** every configuration already marked AUTOMATIC is in scope
the moment the switch is turned on, and with unlimited backfill **all** of its past due events are billed.
```sql
SELECT BIN_TO_UUID(c.billing_configuration_id) configuration_id, c.approval_status, c.billing_status,
       (SELECT COUNT(*) FROM billing_schedule s WHERE s.billing_configuration_id = c.billing_configuration_id
          AND s.is_active = 1 AND s.is_invoiced = 0 AND s.billing_date <= CURDATE()) due_unbilled_occurrences
FROM billing_configuration c
WHERE c.invoice_generation_type = 'AUTOMATIC' AND c.approval_status = 'APPROVED' AND c.manually_deactivated = 0;
```
Every configuration listed must be either an approved pilot configuration or switched to MANUAL first.

| Stage | Entry criteria | Settings | Exit / stop criteria |
|---|---|---|---|
| 1. Pre-production acceptance | Automated suites green for the changed areas; `MySqlIntegrationTest` passed (Docker agent); Finance UAT scenarios §2 and §10 executed and accepted; D1 closed (authorities configured); D3 closed or T&M excluded; BD-01/02/05/09/10 decided; migrations V6–V14 applied or Flyway-adopted (runbook §7); image deployed by commit SHA; probes updated in `intranet-devops` | UAT environment, internal mailboxes only | Finance + technical sign-off (§9) |
| 2. Pilot | Sign-off; inventory query reviewed; pilot configurations approved by Finance | `ENABLED=true`, small batch size, backfill window agreed (e.g. only the pilot's current period) | Every pilot run, tax and invoice reviewed by Finance before approval. **Stop** (`ENABLED=false`, runbook §3) on any wrong amount, duplicate, or unauthorized action |
| 3. Expansion | Pilot results reviewed against the agreed criteria; sign-off for the next group | More configurations set to AUTOMATIC, group by group | Same stop rule; continue monitoring (runbook §4) |

**Deployment order (each environment):** (1) back up the database; (2) apply/verify migrations V12–V14 (or adopt
Flyway, runbook §7); (3) deploy the image by commit SHA with `AR_AUTOMATIC_BILLING_ENABLED=false`; (4) check
readiness `UP`, `GET /api/v1/operations/reconciliation` = no findings, recovery gauges 0; (5) only then enable
automation for the stage's configurations. **Rollback:** disable automation, redeploy the previous SHA (runbook §6;
resolve `UNCERTAIN` deliveries first). **Post-release verification:** after the first ticks and daily during the
pilot — `billing-summary`, `reconciliation`, `ar.invoice.deliveries.unknown_outcome`,
`ar.receivables.sent_without_receivable`, `ar.automatic_billing.runs.needs_intervention`.

Numerical acceptance thresholds (e.g. tolerated failure rate, review sample size) are **to be agreed** by Finance
and the release approvers; none is set here.

---

## 7. Operational ownership (to be filled with named roles)

| Responsibility | Owner (role) | Backup |
|---|---|---|
| Approve automatic billing configurations | _Finance — to assign_ | |
| Approve / reject invoices (Finance Manager) | _Finance Manager — to assign_ | |
| Send invoices to clients | _Finance — to assign_ | |
| Reconcile receivables | _Finance — to assign_ | |
| Monitor scheduler, failed and stuck runs (metrics, daily SQL check) | _AR support — to assign_ | |
| Review calculation / tax discrepancies | _Finance + AR engineering — to assign_ | |
| TMS / UMS / database / mail incidents | _Platform / integration owners — to assign_ | |
| Authorize recovery, re-queue of failed runs, backfill window | _AR engineering lead + Finance — to assign_ | |
| Disable automatic billing during an incident | _On-call engineer (any of the above may request)_ | |

No permissions are granted by this plan.

---

## 8. Finance guide (summary)

- **What is automated:** for APPROVED configurations set to AUTOMATIC, AR creates the invoice when a billing event
  is due and puts it in **Pending Approval**. Nothing is approved or sent automatically.
- **When:** the scheduler runs every 5 minutes (configurable); an event is due on its billing date (Fixed
  Price/Recurring: period end; Milestone/Installment: the payment's billing date; T&M: the period's billing date).
- **Approve:** open the invoice, check lines, tax and totals, approve. The document is generated and the invoice
  becomes **Ready to Send**. If the response says the document could not be generated, fix the reported data and
  use "Generate document".
- **Send:** only Ready to Send invoices can be sent; sending creates the receivable. Sending again does not create
  another receivable.
- **Something looks wrong** (amount, tax, duplicate): do **not** approve. Reject with the reason and escalate to the
  owner in §7 with the invoice number, configuration and billing period. To stop all automatic billing, ask the
  on-call engineer (runbook §3).
- **A due invoice did not appear:** support checks the run (runbook §5) — it is either waiting for an automatic
  retry or needs a data fix (e.g. missing tax configuration, rate card, currency, or the schedule changed).

## 9. Sign-off

| Role | Name | Decision | Date |
|---|---|---|---|
| Finance (billing owner) | | Pending | |
| Finance Manager | | Pending | |
| AR engineering lead | | Pending | |
| Operations / platform | | Pending | |

---

## 10. UAT scenarios — Batches 4–6 (to be executed by Finance)

How to record: fill *Result* (Pass/Fail), *Defect* (UAT-Dn) and *Reviewer / date* per row; attach the invoice PDF,
delivery history and receivable for every financial scenario. Use test clients and projects only — no production
customer data. "Automated" says whether an automated test covers the same rule (it does not replace Finance UAT).

| ID | Scenario | Preconditions / test data | Steps | Expected amounts and state transitions | Expected persisted records | Automated | Result | Defect | Reviewer / date |
|---|---|---|---|---|---|---|---|---|---|
| TM-5 | Time & Material, monthly | Approved AUTOMATIC T&M configuration (KA→KA), rate card Developer 1500/h; approved timesheets 8 h + 5 h × 2000 for the month | Wait for the tick after period end (or enable automation in UAT) | Lines 12000.00 + 10000.00; subtotal 22000.00; CGST 9% 1980.00 + SGST 9% 1980.00; total 25960.00; run → PENDING_APPROVAL | 1 snapshot, 1 tax calculation (currency recorded), 1 invoice GENERATED→PENDING_APPROVAL | Yes | | | |
| TM-6 | Late-approved timesheets | As TM-5 but one timesheet unapproved at period end | Tick; approve timesheet next day; next day's tick | First attempt FAILED at SOURCE_DATA_ACQUISITION ("not approved"); next day invoiced with all hours | 1 run (failure history), 1 snapshot, 1 invoice | Yes | | | |
| FP-4 | Fixed Price, monthly split | Contract 100000.00, 4 months, no retention/advance | Approve configuration; ticks per month | 25000.00 per occurrence (HALF_EVEN split, remainder on last); IGST 18% for KA→MH | 4 occurrences, 1 invoice each | Yes | | | |
| FP-5 | Fixed Price with retention or advance | Retention 5% or advance 10000.00 | Approve; tick | Automatic run FAILED "retention … bill it manually"; no invoice. Finance bills manually per their practice | 1 FAILED run, nothing else | Yes | | BD-01/02 | |
| MS-3 | Milestone / installment | 3 installments 30/30/40 % of 90000.00 | Approve; ticks on installment dates | 27000.00, 27000.00, 36000.00 | 3 occurrences, 3 invoices | Yes | | | |
| RC-3 | Recurring / retainer | Monthly 50000.00, start mid-month | Approve; ticks | First period prorated by days (HALF_EVEN); then 50000.00 | Occurrence per term period | Yes | | BD-04 | |
| MAN-1 | Manual billing | MANUAL configuration | Finance generates tax and invoice manually | Same amounts as automatic; automation never touches it | No automatic run | Yes | | | |
| CFG-3 | Configuration approval | Edited APPROVED configuration | Approve as a different user; try approving as the submitter | Submitter refused (maker-checker, 403); other approver succeeds | Approval actor recorded | Yes | | | |
| WF-5 | Invoice approval / rejection | Invoice PENDING_APPROVAL | Reject with reason; refresh after correction; resubmit; approve | REJECTED→(refresh)→PENDING_APPROVAL→APPROVED→READY_TO_SEND; resubmission without refresh refused | History: REJECTED, CORRECTED, SUBMITTED, APPROVED with actors | Yes | | | |
| DOC-1 | Document generation | Approved invoice | Open the invoice; download PDF | `documentAvailable=true` only when stored; PDF totals = invoice totals | 1 invoice_document | Yes | | | |
| DEL-1 | Manual send | READY_TO_SEND invoice with client email | Send; send again | First SENT; second refused "already been sent"; exactly one email received | 1 delivery SENT (requester recorded), 1 receivable = grand total | Yes | | | |
| DEL-2 | Uncertain delivery | Simulate SMTP timeout in UAT (block port 587 after DATA) or use the test seam | Send; check mailbox; resolve as SENT/FAILED via recovery API | UNCERTAIN; resend blocked; after resolve SENT → receivable; FAILED → send allowed | Delivery with resolvedBy/reason | Partly | | | |
| REC-5 | Missing receivable recovery | SENT invoice whose receivable creation failed (UAT: delete receivable row in a test DB) | Wait ≤ 15 min or call recovery endpoint | Exactly one receivable recreated, no email | 1 receivable | Yes | | | |
| RUN-1 | Failed run re-queue | Run FAILED on missing tax configuration | Add tax configuration; re-queue with reason | Processed next tick; failure kept in events | Run event REQUEUED (actor, reason) | Yes | | | |
| RUN-2 | Approval callback lost | Invoice approved while the run's completion fails (UAT: approve during a DB blip, or the test seam) | Next tick | Run COMPLETED by reconciliation; no new invoice/document | Run event APPROVAL_RECONCILED | Yes | | | |
| DUP-3 | Duplicate prevention | Two users send / approve / re-queue concurrently | Concurrent clicks | One effect only | 1 email, 1 receivable, 1 event | Yes (H2) | | | |
| TAX-3 | Tax and currency correctness | Configuration whose currency or tax region is changed after tax was calculated | Re-approve; try invoicing | Invoicing refused (currency/tax region changed) | No invoice | Yes | | BD-07 | |
| TAX-4 | Jurisdiction cannot be determined | Project location not a known tax region, configuration with ALL + CGST/SGST/IGST components | Calculate tax | Refused (jurisdictions undetermined) — not taxed with ALL only | No tax calculation | Yes | | | |
| AMD-1 | Contract amendment | Change contract value after one occurrence billed | Edit and re-approve | Refused (billed occurrences) without partial changes | Unchanged occurrences | Yes | | | |
| AMD-2 | Effective From after an invoiced period | Move Effective From past an INVOICED occurrence | Edit and re-approve | **Policy undecided** | — | Red test | | BD-05 | |
| CAL-1 | Month-end / short month | Monthly periods across Feb 2027, anchor 31st | Generate | Periods follow the original anchor (28 Feb, 31 Mar) | Occurrences | Yes | | | |
| AUTH-2 | Unauthorized actions | User without the configured authorities | Approve, send, re-queue | 403 each; nothing changes | — | Yes | | | |

Finance sign-off (§9) requires every row Pass or an accepted, recorded exception.
