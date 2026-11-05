# Automatic Billing — Post-Production Monitoring, Reconciliation and Continuous Improvement

Audience: AR support, on-call engineers, Finance (billing operations, Finance Managers), AR engineering lead.
Builds on `AUTOMATIC_BILLING_RUNBOOK.md` (configuration, SQL, incident steps) and `UAT_AND_ROLLOUT_PLAN.md`
(UAT, defects, rollout). No billing, tax, approval, delivery or receivable behaviour is changed by anything here.

**Status (2026-10-21): not deployed.** Finance UAT has not been executed and no sign-off exists
(`UAT_AND_ROLLOUT_PLAN.md` §1, §9). Everything below is prepared and verified in development (H2 in-memory
database); **nothing has been observed in production**. Sections marked *to be agreed* need a business decision.

---

## 1. Production readiness review (as found)

| Item | State | Evidence |
|---|---|---|
| Deployed version / active configuration | **Not deployed.** Manifest still points at the mutable `ar-dev:latest`; ArgoCD deploys from `intranet-devops` | `k8s/backend/ar/deployment.yaml`, runbook §3 |
| Automatic billing scheduler | Implemented, **off by default** (`AR_AUTOMATIC_BILLING_ENABLED=false`) | runbook §2 |
| Database connectivity / migrations | Readiness probe checks the DB. V6–V9 must be applied manually; not confirmed in any environment | runbook §7 |
| TMS / UMS / SMTP | Not probe dependencies (by design). **UAT-D3 open**: no service-to-service TMS auth → scheduled T&M fails with 401 | UAT plan §5 |
| Invoice documents / delivery | PDF stored in `invoice_document` (DB); delivery via Gmail SMTP | runbook §2, §7 |
| Run status and failure tracking | `automatic_billing_run` (status, step, attempts, retry, sanitized reason) | Phase 11/13 |
| Logging, health, metrics | Actuator `health`/`info`/`metrics`, `[run=…]` log context, outcome lines | runbook §4 |
| Alerting platform | **None configured in this repository** (no Prometheus/Alertmanager/log alert rules) | runbook §4 |
| Finance UAT / rollout | **Not executed; no sign-off.** Critical defect **UAT-D1** (no approval authorization) open | UAT plan §5 |

Conclusion: production monitoring cannot be claimed. Phase 15 prepares the monitoring, reconciliation,
alerting and procedures so they are ready on the first enabled environment.

---

## 2. What Phase 15 adds (and reuses)

Reused unchanged: run tracking (`automatic_billing_run`), Phase 13 gauges/counters, outcome log lines,
health probes, the runbook SQL. Added — all **read-only**:

| Endpoint (JWT + configured authority) | Purpose |
|---|---|
| `GET /paves/ar/api/v1/operations/billing-summary?days=30` | Execution status (runs) next to business status (invoices, receivables) |
| `GET /paves/ar/api/v1/operations/reconciliation` | 25 financial reconciliation checks with findings |
| `GET /paves/ar/api/v1/operations/alerts` | Configured alert conditions, current value, raised or not |

Scheduled alert evaluation (`OperationsAlertJob`, off by default) logs `[OperationsAlert]` and counts
`ar.operations.alerts{condition}`.

### Access control
Reports contain client names, invoice numbers and amounts. Access is **denied to everyone** until
`AR_OPERATIONS_REPORT_AUTHORITIES` lists the JWT authorities allowed (`ROLE_<UMS role>` or a permission,
comma-separated). Unauthenticated → 401; authenticated without the authority → 403. The UMS role names
are *to be agreed* together with UAT-D1 (same role model). Other APIs are unaffected.

### Summary sections (do not mix them)
- `automaticBillingRuns` — **execution status**: by status, by billing type × status, by creation date ×
  status (`days` window), needing intervention / retry scheduled (each by failing step: `SOURCE_DATA_ACQUISITION`
  = TMS/data, `TAX_CALCULATION`, `INVOICE_CREATION`, `SUBMIT_FOR_APPROVAL`), retries exhausted, retried,
  recovered (failed once, later succeeded), stuck.
- `occurrences` — due automatic occurrences with no run; of those, overdue (`AR_OPERATIONS_OVERDUE_GRACE_DAYS`,
  default 1 — a reporting cut-off, not an SLA) and the oldest overdue billing date.
- `invoices` — **business status**: by status, by billing type × status, automation-created by status,
  awaiting Finance Manager approval, approved without document (document-generation failure), ready for
  manual delivery, sent, delivery failed and never sent, failed attempts.
- `receivables` — by status; sent invoices with / without a receivable.

A run's `PENDING_APPROVAL` means *automation finished its part*; whether the invoice is still awaiting
approval is in `invoices`.

---

## 3. Financial reconciliation

`GET …/operations/reconciliation` runs every check on each request (nothing is stored or corrected).
Per check: default severity, owner, business impact, corrective action, complete `findingCount`,
`highOrCriticalCount`, and up to `AR_OPERATIONS_RECONCILIATION_MAX_FINDINGS` (default 100) findings with
record id, reference (invoice / snapshot number, billing date), related id, expected vs actual.

| Area | Checks |
|---|---|
| Source amounts | `OCCURRENCE_AMOUNT_MISMATCH` (occurrence amount = invoice subtotal), `SNAPSHOT_AMOUNT_MISMATCH` (snapshot total = subtotal), `SNAPSHOT_ITEMS_MISMATCH` (Σ snapshot lines = snapshot total), `INVOICE_SOURCE_MISSING`, `OCCURRENCE_MISSING` |
| Invoice totals / tax | `INVOICE_LINES_MISMATCH` (Σ lines = subtotal; no lines), `INVOICE_TOTAL_MISMATCH` (subtotal + tax = grand total), `INVOICE_TAX_BREAKDOWN_MISMATCH` (Σ components = total tax), `INVOICE_TAX_CALCULATION_MISMATCH` (invoice = its tax calculation) |
| Invoice ↔ occurrence ↔ run | `DUPLICATE_INVOICE_FOR_SOURCE`, `OCCURRENCE_INVOICED_WITHOUT_INVOICE`, `OCCURRENCE_NOT_MARKED_INVOICED`, `RUN_WITHOUT_INVOICE`, `RUN_INVOICE_OCCURRENCE_MISMATCH`, `INVOICE_LINKED_TO_MULTIPLE_RUNS`, `OCCURRENCE_OVERDUE_WITHOUT_RUN` |
| Approval / document / delivery | `READY_TO_SEND_WITHOUT_DOCUMENT`, `APPROVED_WITHOUT_DOCUMENT`, `DOCUMENT_FOR_UNAPPROVED_INVOICE`, `SENT_INVOICE_NOT_APPROVED` |
| Receivables | `SENT_WITHOUT_RECEIVABLE`, `RECEIVABLE_WITHOUT_SENT_DELIVERY`, `RECEIVABLE_AMOUNT_MISMATCH` (= grand total), `RECEIVABLE_BALANCE_UNEXPECTED`, `DUPLICATE_RECEIVABLE` |

Notes:
- **Rounding:** a line difference of at most ₹0.01 per line is reported **MEDIUM** — the pattern of records
  created before the UAT-D2 fix. Larger differences keep the check's severity.
- **Balances:** no payment process reduces a receivable yet, so an `OUTSTANDING` balance ≠ invoice amount, or
  any balance above it, is reported. Revisit this check when payment application is built.
- `DUPLICATE_*` checks are backstops for unique keys that `ddl-auto=update` may have silently skipped
  (runbook §7); with the keys in place they cannot fire.
- **Never** edit amounts or balances to make a check pass. Corrections go through the invoice correction
  process (rejected invoices), credit/debit notes (sent invoices), or receivable recovery (runbook §5).

---

## 4. Exception management

Findings are the exception list: derived from persisted records, **OPEN while the discrepancy exists**, gone
once corrected. Do not build a second register — track the investigation in the existing incident/ticket
tool with this minimum record:

| Field | Source |
|---|---|
| Identifiers | `check`, `recordType`, `recordId`, `reference`, `relatedId`; for runs also the `[run=<id>]` log lines |
| Severity | finding `severity` (CRITICAL / HIGH / MEDIUM / LOW) |
| Business impact | check `businessImpact` |
| Owner | check `owner` (role — see §7) |
| Resolution status | ticket status; verified closed when the finding no longer appears |
| Corrective action | check `correctiveAction`; the actual action and its approval recorded on the ticket |

Response by severity (*targets to be agreed with Finance*): **CRITICAL** — stop sending the affected invoices;
consider pausing automatic billing (runbook §3); same-day triage. **HIGH** — hold approval/sending of the
affected invoice; triage next business day. **MEDIUM/LOW** — weekly review.

### Investigation procedures

| Exception | Where to look | Procedure |
|---|---|---|
| Repeated billing failures | summary `needingInterventionByFailureStep`, `retriesExhausted`; runbook §5 query | Read `failure_reason`. Data error → owner fixes data (tax config, rate card, currency, schedule) → re-queue the run (runbook §6, audited). Same step failing for many runs → engineering incident |
| Missing / incorrect invoice lines | `INVOICE_LINES_MISMATCH`, `SNAPSHOT_*` | Do not approve. Compare with occurrence/snapshot and TMS timesheets; reject → correction |
| Tax discrepancies | `INVOICE_TAX_*`, `INVOICE_TOTAL_MISMATCH` | Finance tax review against the effective tax configuration (runbook §5). Tax rules change only with Finance/tax approval |
| Duplicate billing attempts | `DUPLICATE_INVOICE_FOR_SOURCE`, `INVOICE_LINKED_TO_MULTIPLE_RUNS`, `RUN_INVOICE_OCCURRENCE_MISMATCH` | Pause automatic billing; verify unique keys (runbook §7); Finance decides which invoice stands |
| Occurrences overdue | summary `occurrences`, `OCCURRENCE_OVERDUE_WITHOUT_RUN` | Scheduler enabled? ticking (`ar.automatic_billing.tick`)? backfill window? configuration still APPROVED+AUTOMATIC? |
| TMS integration failures | `failureStep = SOURCE_DATA_ACQUISITION`, `ar.tms.request.failures{status}` | 401/403 = credentials/UAT-D3 (not retried); 5xx/io = TMS outage (retried). Escalate to TMS owner |
| Failed document generation | `APPROVED_WITHOUT_DOCUMENT`, `ar.invoice.document.failures` | Fix reported data → `POST /api/v1/invoices/{id}/document` |
| Sent without receivable | `SENT_WITHOUT_RECEIVABLE` | `ReceivableService.createReceivableFromInvoice` recovery; **never resend** |
| Unexpected receivable balance | `RECEIVABLE_BALANCE_UNEXPECTED`, `RECEIVABLE_AMOUNT_MISMATCH` | Audit who changed it; authorized correction only |

---

## 5. Alerting

No alerting platform exists in this repository; Phase 15 provides the signal, routing is a deployment task.

| Condition (`AR_ALERT_<CONDITION>`) | Value |
|---|---|
| `RUNS_NEEDING_INTERVENTION` | FAILED runs without automatic retry |
| `RUNS_STUCK` | PROCESSING runs with expired claim |
| `RUNS_RETRY_SCHEDULED` | FAILED runs awaiting retry |
| `OCCURRENCES_OVERDUE` | overdue due occurrences without a run |
| `INVOICES_PENDING_APPROVAL` | approval backlog |
| `DOCUMENTS_MISSING` | approved invoices without document |
| `DELIVERIES_FAILED` | invoices with failed and no successful delivery |
| `SENT_WITHOUT_RECEIVABLE` | sent invoices without receivable |
| `RECONCILIATION_EXCEPTIONS` | HIGH/CRITICAL reconciliation findings (runs the full reconciliation) |

- An alert is raised when **value > threshold**. A condition with no threshold is never evaluated.
  **No thresholds are set by default** — they are *to be agreed* with Finance (e.g. `0` for
  `SENT_WITHOUT_RECEIVABLE` and `RECONCILIATION_EXCEPTIONS` is a natural starting proposal, not a decision).
- Enable with `AR_OPERATIONS_ALERTS_ENABLED=true`; schedule `AR_OPERATIONS_ALERTS_CRON` (default hourly).
- Output: `WARN [OperationsAlert] condition=… value=… threshold=…`, counter `ar.operations.alerts{condition}`;
  `condition=EVALUATION_FAILED` if the evaluation itself fails (e.g. database down). Route these with the log or
  metrics platform; until then, the daily check in §7 covers them.
- The Phase 13 gauges remain the cheaper per-scrape signal for a future Prometheus setup (runbook §4).

---

## 6. Performance and reliability

**No production logs or measurements exist, so no optimization was made** — none is justified without data.
Development observations only:

| Item | Finding |
|---|---|
| Report queries | Aggregates and `EXISTS` subqueries in the database; scalars only (no entity graphs, no PDF blob reads — documents are checked by `invoice_id`, unique key) |
| Indexes | Served by existing keys: `uk_invoice_billing_schedule/_snapshot`, `idx_invoice_item_invoice`, `idx_invoice_tax_component_invoice`, `idx_invoice_delivery_invoice`, `uk_receivable_invoice`, `uk_invoice_document_invoice`, `idx_automatic_billing_run_status/_invoice`. No new index |
| Volume (H2, dev laptop) | 3,000 invoices / 6,000 lines / 1,000 receivables: summary **64 ms**, full reconciliation (25 checks, 3,000 findings counted, 100 listed) **152 ms**. H2 in memory — **not** a MySQL/production figure |
| Bounded output | Findings listed per check are capped; counts stay complete |
| Load | Reconciliation scans invoice/receivable tables; run it on demand or at most hourly, off-peak if volumes grow; re-measure on MySQL before tightening the alert cron |

To review once running (from the existing signals): `ar.automatic_billing.tick` vs the cron interval, batch
size vs due volume (`occurrences.due_without_run` across ticks), `ar.tms.request.failures` and TMS latency vs
`TMS_API_READ_TIMEOUT`, retry frequency (`retryScheduled`, `retried`), Hikari pool metrics
(`hikaricp.connections.*`, exposed by Actuator), JVM memory/CPU (`jvm.*`, `process.cpu.usage`). Any change must
keep database-backed idempotency (unique keys, atomic claim) and transaction boundaries intact.

---

## 7. Operating cadence and ownership

| Cadence | Activity | Owner (role — names *to be assigned*, UAT plan §7) |
|---|---|---|
| Daily (business days) | Summary + reconciliation; act on CRITICAL/HIGH; check runs needing intervention and overdue occurrences | AR support; Finance for financial findings |
| Daily | Approval queue and ready-for-delivery queue | Finance / Finance Manager |
| Weekly | MEDIUM/LOW findings, retry/failure trends by step and billing type, alert threshold review | AR support + AR engineering |
| Monthly | Before AR month-end close: reconciliation must show no open CRITICAL/HIGH findings or each has an approved ticket | Finance + AR engineering lead |
| Per incident | Post-incident review (§8) | AR engineering lead |

Owner roles in findings: `FINANCE`, `AR_SUPPORT`, `AR_ENGINEERING` → mapped to the named owners of UAT plan §7.

---

## 8. Continuous improvement

For every recurring problem (same check, same failure step, or same incident twice):

1. **Root cause** — from findings, run records and `[run=…]` logs; not from symptoms.
2. **Impact** — invoices/amounts/clients affected (finding counts), delay to billing.
3. **Prioritize** — by severity and financial impact, agreed with Finance.
4. **Narrow fix** — smallest change addressing the cause.
5. **Regression test** — reproduce first (the reconciliation test pattern: introduce the discrepancy, assert detection).
6. **Validate** in a non-production environment.
7. **Release** through the existing pipeline (runbook §8) — production via `deployToProd` approval.
8. **Monitor** — confirm the finding/alert stops recurring.

Billing rules, tax behaviour and receivable logic change **only with Finance (and tax) approval**.
Record each review in the incident tool: date, problem, cause, impact, decision, fix, verification.

---

## 9. Configuration reference

| Variable | Default | Meaning |
|---|---|---|
| `AR_OPERATIONS_REPORT_AUTHORITIES` | empty (nobody) | Authorities allowed to read `/api/v1/operations/**` |
| `AR_OPERATIONS_OVERDUE_GRACE_DAYS` | `1` (0–365) | Days past billing date before "overdue" |
| `AR_OPERATIONS_RECONCILIATION_MAX_FINDINGS` | `100` (1–1000) | Findings listed per check |
| `AR_OPERATIONS_ALERTS_ENABLED` | `false` | Scheduled alert evaluation |
| `AR_OPERATIONS_ALERTS_CRON` | `0 0 * * * *` | Evaluation schedule (validated at startup) |
| `AR_ALERT_<CONDITION>` | unset | Threshold per condition (§5); unset = not evaluated |

## 10. Outstanding before production monitoring is real

- Deploy (immutable image tag), apply V6–V9, close **UAT-D1** (and use the same role model for report access),
  close or scope out **UAT-D3**, Finance UAT + sign-off (UAT plan).
- Agree and set `AR_OPERATIONS_REPORT_AUTHORITIES` and the alert thresholds; enable alerts.
- Route `[OperationsAlert]` / `ar.operations.alerts` to people (log alerting or a metrics backend).
- Assign named owners (§7) and start the daily check.
- Re-measure report timings on MySQL with real volumes.
