# AR Automatic Billing — Backend Implementation Specification (Code Audit)

| | |
|---|---|
| Audited branch | `feature/ar-automatic-billing` |
| Audited commit | `2eeeb4a` (2026-10-21 19:40 +0530), working tree clean |
| Audit date | 2026-10-22 |
| Scope | Read-only audit. No code, configuration, test or document other than this file was changed. No tests were run. |
| Java root | `src/main/java/com/AccountReceivableManagement/` (paths below are relative to it unless they start with `src/`, `k8s/` or are repository-root files) |

**How to read this document**

- **[IMPL]** — read directly in the current source.
- **[TESTED]** — covered by a test that exists in `src/test` (results are the last *recorded* run, not re-run for this audit — see §9).
- **[INFERRED]** — follows from the code, but no test or execution confirms it.
- **NOT VERIFIED** — could not be established from code alone (depends on runtime data, environment secrets or unexecuted framework behaviour).

Earlier repository documents (`AR_FRONTEND_END_TO_END_TEST_GUIDE.md`, `FRONTEND_INTEGRATION_HANDOFF.md`, `RECURRING_BILLING_API_DOCUMENTATION.md`, …) were **not** treated as authoritative. Where they disagree with code, this document follows code and says so (§4.0).

---

## 0. Executive summary

1. **Automation is off by default.** `ar.automatic-billing.enabled` (`AR_AUTOMATIC_BILLING_ENABLED`) defaults to `false` in every profile. No API exposes whether it is on.
2. **What automation does.** When enabled, a scheduler (`AutomaticBillingScheduler`, every 5 minutes by default) finds due billing events of **APPROVED** configurations with `invoiceGenerationType = AUTOMATIC` and drives each through: source data → tax → **invoice draft creation** → **submit for Finance Manager approval**. It **stops at invoice `PENDING_APPROVAL`**.
3. **What stays manual.** Invoice approval (which also finalizes the PDF and moves the invoice to `READY_TO_SEND`), invoice delivery ("Send" — emails the PDF via SMTP), and every recovery action are user actions. Receivable creation is automatic *after* a successful send.
4. **There is no "generation day" and no "billing trigger" field** anywhere in the backend. Due dates come from billing frequency + effective dates (Fixed Price, Recurring, T&M) or from payment-entry dates (Milestone / Installment). Billing is in arrears: billing date = period end.
5. **Installment is not a billing type.** It is `PaymentStructure.INSTALLMENTS` inside the "Milestone Plan" billing type and shares the milestone code path.
6. **The "Manual" billing type has no implementation** beyond an enum value; it is never billed automatically.
7. **Fixed Price with retention > 0 or advance > 0 is refused by automation** (G-32, pending Finance decisions BD-01/BD-02).
8. **The frontend cannot currently see automation state**: there is no endpoint to list/get automatic billing runs, and invoices carry no "created automatically" flag. Only operations reports (restricted) and the recovery history endpoint expose run data.
9. **Approval authorities are empty by default** — nobody can approve, reject, send or recover until `AR_AUTH_*` properties are set to UMS roles/permissions, which are not defined in this repository (BD-09).

---

## 1. Feature flag and scheduler

### 1.1 Feature flag

| Item | Value | Source |
|---|---|---|
| Property | `ar.automatic-billing.enabled` | `config/properties/AutomaticBillingSchedulerProperties.enabled` (Java default `false`) |
| Env variable | `AR_AUTOMATIC_BILLING_ENABLED` | `src/main/resources/application.properties` L230: `ar.automatic-billing.enabled=${AR_AUTOMATIC_BILLING_ENABLED:false}` |
| `local` profile | **not overridden** → `false` | `src/main/resources/application-local.properties` (only SQL logging and CORS) |
| test | set per test class via `@SpringBootTest(properties=…)`; there is no `src/test/resources` | §9 |
| prod / k8s | taken from secret `ar-secrets` (not in repo) → **NOT VERIFIED** | `k8s/backend/ar/deployment.yaml` (`envFrom: secretRef ar-secrets`) |
| Registered by | `config/AutomaticBillingConfig` (`@EnableConfigurationProperties`) | |
| Startup validation | `@Validated`: invalid cron, `batch-size` outside 1..500, invalid retry settings stop startup | `AutomaticBillingSchedulerProperties`, [TESTED] `AutomaticBillingSchedulerPropertiesTest` |

When disabled, `AutomaticBillingScheduler.runOnce()` returns immediately without touching the database [IMPL, TESTED `disabled_touchesNothing`]. Manual APIs are unaffected [TESTED `OperationalReadinessTest.automaticBilling_isOffUnlessExplicitlyEnabled_andManualApisStayAvailable`].

All related settings (`application.properties` L230–258):

| Property | Env var | Default |
|---|---|---|
| `ar.automatic-billing.cron` | `AR_AUTOMATIC_BILLING_CRON` | `0 */5 * * * *` (every 5 min, 6-field Spring cron) |
| `ar.automatic-billing.batch-size` | `AR_AUTOMATIC_BILLING_BATCH_SIZE` | `50` (max billing events per tick) |
| `ar.automatic-billing.claim-duration` | `AR_AUTOMATIC_BILLING_CLAIM_DURATION` | `PT15M` |
| `ar.automatic-billing.retry.enabled` | `AR_AUTOMATIC_BILLING_RETRY_ENABLED` | `true` |
| `ar.automatic-billing.retry.max-attempts` | `…_RETRY_MAX_ATTEMPTS` | `3` |
| `ar.automatic-billing.retry.initial-delay` | `…_RETRY_INITIAL_DELAY` | `PT15M` |
| `ar.automatic-billing.retry.multiplier` | `…_RETRY_MULTIPLIER` | `2.0` |
| `ar.automatic-billing.retry.max-delay` | `…_RETRY_MAX_DELAY` | `PT6H` |
| `ar.automatic-billing.backfill.enabled` | `…_BACKFILL_ENABLED` | `true` |
| `ar.automatic-billing.backfill.max-age-days` | `…_BACKFILL_MAX_AGE_DAYS` | `0` (= unlimited) |
| `spring.task.scheduling.pool.size` | `AR_SCHEDULING_POOL_SIZE` | `3` (shared by all `@Scheduled` jobs) |

### 1.2 Scheduler

| Item | Detail |
|---|---|
| Class / method | `service_Imple/automatic_billing/AutomaticBillingScheduler.scheduledTick()` → `runOnce()` |
| Trigger | `@Scheduled(cron = "${ar.automatic-billing.cron:0 */5 * * * *}")`; scheduling enabled by `@EnableScheduling` on `AccountReceivableManagementApplication` and `spring.task.scheduling.enabled=true` |
| Time zone | **No zone is configured.** `@Scheduled` has no `zone`, the scheduler uses `Clock.systemDefaultZone()`, and nothing sets `TimeZone.setDefault` or `-Duser.timezone`. "Today" is the JVM/container default zone (often UTC in containers) — **NOT VERIFIED** for the deployed image. Open business decision BD-10. |
| Error containment | `scheduledTick` catches any `RuntimeException` from discovery, logs a sanitized message, and the next tick retries. Each billing event is processed in isolation (`process(...)`); one failure never stops the batch. |
| Transaction | None around the batch. Each lookup, run creation and orchestrator step commits on its own. |

**Order of work in one tick** (`runOnce`), bounded by `batch-size` in total:

1. **Approval reconciliation (G-18)** — `AutomaticBillingRunRecoveryService.reconcileApprovedRuns(batchSize)`: `PENDING_APPROVAL` runs whose invoice is already `APPROVED`/`READY_TO_SEND` are marked `COMPLETED`. Runs outside the batch budget and regardless of retry setting.
2. **Straggler runs** — runs still `SCHEDULED` (created but never processed, e.g. instance stopped) via `AutomaticBillingRunRepository.findRunIdsByStatus(SCHEDULED)`.
3. **Recovery** —
   - retry enabled (`recover`): `PROCESSING` runs with expired claim (`findInterruptedRunIds`) are resumed, or closed as `FAILED` if `attemptCount >= maxAttempts`; then `FAILED` runs with `nextRetryAt <= now` (`findRunIdsDueForRetry`).
   - retry disabled (`recoverWithoutAutomaticRetry`, G-30): interrupted runs are closed as `FAILED`; only runs explicitly re-queued after their last failure (`findRequeuedRunIdsDue`) are processed.
4. **New occurrence-based work** — `BillingScheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun(today, earliestBillingDate, APPROVED, AUTOMATIC, UNBILLED_STATUSES, limit)`, oldest billing date first; each gets a run via `AutomaticBillingRunService.findOrCreateForOccurrence`.
5. **New Time & Material periods** — `findDueTimeAndMaterialPeriods` (see §3.1); each gets a run via `findOrCreateForTimeAndMaterialPeriod`.

Each run is then handed to `AutomaticBillingOrchestrator.processRun(runId)`.

### 1.3 Which configurations qualify

Occurrence query (`BillingScheduleRepository.findDueOccurrenceIdsWithoutAutomaticRun`) [IMPL, TESTED `AutomaticBillingSchedulerPersistenceTest`]:

```
c.approvalStatus = APPROVED
AND c.invoiceGenerationType = AUTOMATIC
AND c.manuallyDeactivated = false
AND s.isActive = true AND s.isInvoiced = false
AND s.billingDate <= today AND s.billingDate >= earliestBillingDate
AND s.periodStatus IN (SCHEDULED, TAX_PENDING, TAX_CALCULATED)
AND no AutomaticBillingRun exists for s
```

T&M configuration query (`BillingConfigurationRepository.findAutomaticConfigurationIdsByBillingTypeNamesDueBetween`): `APPROVED`, `AUTOMATIC`, `manuallyDeactivated = false`, billing type name in `{"timesheet based","time & material","time and material","time_material"}` (case-insensitive, trimmed), `effectiveFrom <= today` (or null), `effectiveTo >= earliestBillingDate` (or null).

Notes:
- **`billingStatus` (ACTIVE / INACTIVE / EXPIRED) is not filtered.** Due occurrences of an `EXPIRED` configuration are still billed — intentionally [TESTED `expiredConfiguration_itsFinalDueOccurrenceIsStillBilled`].
- `earliestBillingDate` = today if backfill is disabled; `today − maxAgeDays` if `maxAgeDays > 0`; otherwise `1900-01-01` (unlimited).
- An occurrence with a run of **any** status is never rediscovered; failures are retried through the existing run.

### 1.4 Manually deactivated configurations

`PATCH /api/billing-configurations/{id}/deactivate` (`BillingConfigurationServiceImpl.deactivateBillingConfiguration`) sets `manuallyDeactivated = true`, `billingStatus = INACTIVE`, and `isActive = false` on SCHEDULED and TAX_PENDING occurrences. Both discovery queries filter `manuallyDeactivated = false`, and deactivated occurrences also fail the `isActive` filter. TAX_CALCULATED/INVOICED occurrences are kept (and not discovered because of the config filter) [IMPL, TESTED `manualUnapprovedAndDeactivatedConfigurations_areSkipped`].

Already-existing runs of a deactivated configuration: `AutomaticBillingOrchestrator.resolveSource` re-checks approval and `AUTOMATIC` on every attempt but **does not check `manuallyDeactivated`**; it does reject an inactive occurrence ("Billing Occurrence … is no longer active.") [IMPL]. A T&M run created before deactivation could therefore still be processed if retried [INFERRED].

### 1.5 Creates occurrences or processes them?

**The scheduler never creates occurrences.** Occurrences (`billing_schedule` rows) are created at configuration **approval** (§2.3). The scheduler only creates `automatic_billing_run` rows and processes them. For T&M there are no persisted occurrences; periods are computed on the fly.

A separate job, `BillingOccurrenceStatusScheduler` (hard-coded `@Scheduled(cron = "0 * * * * *")`, every minute, not property-driven, **not gated by the automatic billing flag**), moves due occurrences `SCHEDULED → TAX_PENDING`. The orchestrator performs the same locked, idempotent transition itself, so order does not matter. `BillingConfigurationStatusScheduler` (also every minute, hard-coded) recalculates `billingStatus` of APPROVED, non-deactivated configurations.

### 1.6 Concurrency, restart and multi-instance safety

| Concern | Mechanism | Source |
|---|---|---|
| Same scheduler overlapping itself | Spring `@Scheduled` never runs the same task concurrently on one instance | framework |
| Two creators of the same run | Unique keys `uk_automatic_billing_run_period (billing_configuration_id, billing_period_start, billing_period_end)` and `uk_automatic_billing_run_schedule (billing_schedule_id)`; `findOrCreate` catches `DataIntegrityViolationException` and returns the winner | `entity/automatic_billing/AutomaticBillingRun`, `V7__automatic_billing_run.sql`, `AutomaticBillingRunServiceImpl.findOrCreate` |
| Two workers processing one run | Atomic conditional `UPDATE` claim (`AutomaticBillingRunRepository.claim`) — exactly one caller gets `1`; claim token required on every later update, which re-reads the row with `PESSIMISTIC_WRITE` (`updateOwnedRun`) | [TESTED `concurrentClaims_exactlyOneWorkerOwnsTheRun`, `manyConcurrentCalls_processTheRunExactlyOnce` on H2; MySQL test exists but was **skipped**] |
| Crash mid-run | Claim expires after `claim-duration` (15 min); claim is renewed before every step, so only an abandoned run expires; the next tick resumes from `lastCompletedStep` | `AutomaticBillingOrchestrator.startStep`, `recover` |
| Partial completion | Each step uses find-or-create (`BillingSnapshotService.createBillingSnapshot` reuses a project/period snapshot, `findOrCalculateTax*`, `findOrGenerateInvoice*`) backed by unique keys, so a resumed run never duplicates a snapshot, tax calculation or invoice | §8.1 |
| Multiple instances | Safe by the above (no ShedLock; not needed). Deployment currently has `replicas: 1` (`k8s/backend/ar/deployment.yaml`) | [INFERRED for >1 replica on MySQL] |

### 1.7 Retry policy

`AutomaticBillingRetryPolicy`:
- **Retryable**: `TimesheetsPendingApprovalException`, `TmsServiceUnavailableException`, `ResourceAccessException`, `IOException`, `TimeoutException`, transient/recoverable data-access and lock failures, `DuplicateResourceException`.
- **Permanent** (never retried, even if caused by a retryable type): `ValidationException`, `ResourceNotFoundException`, `IllegalArgumentException`; anything unrecognised is also not retried.
- Delay after attempt *n*: `initialDelay × multiplier^(n−1)`, capped at `maxDelay`; no retry once `attemptCount >= maxAttempts`.
- A run left `FAILED` with `nextRetryAt = null` needs an operator: `POST /api/v1/recovery/automatic-billing-runs/{runId}/requeue`.
- Configuration re-approval re-queues failed runs of re-planned occurrences or runs that failed before any billing step (`AutomaticBillingRunRepository.requeueFailedRunsOfOccurrences`).

---

## 2. Complete lifecycle and call graph

Legend: **[USER]** requires a user action; **[AUTO]** happens without a user; **[AUTO*]** only when the automatic billing flag is on.

### 2.1 Configuration create / draft save — [USER]

| Step | Endpoint | Method | Result |
|---|---|---|---|
| Create (full) | `POST /api/billing-configurations` | `BillingConfigurationController.create` → `BillingConfigurationServiceImpl.create` | `approvalStatus = DRAFT`, `billingStatus = INACTIVE`. No occurrences. |
| Create draft | `POST /api/billing-configurations/draft` | `createDraft` | DRAFT / INACTIVE. `invoiceGenerationType` may be null. |
| Save draft / edit | `PUT /api/billing-configurations/{id}/draft` | `saveDraft` | DRAFT stays DRAFT; APPROVED+ACTIVE → PENDING_APPROVAL + INACTIVE with a change-tracking snapshot (`BillingConfigurationChangeTrackingService`) |
| Full update | `PUT /api/billing-configurations/{id}` | `updateBillingConfiguration` → `handleApprovalStateTransition` | APPROVED → PENDING_APPROVAL + INACTIVE; REJECTED → DRAFT; **no snapshot** |
| Type details | Fixed price / milestone plan / recurring / T&M rate card endpoints (§4.3) | `BillingFixedPriceServiceImpl`, `BillingMilestonePlanServiceImpl`, `RecurringBillingServiceImpl`, `BillingTMRateCardServiceImpl` | Child records; none generates occurrences |

Transactions: `BillingConfigurationServiceImpl` is class-level `@Transactional`. Edits call `RecordActionLockService.validateNotLockedByAnotherUser` (409 if someone else holds a lock).

### 2.2 Submit for approval — [USER]

`PUT /api/billing-configurations/{id}/submit` → `BillingConfigurationServiceImpl.submitForApproval`: only from DRAFT; `effectiveFrom` required; `effectiveTo >= effectiveFrom`. → `PENDING_APPROVAL`. `BillingConfiguration.recordSubmitter()` (`@PrePersist/@PreUpdate`) stores `submittedByUserId` from `ActorContext.currentUserId()`. **No permission check.** Does **not** validate `invoiceGenerationType`.

### 2.3 Configuration approval — [USER, Finance approver]

`PUT /api/billing-configurations/{id}/approve` → `BillingConfigurationServiceImpl.approve` (one transaction):
1. `FinancialActionAuthorizer.requirePermission(BILLING_CONFIGURATION_APPROVAL)` → 403 otherwise.
2. `RecordActionLockService.validateLockOwner(BILLING_CONFIGURATION, id, APPROVAL)` → 409 unless the caller holds an APPROVAL lock (§4.2).
3. Must be `PENDING_APPROVAL`; effective dates valid; maker-checker (`requireDifferentFromMaker(submittedByUserId)`) → 403 if approver = submitter.
4. → `APPROVED`, `manuallyDeactivated = false`, `billingStatus` from `calculateBillingStatus` (INACTIVE before `effectiveFrom`, EXPIRED after project end / `effectiveTo`, else ACTIVE).
5. **Occurrence generation/reconciliation**: `BillingOccurrenceServiceImpl.reconcileOccurrencesOnConfigurationUpdate` (per type, §3), then `promoteDueOccurrencesToTaxPending`.
6. Re-queue failed runs of re-planned occurrences.
7. Any failure rolls back the whole approval (400 `ValidationException` / `DuplicateResourceException`).

**Approval succeeds with zero occurrences** if the type-specific child record is missing (no active fixed-price record, milestone plan or recurring term) [IMPL — the reconcile methods simply return]. The UI must ensure child data exists before submission.

### 2.4 Eligibility discovery — [AUTO*]

`AutomaticBillingScheduler.runOnce` (§1.2–1.3).

### 2.5 Occurrence / run creation and scheduling — [AUTO at approval] + [AUTO*]

- Occurrences: created at approval (§2.3).
- Runs: `AutomaticBillingRunServiceImpl.findOrCreateForOccurrence` / `findOrCreateForTimeAndMaterialPeriod` (`REQUIRES_NEW`), which re-check `requireAutomaticBilling` (APPROVED + AUTOMATIC) and that the event is due. A run of a removed-and-recreated occurrence for the same period is re-linked (`relinkRunOfReplacedOccurrence`).

### 2.6 Processing a run — [AUTO*]

`AutomaticBillingOrchestrator.processRun` (runs as `ActorContext.callAsSystem` — the system actor can never pass `requirePermission`):

```
claim (atomic)
 └─ resolveSource: config APPROVED? AUTOMATIC? billing type known? run shape matches type? occurrence active?
 ├─ SOURCE_DATA_ACQUISITION
 │    requireNoUndecidedFinanceTerms (Fixed Price retention/advance → permanent failure, G-32)
 │    T&M:   BillingSnapshotService.createBillingSnapshot(projectId, configId, periodStart, periodEnd)
 │    other: requireFixedPrice|MilestonePlan|RecurringOccurrenceBillable; SCHEDULED → TAX_PENDING
 ├─ TAX_CALCULATION   TaxCalculationService.findOrCalculateTax(snapshotId) | findOrCalculateTaxForSchedule(occurrenceId)
 ├─ INVOICE_CREATION  InvoiceService.findOrGenerateInvoice(snapshotId) | findOrGenerateInvoiceForSchedule(occurrenceId)
 ├─ SUBMIT_FOR_APPROVAL InvoiceService.submitForApproval(invoiceId)  (GENERATED → PENDING_APPROVAL)
 │     invoice already APPROVED/READY_TO_SEND → run COMPLETED
 │     invoice REJECTED → permanent failure (manual correction workflow)
 └─ run → PENDING_APPROVAL
```

No transaction is held across steps; run state is written by `AutomaticBillingRunService` in `REQUIRES_NEW` transactions between steps.

> Code comment discrepancy: the class Javadoc of `AutomaticBillingOrchestrator` says "approval, invoice generation and sending stay manual". In the code, the **invoice record is generated automatically** (`INVOICE_CREATION`); what stays manual is approval, **document (PDF) finalization** and sending.

### 2.7 T&M source-data acquisition — [AUTO*] or [USER]

`BillingSnapshotServiceImpl.createBillingSnapshot` → `TimeAndMaterialBillingStrategy.acquire` → `TimesheetIntegrationImpl` (`GET {TMS_API_BASE_URL}/api/timesheets/billing`, Spring `RestClient`) → `BillingAcquisitionValidator` → `BillingSnapshotBuilder` → persisted `BillingSnapshot` + `BillingSnapshotItem` + `BillingAcquisition` record. On a scheduler thread there is no user JWT, so TMS is called with `tms.api.service-token` (`TMS_SERVICE_TOKEN`); without it the call has no `Authorization` header [TESTED `TimesheetIntegrationImplTest.onASchedulerThread_withoutServiceToken_callsWithoutAuthorization`]. `BillingAcquisition.triggerMode` is always written as `MANUAL`, even for automatic runs [IMPL — `BillingAcquisitionServiceImpl`].

### 2.8 Billing snapshot creation

T&M only (§2.7). Occurrence-based types use the occurrence itself; no snapshot is created.

### 2.9 Tax calculation — [AUTO*] or [USER]

- Snapshot: `TaxCalculationServiceImpl.calculateTax` (snapshot must be `READY_FOR_TAX`; → `TAX_COMPLETED`).
- Occurrence: `calculateTaxForSchedule` (must be `TAX_PENDING`, active, `requireOccurrenceBillable`, amount ≥ 0, configuration has `taxRegion`, an applicable `TaxConfiguration` exists for region + `periodStartDate`); → period/tax status `TAX_CALCULATED`.
- Applicability: source jurisdiction = configuration tax region code; destination = project `primaryLocation` matched by name to `TaxRegionMaster` (`resolveApplicabilityForSchedule`). Same → `SAME_JURISDICTION`, different → `DIFFERENT_JURISDICTION`, unknown → `ALL` (and fails if the tax configuration has jurisdiction-specific components, G-12).
- Idempotent wrappers `findOrCalculateTax*` return an existing `CALCULATED` record; an existing `FAILED` record is reported, not reused.

### 2.10 Invoice generation — [AUTO*] or [USER]

`InvoiceServiceImpl.generateInvoice` (snapshot) / `generateInvoiceForSchedule` (occurrence, row-locked with `findByIdForUpdate`): requires completed tax, consistent totals, not already invoiced. Sets occurrence `INVOICED`, `isInvoiced = true`. Invoice status `GENERATED`.
- **`invoiceDate = LocalDate.now()`** (server zone) — the processing date, **not** the billing date. A backfilled occurrence is invoiced with today's date [IMPL].
- **`dueDate = invoiceDate + PaymentTermsMaster.paymentDays`** (`resolveDueDate`); null if no payment term.

### 2.11 Submission for Finance Manager approval — [AUTO*] or [USER]

`InvoiceServiceImpl.submitForApproval`: from `GENERATED` or `REJECTED` (rejected only after correction refresh) → `PENDING_APPROVAL`; history row `SUBMITTED`. **No permission check.** When automation submits, the history actor is the system (`actionByUserId = null`), so maker-checker does not block any approver.

### 2.12 Finance Manager invoice approval — [USER]

`POST /api/v1/invoices/{id}/approve` → `InvoiceApprovalController.approve`:
1. `InvoiceServiceImpl.approveInvoice` — `INVOICE_APPROVAL` permission, APPROVAL lock on `INVOICE`, must be `PENDING_APPROVAL`, maker-checker → `APPROVED`; registers an after-commit callback `completeAutomaticBillingRunsAfterCommit` that moves the invoice's `PENDING_APPROVAL` runs to `COMPLETED` (failures only logged; recovered by G-18 reconciliation each tick).
2. Then, in a separate transaction, `finalizeInvoiceDocument` (§2.13). A document failure does **not** undo the approval; response is still `200 success:true` with an explanatory `message` and `documentAvailable = false`.

Reject: `POST /api/v1/invoices/{id}/reject` (`INVOICE_APPROVAL`, APPROVAL lock, reason required) → `REJECTED`. The automatic run stays `PENDING_APPROVAL`; after correction, the invoice is resubmitted manually.

### 2.13 Transition to `READY_TO_SEND` — [USER-initiated, automatic within approve]

`InvoiceServiceImpl.finalizeInvoiceDocument`: requires `INVOICE_APPROVAL`; invoice `APPROVED`; `validateInvoiceReadyForDocument`; renders PDF (`InvoiceDocumentServiceImpl.generateInvoicePdf`); stores `InvoiceDocument` (unique per invoice, SHA-256 checksum) → `READY_TO_SEND`. Retry via `POST /api/v1/invoices/{id}/document`. Idempotent once `READY_TO_SEND`.

### 2.14 Invoice delivery — [USER]

`POST /api/v1/invoices/{id}/send` → `InvoiceDeliveryServiceImpl.sendInvoice` (must not run inside a transaction):
1. **Claim** (transaction, invoice row lock): `INVOICE_DELIVERY` permission; invoice `READY_TO_SEND` with stored document and valid totals; recipient = `invoice.email`, else client email; no delivery `SENT`, `PENDING` or `UNCERTAIN` → commits a `PENDING` `InvoiceDelivery`.
2. **Send** (no transaction): `EmailService.sendEmailWithAttachment` with the stored PDF (Gmail SMTP, `spring.mail.*`).
3. **Record** (own transaction): `SENT`, `FAILED`, or `UNCERTAIN` (read timeout — the server may have accepted the mail).

There is **no automatic sending** and **no endpoint to record an out-of-band (manual) delivery**. The invoice status stays `READY_TO_SEND` after sending; delivery state lives only on `InvoiceDelivery`.

### 2.15 Receivable creation — [AUTO after SENT]

After `SENT` is committed, `createReceivable` → `ReceivableServiceImpl.createReceivableFromInvoice` (own transaction, invoice row lock, idempotent, `uk_receivable_invoice`): `OUTSTANDING`, `invoiceAmount = outstandingBalance = grandTotal`. A failure is logged and does not undo `SENT`.

### 2.16 Receivable reconciliation and recovery

- **[AUTO]** `ReceivableReconciliationJob.reconcile` (`ar.receivable-reconciliation.cron`, default `0 */15 * * * *`; enabled unless `AR_RECEIVABLE_RECONCILIATION_ENABLED=false`) → `ReceivableRecoveryServiceImpl.reconcileSentInvoicesWithoutReceivable`. Not gated by the automatic billing flag. Never sends email.
- **[USER, OPERATIONS_RECOVERY]** `POST /api/v1/recovery/invoices/{invoiceId}/receivable`.
- **[USER, OPERATIONS_RECOVERY]** `POST /api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` — resolves `UNCERTAIN` (or `PENDING` older than `ar.invoice-delivery.pending-timeout`, default 10 min) to `SENT` (creates receivable) or `FAILED`.

### 2.17 Automatic vs. user transitions — summary

| Transition | Who |
|---|---|
| Config DRAFT → PENDING_APPROVAL | User (submit) |
| Config PENDING_APPROVAL → APPROVED / REJECTED | Finance approver |
| Occurrence generation | System, inside approval |
| Occurrence SCHEDULED → TAX_PENDING | System (`BillingOccurrenceStatusScheduler`, every minute; also approval and orchestrator) |
| Config billingStatus ACTIVE/INACTIVE/EXPIRED | System (`BillingConfigurationStatusScheduler`, every minute) |
| Run creation, snapshot, tax, invoice draft, submit | System **only if flag on and config AUTOMATIC**; otherwise user via manual endpoints |
| Invoice PENDING_APPROVAL → APPROVED → READY_TO_SEND | Finance Manager (one click; PDF step may need a retry) |
| Invoice send | User with INVOICE_DELIVERY |
| Receivable creation | System after SENT (plus 15-min reconciliation) |
| Run requeue, approval reconciliation, stale-tax recalculation, delivery resolution, receivable recovery | Operator with OPERATIONS_RECOVERY (approval reconciliation is also automatic each tick) |

---

## 3. Billing type behaviour

### 3.0 How the type is identified

There is **no type code**. The type is the free-text `BillingTypeMaster.billingTypeName` reached through `BillingConfiguration.billingType`, and different layers match it differently:

| Layer | Rule | Source |
|---|---|---|
| Automation, billability, strategy | `BillingConfigurationIntegrationImpl.toBillingTypeEnum`: normalise (`&`→`AND`, non-alphanumerics→`_`, upper-case); contains `TIMESHEET`/`TIME_MATERIAL`/`TIME_AND_MATERIAL` or equals `T_M` → `TIME_AND_MATERIAL`; `FIXED` → `FIXED_PRICE`; `MILESTONE` → `MILESTONE`; `RECURRING`/`SUBSCRIPTION`/`RETAINER` → `RETAINER`; `MANUAL` → `MANUAL`; else `null` (never defaults to T&M, G-15) | [TESTED `BillingConfigurationIntegrationImplTest`] |
| Occurrence reconciliation at approval | `equalsIgnoreCase`: "Fixed Price", "Recurring"/"Subscription", "Milestone Plan"; anything else → no occurrences | `BillingOccurrenceServiceImpl.reconcileOccurrencesOnConfigurationUpdate` |
| T&M create validation, rate cards | only "Timesheet Based" | `BillingConfigurationServiceImpl.create`, `BillingTMRateCardServiceImpl` |
| T&M discovery | `{"timesheet based","time & material","time and material","time_material"}` | `BillingOccurrenceServiceImpl.TIME_AND_MATERIAL_BILLING_TYPE_NAMES` |

**Consequence for the frontend:** the safe master names are exactly **"Timesheet Based"**, **"Fixed Price"**, **"Milestone Plan"**, **"Recurring"** or **"Subscription"**. Other spellings may be accepted in one layer and rejected in another. Which names exist in each environment's `billing_type_master` is **NOT VERIFIED** (not seeded in the repo).

`BillingType` enum: `TIME_AND_MATERIAL, MILESTONE, FIXED_PRICE, RETAINER, MANUAL`.

### 3.1 Time & Material ("Timesheet Based")

| Aspect | Behaviour |
|---|---|
| Occurrences | **None persisted.** Periods computed by `BillingOccurrenceServiceImpl.resolveTimeAndMaterialBillingPeriods(configId, asOf)` |
| Dates | Range start = `effectiveFrom` (required); range end = earlier of `effectiveTo` and project end (may be open-ended). Frequency `BillingConfiguration.billingFrequency` required (but **not** `@NotNull` on create). "One-Time" → one period (needs a range end). Otherwise `durationValue`/`durationUnit`, anchor-based on `effectiveFrom` with month-end handling (`calculatePeriodEndDate`). **billingDate = period end**; last period clipped and partial. |
| Trigger | Billing frequency + effective dates |
| Amount | Per timesheet line `hours × hourlyRate`, `HALF_UP` to 2 dp (`BillingSnapshotBuilder.lineAmount`); subtotal = Σ lines; expenses always 0 (BD-03). `ROLE_BASED`: hourly rate card matching role and work date, no fallback. `STANDARD`: role card → blank-role card → `BillingConfiguration.hourlyRate`. |
| Required sources | Approved, billable TMS timesheets with hours > 0 not claimed by another snapshot; rate card or hourly rate; project; configuration `billingStatus` ACTIVE (or EXPIRED with the period inside its range) |
| Tax region / currency | From the configuration. Destination jurisdiction from project `primaryLocation`; missing → failure. Currency = configuration currency (PMS `projectBudgetCurrency` for PROJECT context); missing/unknown → failure (G-13). |
| Automatic discovery | §1.3; skipped if a run exists that is COMPLETED/PENDING_APPROVAL/PROCESSING/SCHEDULED or has `nextRetryAt`/invoice/snapshot; **skipped if any snapshot of the project overlaps the period** (left to manual flow). A FAILED run with no financial records is rediscovered once per day (G-14, late-approved timesheets). |
| Missing data | Unapproved timesheets → `TimesheetsPendingApprovalException` (retryable). No timesheets / missing rate → `ValidationException` (permanent; G-14 rediscovery next day). TMS 5xx → retryable; TMS 400 → permanent; TMS 401/403 → not retryable. Missing frequency/effectiveFrom → discovery error each tick, no run. |
| Change / re-approval / deactivation | Nothing to regenerate. While PENDING_APPROVAL, discovery skips it and existing runs fail at `resolveSource`. `updateRateCard` sends APPROVED → PENDING_APPROVAL; `addRateCard`/`saveRateCard`/`deleteRateCard` do **not** change approval state. |
| AUTOMATIC allowed | Yes |
| Tests | [TESTED] orchestrator (T&M group), scheduler T&M discovery, `TimeAndMaterialBillingStrategyTest`, `BillingSnapshotServiceImplTest`, `TimesheetIntegrationImplTest`, E2E `timeAndMaterial_billsEachRolesHoursAtItsRate_andNeverBillsATimesheetTwice`. TMS is always mocked or a local HTTP stub. |

### 3.2 Fixed Price ("Fixed Price")

| Aspect | Behaviour |
|---|---|
| Child record | `BillingFixedPriceConfiguration` via `POST /api/billing-fixed-price/{billingConfigurationId}/fixed-price`. Requires frequency already set on the configuration, `contractValue > 0`, `retentionPercentage` 0–100, `advanceReceived ≥ 0` and ≤ billable amount after retention. Create does **not** generate occurrences. |
| Dates | Fixed-price `effectiveFrom/To` falling back to the configuration's (both required). **One-Time** frequency: one occurrence, **billingDate = effectiveTo**, amount = contract value. **Periodic**: periods from `effectiveFrom` (or the day after the last billed period), billingDate = period end. |
| Trigger | Billing frequency + effective dates |
| Amount | **Equal split** per period, `HALF_EVEN` 2 dp; last period absorbs the remainder; partial periods are not prorated (`calculateFixedPricePeriodAmount`). Regeneration splits only the unbilled remainder. |
| Required at billing | `requireFixedPriceOccurrenceBillable`: active FP record, contract value > 0, amount > 0, currency, active occurrences ≤ contract value |
| Tax / currency | Configuration `taxRegion` + `TaxConfiguration` for (region, period start); configuration currency |
| Change / re-approval | `reconcileFixedPriceOccurrences` at approve: unchanged → no-op; effective-from change → retire replaceable rows and regenerate (refused if billed rows would fall before the new start); frequency change → regenerate; amount change → reallocate in place (refused if any row billed); otherwise regenerate. Retired rows are `isActive=false`, never deleted; identical periods are reactivated. |
| AUTOMATIC allowed | Yes, **except** retention > 0 or advance > 0 → run fails permanently at `SOURCE_DATA_ACQUISITION` with "…bill it manually after review" (`AutomaticBillingOrchestrator.requireNoUndecidedFinanceTerms`, `BillingOccurrenceServiceImpl.undecidedFixedPriceTerms`; BD-01/BD-02 open). Manual billing of such configurations is not blocked. |
| Tests | [TESTED] FP billability, FIR G02–G05 and G10, E2E `fixedPrice_fromConfigurationToReceivable`, orchestrator `fixedPriceWithUndecidedRetentionOrAdvance_isNotBilledAutomatically`. **`BillingFixedPriceServiceImpl` CRUD has no tests.** |

### 3.3 Milestone ("Milestone Plan", `paymentStructure = FULL_PAYMENT`)

| Aspect | Behaviour |
|---|---|
| Child records | `BillingMilestonePlan` (one per configuration) + `BillingPaymentEntry` rows via `POST /api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` |
| Validation | `totalContractValue > 0`; FULL_PAYMENT = exactly one entry at 100 % with a `billingDate`. Entry dates vs project dates are checked **only on update**, not create. |
| Dates | One occurrence per active entry: `periodStart = periodEnd = billingDate = entry.billingDate`. Two entries on one date fail the approval. |
| Trigger | **Payment entry dates** (not frequency) |
| Amount | `total × % / 100`, `HALF_EVEN` 2 dp; last entry absorbs remainder |
| Required at billing | `requireMilestonePlanOccurrenceBillable`: active plan, matching entry (same sequence, date, amount), no duplicate, total within plan |
| Change / re-approval | `update()` replaces entries and sends APPROVED → PENDING_APPROVAL; occurrences reconciled only on next approve (`reconcileMilestonePlanOccurrences`) |
| `effectiveTo` | Ignored for Milestone Plan in update/draft save |
| AUTOMATIC allowed | Yes; no retention/advance guard |
| Tests | [TESTED] `MilestonePlanOccurrenceBillabilityTest`, orchestrator milestone tests. **`BillingMilestonePlanServiceImpl` has no tests.** |

### 3.4 Installment ("Milestone Plan", `paymentStructure = INSTALLMENTS`)

Same generation, reconciliation, billability and automation code as Milestone [IMPL]. Proven differences only:
- Validation (`validateInstallmentEntries`): sequences > 0 and unique, each percentage > 0 and ≤ 100, total exactly 100, each `billingDate` required.
- Invoice line text: "Installment n of N" vs "Milestone n — …" (`InvoiceServiceImpl` schedule line description); item type `MILESTONE` for both.
- Not identifiable as a separate `BillingType`; a master named "Installment" maps to `null` and is rejected as unsupported.

[TESTED] E2E `installments_eachDueInstallmentIsInvoicedForItsOwnAmount`, orchestrator installment tests.

### 3.5 Recurring / Subscription / Retainer ("Recurring" or "Subscription")

| Aspect | Behaviour |
|---|---|
| Child record | `BillingRecurringConfiguration` via `POST /api/billing-recurring/{billingConfigurationId}`; parent must be DRAFT; frequency required with `durationValue > 0`; `PMS_BUDGET` → contract value from project budget (> 0); `MANUAL` → request value |
| Dates | Per active term: `recurringStartDate/EndDate` falling back to configuration dates; term frequency falling back to configuration frequency; **billingDate = period end** |
| Trigger | Billing frequency + term dates |
| Amount | **Day-prorated** share of term contract value, `HALF_EVEN` 2 dp; last period takes remainder |
| Required at billing | `requireRecurringOccurrenceBillable`: term linked and active, value > 0, amount > 0, currency, period inside term, no overlap, Σ ≤ term value |
| Renewal | `POST /api/billing-recurring/{recurringConfigurationId}/renew` — only PRODUCT_SERVICE context, APPROVED config, one renewal per term, no overlap; new term inactive with `renewalApprovalStatus = PENDING_APPROVAL`; config → PENDING_APPROVAL (billingStatus **not** set INACTIVE). Activated and given occurrences on the next config approve; config reject marks renewal REJECTED **and** the whole config REJECTED. |
| `RenewalType.AUTO` | Stored only — **no automatic renewal exists** |
| AUTOMATIC allowed | Yes |
| Tests | [TESTED] `RecurringOccurrenceBillabilityTest`, FIR G01 and G07, E2E `recurringRetainer_…`. `RecurringBillingServiceImpl.update/delete/getBillingSchedule` untested. |

### 3.6 Manual

`BillingType.MANUAL` (name containing "MANUAL") has no generator, strategy or discovery; it is never billed automatically [INFERRED]. Not to be confused with `InvoiceGenerationType.MANUAL`, which is the per-configuration switch that keeps **any** type out of automation.

### 3.7 Schedule preview vs. generated occurrences

`POST /api/billing-configurations/preview-schedule` (`previewSchedule`) supports only Fixed Price and Recurring, uses plain `plusMonths(n).minusDays(1)` period math and always day-proration, and returns `billingDate = null`. Generation uses anchor/month-end period math and an **equal split** for Fixed Price. **Preview amounts for periodic Fixed Price — and dates for month-end/clamped anchors — can differ from the occurrences generated at approval** [INFERRED]. The authoritative schedule is `GET /api/billing-occurrences?billingConfigurationId=…` **after approval**.

---

## 4. Authoritative API contract inventory

### 4.0 Base URL and context path

- `/paves/ar` is a **servlet context path**, not part of any `@RequestMapping`: `server.servlet.context-path=${SERVER_SERVLET_CONTEXT_PATH:}` (default empty). k8s sets `SERVER_SERVLET_CONTEXT_PATH=/paves/ar`; ingress routes `/paves/ar` (`k8s/ingress/ingress-ar-path.yaml`).
- Locally the context path is empty. `config/LocalContextPathFilter` (`@Component`, highest precedence, **active in every profile**) forwards `/paves/ar/**` to `/**` when the context path is empty, so both forms work locally. It is untested, and whether Spring Security re-authenticates the FORWARD dispatch is **NOT VERIFIED** at runtime.
- **Frontend rule:** configure one base URL — `http://localhost:8080` locally or `https://<host>/paves/ar` in k8s — and append the controller paths below (which start with `/api/...`). Never add `/paves/ar` twice.
- `InvoiceResponseDto.documentUrl` is `"/api/v1/invoices/{id}/document"` — **without** the context path; prefix it with the base URL.
- `AR_FRONTEND_END_TO_END_TEST_GUIDE.md` cites paths that do not exist (`/api/billing-snapshots`, `/api/tax-calculations/calculate`, `/api/v1/invoices/generate/snapshot/{id}`) and a non-existent `MakerCheckerException`. Use this document instead.

### 4.1 Response envelopes and errors

- Most endpoints: `dto/centralizeddto/ApiResponse<T>` = `{ success, message, data, timestamp }` (`timestamp` usually `null`).
- `BillingSnapshotController` only: `dto/common/ApiResponse<T>` = `{ success, message, data }`.
- **Unwrapped** (raw DTO) responses: `GET /api/billing-configurations/{id}`, `GET /api/billing-configurations`, all `GET/POST /api/billing-occurrences/**` except `POST /{id}/invoice`, `/api/record-locks/**`, `GET /api/currency/active`, `PUT /api/currency/{id}`, `GET /health`, PDF download.
- Dates are ISO strings; `LocalDateTime` has no zone.

`GlobalExceptionHandler` mapping (body `{success:false, message, data:null, timestamp:null}`):

| Exception | HTTP |
|---|---|
| Bean validation (`@Valid`) | 400 — **first** field error message only |
| `ValidationException`, `IllegalArgumentException`, `IllegalStateException`, `DuplicateBillingConfigurationException` | 400 |
| `DuplicateResourceException` | **400** (not 409) |
| `AccessDeniedException` (missing financial permission, maker-checker) | 403 |
| `ResourceNotFoundException`, unmapped path | 404 |
| `RecordLockedException` (no/expired/foreign lock) | 409 |
| `ObjectOptimisticLockingFailureException` | 409 "This record was modified by another user…" |
| Anything else | 500 generic message |

Malformed JSON, invalid enum strings, bad UUIDs in path/query and missing required query parameters are **not** specially handled and fall to the catch-all → **500** [INFERRED, NOT VERIFIED]. The frontend must validate enums/UUIDs/dates client-side.

Security-layer errors: 401 from `CustomAuthenticationEntryPoint` (`{"detail":"401: Token has expired"}` for expiry, otherwise `ApiResponse` "Authentication failed: …"); 403 on `/api/v1/operations/**` from `CustomAccessDeniedHandler` (`{"message":"Not allowed: You do not have the required role."}`).

### 4.2 Authorization model

- Every endpoint except `OPTIONS /**`, `/health`, `/actuator/health{,/liveness,/readiness}`, `/public/**` and (when enabled) Swagger requires a valid JWT.
- There are **no** `@PreAuthorize` annotations. Financial permissions are enforced in services by `FinancialActionAuthorizer.requirePermission(FinancialAction)`:

| FinancialAction | Property / env var | Enforced in |
|---|---|---|
| `BILLING_CONFIGURATION_APPROVAL` | `ar.security.authorization.configuration-approval-authorities` / `AR_AUTH_CONFIGURATION_APPROVAL` | config approve, reject; acquiring an APPROVAL lock on `BILLING_CONFIGURATION` |
| `INVOICE_APPROVAL` | `…invoice-approval-authorities` / `AR_AUTH_INVOICE_APPROVAL` | invoice approve, reject, document finalize; APPROVAL lock on `INVOICE` |
| `INVOICE_DELIVERY` | `…invoice-delivery-authorities` / `AR_AUTH_INVOICE_DELIVERY` | invoice send |
| `OPERATIONS_RECOVERY` | `…operations-recovery-authorities` / `AR_AUTH_OPERATIONS_RECOVERY` | all `/api/v1/recovery/**` |
| (operations reports) | `ar.operations.report-authorities` / `AR_OPERATIONS_REPORT_AUTHORITIES` | `/api/v1/operations/**` via `OperationsReportAccess` |

- Values are comma-separated JWT authorities: `ROLE_<x>` from the `roles` claim or raw entries of the `permissions` claim. **Empty = nobody** (default in every profile).
- Maker-checker (`AR_AUTH_MAKER_CHECKER`, default `true`) on config approve and invoice approve only (not reject). System-submitted invoices (automation) have no maker.
- **Record locks** (`/api/record-locks`) are a precondition for approve/reject: `POST /api/record-locks/{BILLING_CONFIGURATION|INVOICE}/{id}` with `{"actionType":"APPROVAL"}`, lock lasts 10 minutes, extend with `PUT …/heartbeat`, release with `DELETE` (204). Edit endpoints only fail (409) if *another* user holds a lock.

### 4.3 Endpoint table

All paths are relative to the base URL (§4.0). "JWT" = any authenticated user. Status codes list the codes produced by code paths of that endpoint (500 for framework errors is omitted, see §4.1).

#### Billing configuration — `BillingConfigurationController` (`/api/billing-configurations`)

| Purpose | Method | Path | Request | Response | Authority | Status |
|---|---|---|---|---|---|---|
| Create | POST | `/api/billing-configurations` | `BillingConfigurationRequestDto` (`@Valid`) | `ApiResponse<BillingConfigurationResponseDto>` | JWT | 201, 400, 404 |
| Full update | PUT | `/api/billing-configurations/{billingConfigurationId}` | `BillingConfigurationRequestDto` | `ApiResponse<BillingConfigurationResponseDto>` | JWT (+ no foreign lock) | 200, 400, 404, 409 |
| Create draft | POST | `/api/billing-configurations/draft` | `BillingConfigurationDraftRequestDto` | `ApiResponse<BillingConfigurationDraftResponseDto>` | JWT | 201, 400, 404 |
| Save draft / edit approved | PUT | `/api/billing-configurations/{id}/draft` | `BillingConfigurationDraftRequestDto` | `ApiResponse<BillingConfigurationDraftResponseDto>` | JWT | 200, 400, 404, 409 |
| Submit | PUT | `/api/billing-configurations/{id}/submit` | — | `ApiResponse<BillingConfigurationResponseDto>` | JWT | 200, 400, 404, 409 |
| Approve | PUT | `/api/billing-configurations/{id}/approve` | — | `ApiResponse<BillingConfigurationResponseDto>` | BILLING_CONFIGURATION_APPROVAL + APPROVAL lock; maker-checker | 200, 400, 403, 404, 409 |
| Reject | PUT | `/api/billing-configurations/{id}/reject` | `{ rejectionReason }` (`@NotBlank`, ≤500) | `ApiResponse<BillingConfigurationResponseDto>` | BILLING_CONFIGURATION_APPROVAL + APPROVAL lock | 200, 400, 403, 404, 409 |
| Deactivate | PATCH | `/api/billing-configurations/{id}/deactivate` | — | `ApiResponse<String>` (`"SUCCESS"`) | JWT | 200, 400, 404, 409 |
| Delete draft | DELETE | `/api/billing-configurations/{id}` | — | `ApiResponse<Void>` | JWT | 200, 400, 404, 409 |
| Get one | GET | `/api/billing-configurations/{id}` | — | **unwrapped** `BillingConfigurationResponseDto` | JWT | 200, 404 |
| List all | GET | `/api/billing-configurations` | — | **unwrapped** `List<BillingConfigurationResponseDto>` | JWT | 200 |
| Pending approvals | GET | `/api/billing-configurations/pending-approvals` | — | `ApiResponse<List<BillingConfigurationResponseDto>>` | JWT | 200 |
| Approved config of project | GET | `/api/billing-configurations/project/{projectId}` | — | `ApiResponse<BillingConfigurationResponseDto>` | JWT | 200, **500 when none** (plain `RuntimeException`) |
| Schedule preview | POST | `/api/billing-configurations/preview-schedule` | `BillingSchedulePreviewRequest` | `ApiResponse<List<BillingPeriodDto>>` | JWT | 200, 400, 404 |
| Clients | GET | `/api/billing-configurations/clients` | — | `ApiResponse<List<ClientResponseDto>>` | JWT | 200 |
| Projects of client | GET | `/api/billing-configurations/projects/{clientId}` | — | `ApiResponse<List<ProjectResponseDto>>` | JWT | 200 |
| Available projects | GET | `/api/billing-configurations/available-projects?clientId={uuid}` | query `clientId` (required) | `ApiResponse<List<ProjectResponseDto>>` | JWT | 200, 404 |

#### Type details

| Purpose | Method | Path | Request | Response | Status |
|---|---|---|---|---|---|
| Fixed price create | POST | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | `BillingFixedPriceRequestDto` | `ApiResponse<BillingFixedPriceResponseDto>` | 201, 400, 404, 409 |
| Fixed price update / get / delete | PUT / GET / DELETE | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | same | same | 200, 400, 404, 409 |
| Fixed price of config | GET | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | — | `ApiResponse<List<…>>` | 200 |
| Milestone plan create | POST | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | `BillingMilestonePlanRequestDto` | `ApiResponse<BillingMilestonePlanResponseDto>` | 201, 400, 404, 409 |
| Milestone plan update / get / delete | PUT / GET / DELETE | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | same | same | 200, 400, 404, 409 |
| Milestone plan of config | GET | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | — | `ApiResponse<…>` | 200, 404 |
| Recurring create | POST | `/api/billing-recurring/{billingConfigurationId}` | `RecurringBillingRequestDto` | `ApiResponse<RecurringBillingResponseDto>` | 201, 400, 404, 409 |
| Recurring update / get / delete | PUT / GET / DELETE | `/api/billing-recurring/{recurringConfigurationId}` (note: **different id type** than POST) | same | same | 200, 400, 404, 409 |
| Recurring terms of config | GET | `/api/billing-recurring/billing-configuration/{billingConfigurationId}` | — | `ApiResponse<List<…>>` | 200 |
| Recurring schedule | GET | `/api/billing-recurring/{recurringConfigurationId}/schedule` | — | `ApiResponse<List<BillingPeriodDto>>` | 200 |
| Renew | POST | `/api/billing-recurring/{recurringConfigurationId}/renew` | `RenewalRequestDto` | `ApiResponse<RecurringBillingResponseDto>` | 201, 400 |
| Renewal history | GET | `/api/billing-recurring/{recurringConfigurationId}/renewal-history` | — | `ApiResponse<List<…>>` | 200 |
| T&M rate card add | POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | `BillingTMRateCardRequestDto` | `ApiResponse<BillingTMRateCardResponseDto>` | 201, 400, 404, 409 |
| T&M rate cards list | GET | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | — | `ApiResponse<List<…>>` | 200 |
| T&M rate card update / get / delete | PUT / GET / DELETE | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | same | same | 200, 400, 404, 409 |
| T&M rate card save (duplicate of add) | POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards/save` | same | same | 201, 400 |

All type-detail endpoints: JWT only.

#### Master data (all JWT, all `ApiResponse`)

| Base path | Useful for the wizard |
|---|---|
| `/api/billing-types` (`GET /active`) | billing type dropdown |
| `/api/billing-frequency` (`GET /active`) | frequency (`durationValue`, `durationUnit`) |
| `/api/currency` (`GET /active` — **unwrapped**) | currency (PRODUCT_SERVICE only) |
| `/api/payment-terms` (`GET /active`) | payment term (`paymentDays` → invoice due date) |
| `/api/tax-region` (`GET /active`) | tax region |
| `/api/v1/tax-rate-configurations` (`GET /active`, `GET /tax-region/{id}`) | confirm a tax configuration exists for the region |
| `/api/tax-structure/regions/{taxRegionId}` | tax structure display |
| `/api/proration-rule`, `/api/tax-regime-master`, `/api/tax-type-master`, `/api/tax-component-master`, `/api/tax-configuration-components` | admin screens |

#### Occurrences, snapshot, tax, invoice generation

| Purpose | Method | Path | Request | Response | Authority | Status |
|---|---|---|---|---|---|---|
| List occurrences | GET | `/api/billing-occurrences` | query: `billingConfigurationId`, `recurringConfigurationId`, `periodStatus`, `taxStatus`, `billingDateBefore`, `billingDateAfter` (at least one of the first four required; only `isActive=true` rows) | **unwrapped** `List<BillingOccurrenceResponseDto>` | JWT | 200, 400, 404 |
| Get occurrence | GET | `/api/billing-occurrences/{occurrenceId}` | — | **unwrapped** `BillingOccurrenceResponseDto` | JWT | 200, 404 |
| Occurrence tax | GET | `/api/billing-occurrences/{occurrenceId}/tax-calculation` | — | **unwrapped** `TaxCalculationResponseDto` | JWT | 200, 404 |
| Calculate occurrence tax (manual) | POST | `/api/billing-occurrences/{occurrenceId}/calculate-tax` | — | **unwrapped** `TaxCalculationResponseDto` | JWT | 200, 400, 404 |
| Generate invoice from occurrence (manual) | POST | `/api/billing-occurrences/{occurrenceId}/invoice` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 201, 400, 404 |
| Create T&M snapshot (manual) | POST | `/api/v1/billing-snapshots` | `{ projectId*, billingConfigurationId, billingPeriodStart*, billingPeriodEnd* }` | `common.ApiResponse<BillingSnapshotResponseDto>` | JWT | **200 even for business failures** (`success:false`), 400, 404 |
| Snapshot by period | GET | `/api/v1/billing-snapshots/by-period?projectId&billingPeriodStart&billingPeriodEnd[&billingConfigurationId]` | — | `common.ApiResponse<BillingSnapshotResponseDto>` | JWT | 200 (`success:false` if absent) |
| Snapshot tax | POST / GET | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | — | `ApiResponse<TaxCalculationResponseDto>` | JWT | 201 / 200, 400, 404 |
| Invoice preview (snapshot only) | GET | `/api/v1/billing-snapshots/{snapshotId}/invoice-preview` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 400, 404 |
| Generate invoice from snapshot | POST | `/api/v1/billing-snapshots/{snapshotId}/invoice` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 201, 400, 404 |
| Invoice of snapshot | GET | `/api/v1/billing-snapshots/{snapshotId}/invoice` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 404 |
| Invoice workspace | GET | `/api/v1/invoice-generation/workspace` | — | `ApiResponse<InvoiceGenerationWorkspaceResponseDto>` | JWT | 200 |
| Active configs for acquisition | GET | `/api/billing-data-acquisition/active-configurations` | — | `ApiResponse<List<BillingDataAcquisitionResponseDto>>` | JWT | 200 |
| Record acquisition (internal-looking; snapshot creation already does it) | POST | `/api/billing-data-acquisition/acquire` | `BillingAcquisitionRequestDto` | `ApiResponse<AcquireDataResponseDto>` | JWT | 200, 400 |

There is **no** occurrence-based invoice preview and **no** snapshot cancel endpoint.

#### Invoices, approval, document, delivery

| Purpose | Method | Path | Request | Response | Authority | Status |
|---|---|---|---|---|---|---|
| List invoices | GET | `/api/v1/invoices` | — | `ApiResponse<List<InvoiceSummaryResponseDto>>` | JWT | 200 |
| Get invoice | GET | `/api/v1/invoices/{invoiceId}` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 404 |
| Submit for approval | POST | `/api/v1/invoices/{invoiceId}/submit-for-approval` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 400, 404 |
| Approve (+ finalize PDF) | POST | `/api/v1/invoices/{invoiceId}/approve` | — | `ApiResponse<InvoiceResponseDto>` | INVOICE_APPROVAL + APPROVAL lock; maker-checker | 200, 400, 403, 404, 409 |
| Generate document (retry) | POST | `/api/v1/invoices/{invoiceId}/document` | — | `ApiResponse<InvoiceResponseDto>` | INVOICE_APPROVAL | 200, 400, 403, 404, 500 (PDF render failure) |
| Download PDF | GET | `/api/v1/invoices/{invoiceId}/document` | — | `application/pdf` attachment | JWT | 200, 404 |
| Reject | POST | `/api/v1/invoices/{invoiceId}/reject` | `{ reason }` (required; length not enforced by controller) | `ApiResponse<InvoiceResponseDto>` | INVOICE_APPROVAL + APPROVAL lock | 200, 400, 403, 404, 409 |
| Refresh after correction | POST | `/api/v1/invoices/{invoiceId}/refresh-after-correction` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 400, 404 |
| Non-financial correction | PATCH | `/api/v1/invoices/{invoiceId}/non-financial-correction` | `{ clientName, projectName }` | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 400, 404 |
| Financial correction (re-acquire T&M) | POST | `/api/v1/invoices/{invoiceId}/financial-correction/reacquire` | — | `ApiResponse<InvoiceResponseDto>` | JWT | 200, 400, 404 |
| Approval history | GET | `/api/v1/invoices/{invoiceId}/approval-history` | — | `ApiResponse<List<InvoiceApprovalHistoryResponseDto>>` | JWT | 200 |
| Pending approval list | GET | `/api/v1/invoices/pending-approval` | — | `ApiResponse<List<InvoiceApprovalSummaryResponseDto>>` | JWT | 200 |
| Approval workspace | GET | `/api/v1/invoices/approval-workspace` | — | `ApiResponse<List<InvoiceApprovalWorkspaceResponseDto>>` | JWT | 200 |
| Send to client | POST | `/api/v1/invoices/{invoiceId}/send` | — | `ApiResponse<InvoiceDeliveryResponseDto>`; `success:true` only when `SENT` | INVOICE_DELIVERY | **200 for SENT/FAILED/UNCERTAIN**, 400, 403, 404 |
| Delivery history | GET | `/api/v1/invoices/{invoiceId}/deliveries` | — | `ApiResponse<List<InvoiceDeliveryResponseDto>>` | JWT | 200, 404 |

#### Receivables

| Purpose | Method | Path | Request | Response | Authority | Status |
|---|---|---|---|---|---|---|
| Outstanding receivables | GET | `/api/v1/receivables/outstanding` | optional `clientId`, `projectId`, `status` (`OUTSTANDING`/`PARTIALLY_PAID`/`SETTLED`) | `ApiResponse<List<ReceivableResponseDto>>` (balance > 0, earliest due first) | JWT | 200 |
| Get receivable | GET | `/api/v1/receivables/{receivableId}` | — | `ApiResponse<ReceivableResponseDto>` | JWT | 200, 404 |

#### Recovery (operator) — 200 when accepted, **409 when not eligible** (body explains)

| Purpose | Method | Path | Request | Response | Authority |
|---|---|---|---|---|---|
| Re-queue failed/interrupted run | POST | `/api/v1/recovery/automatic-billing-runs/{runId}/requeue` | `{ reason }` | `ApiResponse<RecoveryResultDto>` | OPERATIONS_RECOVERY |
| Complete run whose invoice is approved | POST | `/api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval` | — | `ApiResponse<RecoveryResultDto>` | OPERATIONS_RECOVERY |
| Run recovery history | GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | — | `ApiResponse<List<AutomaticBillingRunEventDto>>` | OPERATIONS_RECOVERY |
| Recalculate stale tax (BD-07) | POST | `/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax` | `{ reason }` | `ApiResponse<RecoveryResultDto>` | OPERATIONS_RECOVERY |
| Create missing receivable | POST | `/api/v1/recovery/invoices/{invoiceId}/receivable` | `{ reason }` | `ApiResponse<RecoveryResultDto>` | OPERATIONS_RECOVERY |
| Resolve unknown delivery | POST | `/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` | `{ reason, outcome: SENT\|FAILED }` | `ApiResponse<InvoiceDeliveryResponseDto>` (200; 400 if not eligible) | OPERATIONS_RECOVERY |

#### Operations and diagnostics

| Purpose | Method | Path | Authority |
|---|---|---|---|
| Billing operations summary (run/invoice/receivable counts) | GET | `/api/v1/operations/billing-summary?days=30` (1–366) | `AR_OPERATIONS_REPORT_AUTHORITIES` |
| Reconciliation report | GET | `/api/v1/operations/reconciliation` | same |
| Alert evaluation | GET | `/api/v1/operations/alerts` | same |
| Health (static) | GET | `/health` → `{"status":"UP","service":"account-receivable","timestamp":…}` | public |
| Actuator health / liveness / readiness | GET | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | public (details only when authorized) |
| Actuator info / metrics | GET | `/actuator/info`, `/actuator/metrics` | JWT |

**Scheduler-only / internal (no endpoint):** automatic run creation and processing, approval reconciliation tick, occurrence status scheduler, configuration status scheduler, receivable reconciliation job, operations alert job. **There is no endpoint to list or read automatic billing runs** and none to read or toggle the feature flag.

### 4.4 Key DTO field lists

**`BillingConfigurationRequestDto`** (create / full update)

| Field | Type | Constraint |
|---|---|---|
| clientId | UUID | `@NotNull` |
| projectId | Long | required in practice for PROJECT context |
| billingTypeId | UUID | `@NotNull` |
| billingContext | `PROJECT` \| `PRODUCT_SERVICE` | null → PROJECT |
| productName, productDescription | String | productName required for PRODUCT_SERVICE |
| currency | String | `@NotNull`; used only for PRODUCT_SERVICE (PROJECT uses PMS project currency) |
| paymentTermId | UUID | `@NotNull` |
| billingFrequencyId | UUID | optional in DTO; **required** by T&M periods, Fixed Price and Recurring |
| taxRegionId | UUID | `@NotNull` |
| pricingModel | `STANDARD` \| `ROLE_BASED` | required for "Timesheet Based" |
| invoiceGenerationType | `MANUAL` \| `AUTOMATIC` | `@NotNull` |
| contractValue | BigDecimal | — |
| expenseBillingEligible | Boolean | `@NotNull` (expense billing itself not implemented, BD-03) |
| effectiveFrom | LocalDate | `@NotNull` |
| effectiveTo | LocalDate | ignored for Milestone Plan |
| hourlyRate | BigDecimal | `> 0` required for T&M STANDARD |
| finalize | Boolean | **not read by any service** — do not rely on it |

**`BillingConfigurationDraftRequestDto`**: `@NotNull` clientId, projectId, billingTypeId; optional billingFrequencyId, currencyId, currency, paymentTermId, taxRegionId, pricingModel, invoiceGenerationType, expenseBillingEligible, effectiveFrom, effectiveTo, hourlyRate.

**`BillingConfigurationResponseDto`**: billingConfigurationId; clientId, clientName, countryCode, countryName, email, phone; projectId, projectName, projectCode, primaryLocation, projectStartDate, projectEndDate; billingTypeId, billingTypeName; currencyId, currencyCode, currency; projectBudget, projectBudgetCurrency, contractValue; paymentTermId, paymentTermCode, paymentTermName; billingFrequencyId, billingFrequencyName; taxRegionId, taxRegionName, taxRegionCode; pricingModel; **approvalStatus**; **billingStatus**; **invoiceGenerationType**; expenseBillingEligible; rejectionReason; effectiveFrom, effectiveTo; hourlyRate; billingContext, productName, productDescription; createdAt, updatedAt, versionNo, createdBy, createdDate; fixedPriceDetails, recurringDetails, tmRateCards, milestoneSchedules, paymentStructure, milestonePlanDetails; **changes** `[{field, previousValue, newValue, category}]`, previousApprovalStatus, previousBillingStatus.

**`BillingOccurrenceResponseDto`**: billingScheduleId, billingConfigurationId, recurringConfigurationId, periodNumber, periodStartDate, periodEndDate, **billingDate**, billingAmount, scheduleType, isPartialPeriod, **periodStatus**, taxStatus, isInvoiced, invoiceDate, remarks, isActive, createdAt, updatedAt; taxCalculationId, taxCalculationStatus, taxableAmount, totalTaxAmount, grandTotal, taxCalculatedAt, taxComponents; projectName, clientName, currencyCode, taxRegionName, taxRegionCode, clientCountryName, clientCountryCode, projectStartDate, projectEndDate.

**`InvoiceResponseDto`**: invoiceId, invoiceNumber, **status**, billingSnapshotId, billingScheduleId, billingSnapshotNumber, taxCalculationId, clientId, client/project/seller fields, billingPeriodStart/End, currencyCode, paymentTermCode/Name, invoiceDate, dueDate, items, taxComponents, subtotal, totalTaxAmount, grandTotal, generatedAt, correctionRequired, lastCorrectedAt, **documentAvailable**, **documentUrl**. **No field indicates whether the invoice was created by automation.**

**`InvoiceDeliveryResponseDto`**: deliveryId, invoiceId, invoiceNumber, recipientEmail, deliveryStatus (`PENDING`/`SENT`/`FAILED`/`UNCERTAIN`), sentAt, failedAt, failureReason, createdAt, requestedBy, resolvedAt, resolvedBy, resolutionReason.

**`ReceivableResponseDto`**: receivableId, invoiceId, invoiceNumber, clientId, clientName, projectId, projectName, currencyCode, invoiceDate, dueDate, invoiceAmount, outstandingBalance, status, daysOutstanding, daysOverdue, createdAt, updatedAt.

**`RecoveryRequestDto`**: reason (`@NotBlank`, ≤500). **`RecoveryResultDto`**: recordId, recordType (`AUTOMATIC_BILLING_RUN` / `BILLING_OCCURRENCE` / `RECEIVABLE`), accepted, state, message.

Other DTOs (fixed price, milestone plan, recurring, rate card, snapshot, tax, workspace, master data) are listed field-by-field in the source under `dto/`; their names are given in §4.3.

### 4.5 Examples (fake IDs, no secrets)

**Create configuration (Fixed Price, AUTOMATIC)**

```http
POST {base}/api/billing-configurations
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "clientId": "11111111-1111-1111-1111-111111111111",
  "projectId": 4021,
  "billingTypeId": "22222222-2222-2222-2222-222222222222",
  "billingContext": "PROJECT",
  "currency": "INR",
  "paymentTermId": "33333333-3333-3333-3333-333333333333",
  "billingFrequencyId": "44444444-4444-4444-4444-444444444444",
  "taxRegionId": "55555555-5555-5555-5555-555555555555",
  "invoiceGenerationType": "AUTOMATIC",
  "contractValue": 1200000.00,
  "expenseBillingEligible": false,
  "effectiveFrom": "2026-11-01",
  "effectiveTo": "2027-10-31"
}
```

```json
HTTP/1.1 201
{
  "success": true,
  "message": "Billing configuration created successfully.",
  "data": {
    "billingConfigurationId": "66666666-6666-6666-6666-666666666666",
    "clientId": "11111111-1111-1111-1111-111111111111",
    "clientName": "Acme Industries",
    "projectId": 4021,
    "projectName": "Acme ERP Rollout",
    "billingTypeName": "Fixed Price",
    "currencyCode": "INR",
    "billingFrequencyName": "Monthly",
    "taxRegionCode": "IN-KA",
    "approvalStatus": "DRAFT",
    "billingStatus": "INACTIVE",
    "invoiceGenerationType": "AUTOMATIC",
    "contractValue": 1200000.00,
    "effectiveFrom": "2026-11-01",
    "effectiveTo": "2027-10-31",
    "changes": []
  },
  "timestamp": null
}
```

**Fixed price details**

```http
POST {base}/api/billing-fixed-price/66666666-6666-6666-6666-666666666666/fixed-price
{ "contractValue": 1200000.00, "contractValueSource": "MANUAL",
  "retentionPercentage": 0, "advanceReceived": 0,
  "effectiveFrom": "2026-11-01", "effectiveTo": "2027-10-31" }
```
→ `201 { "success": true, "data": { "fixedPriceConfigurationId": "…", "billableAmount": 1200000.00, "retentionAmount": 0.00, "remainingReceivable": 1200000.00, "isActive": true, … } }`. Retention or advance > 0 is accepted here, but automation will refuse to bill it (§3.2).

**Milestone plan (installments)**

```http
POST {base}/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan
{ "totalContractValue": 900000.00, "paymentStructure": "INSTALLMENTS",
  "entries": [
    { "sequence": 1, "percentage": 40, "billingDate": "2026-12-15" },
    { "sequence": 2, "percentage": 60, "billingDate": "2027-03-15" } ] }
```
→ `201`, `data.entries[].amount` = 360000.00 / 540000.00.

**Submit, lock, approve**

```http
PUT  {base}/api/billing-configurations/{id}/submit                → 200 data.approvalStatus = "PENDING_APPROVAL"
POST {base}/api/record-locks/BILLING_CONFIGURATION/{id}  {"actionType":"APPROVAL"}
     → 200 { "locked": true, "resourceType": "BILLING_CONFIGURATION", "actionType": "APPROVAL",
             "lockedByUserId": 702, "expiresAt": "2026-11-02T10:40:00", "currentUser": true, … }
PUT  {base}/api/billing-configurations/{id}/approve               → 200 data.approvalStatus = "APPROVED", data.billingStatus = "ACTIVE"
DELETE {base}/api/record-locks/BILLING_CONFIGURATION/{id}         → 204
```
Approve without the lock → `409 {"success":false,"message":"Open this record for approval before taking this action.", …}` (or another lock message). Approver = submitter → `403`.

**Read the generated schedule (authoritative)**

```http
GET {base}/api/billing-occurrences?billingConfigurationId=66666666-6666-6666-6666-666666666666
```
```json
[
  { "billingScheduleId": "77777777-7777-7777-7777-777777777777", "periodNumber": 1,
    "periodStartDate": "2026-11-01", "periodEndDate": "2026-11-30", "billingDate": "2026-11-30",
    "billingAmount": 100000.00, "periodStatus": "SCHEDULED", "taxStatus": "PENDING",
    "isInvoiced": false, "isActive": true, "currencyCode": "INR", "projectName": "Acme ERP Rollout" }
]
```
(unwrapped array)

**Schedule preview (indicative only)**

```http
POST {base}/api/billing-configurations/preview-schedule
{ "billingType": "FIXED_PRICE", "billingFrequencyId": "4444…", "effectiveFrom": "2026-11-01",
  "effectiveTo": "2027-10-31", "contractValue": 1200000.00 }
```
→ `200 { "success": true, "data": [ { "periodNumber": 1, "periodStartDate": "2026-11-01", "periodEndDate": "2026-11-30", "billingDate": null, "billingAmount": 98630.14, "isPartialPeriod": false }, … ] }` — amounts are day-prorated and will not match the equal split generated at approval.

**Invoice approval (Finance Manager)**

```http
GET  {base}/api/v1/invoices/pending-approval
POST {base}/api/record-locks/INVOICE/{invoiceId}  {"actionType":"APPROVAL"}
POST {base}/api/v1/invoices/{invoiceId}/approve
```
```json
{ "success": true,
  "message": "Invoice approved successfully. Its document has been generated and it is ready to send.",
  "data": { "invoiceId": "88888888-8888-8888-8888-888888888888", "invoiceNumber": "INV-2026-000123",
            "status": "READY_TO_SEND", "invoiceDate": "2026-11-30", "dueDate": "2026-12-30",
            "subtotal": 100000.00, "totalTaxAmount": 18000.00, "grandTotal": 118000.00,
            "documentAvailable": true, "documentUrl": "/api/v1/invoices/88888888-8888-8888-8888-888888888888/document" },
  "timestamp": null }
```
If PDF generation fails: still `200 success:true`, `data.status = "APPROVED"`, `documentAvailable = false`, message "…its document could not be generated… Generate the document again before sending the invoice." → call `POST /api/v1/invoices/{id}/document`.

**Reject invoice**

```http
POST {base}/api/v1/invoices/{invoiceId}/reject   { "reason": "PO number missing" }
```
→ `200 data.status = "REJECTED"`.

**Send invoice**

```http
POST {base}/api/v1/invoices/{invoiceId}/send
```
```json
{ "success": true, "message": "…",
  "data": { "deliveryId": "99999999-9999-9999-9999-999999999999", "invoiceId": "8888…",
            "invoiceNumber": "INV-2026-000123", "recipientEmail": "ap@acme.example",
            "deliveryStatus": "SENT", "sentAt": "2026-12-01T09:15:02", "requestedBy": "Finance User" } }
```
`FAILED` / `UNCERTAIN` also return HTTP 200 with `success:false`; `UNCERTAIN` must never be retried by the user (send is blocked until an operator resolves it).

**Receivables**

```http
GET {base}/api/v1/receivables/outstanding?clientId=11111111-1111-1111-1111-111111111111
```
→ `200 data: [ { "receivableId": "aaaa…", "invoiceNumber": "INV-2026-000123", "invoiceAmount": 118000.00, "outstandingBalance": 118000.00, "status": "OUTSTANDING", "daysOutstanding": 0, "daysOverdue": 0, … } ]`

**Recovery: re-queue a run**

```http
POST {base}/api/v1/recovery/automatic-billing-runs/{runId}/requeue   { "reason": "TMS outage resolved (INC-4411)" }
```
→ `200 { "success": true, "data": { "recordId": "{runId}", "recordType": "AUTOMATIC_BILLING_RUN", "accepted": true, "state": "FAILED", "message": "Run re-queued; the next scheduler tick processes it…" } }` or `409` with `accepted:false` and the reason.

**Recovery: recalculate stale tax**

```http
POST {base}/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax  { "reason": "Currency changed after re-approval" }
```
→ `200` `state: "TAX_CALCULATED"` or `409` ("already invoiced", "no completed tax calculation", "matches the configuration… nothing to recalculate").

---

## 5. Invoice Preferences contract

| Question | Answer (code) |
|---|---|
| Field for Manual vs Automatic | **`invoiceGenerationType`** on `BillingConfiguration` (column `invoice_generation_type`), in `BillingConfigurationRequestDto`, `BillingConfigurationDraftRequestDto`, both response DTOs |
| Valid values | `MANUAL`, `AUTOMATIC` (`entity_enums/projectbilling_config/InvoiceGenerationType`) |
| Default | **None.** Column nullable, no Java default. |
| Missing / null | Full create/update: 400 "Invoice Generation Type is required." Drafts: accepted as null; submit and approve do **not** check it, so an APPROVED config can have null — it is then treated as manual (all automation filters use `= AUTOMATIC`) [INFERRED] |
| Invalid string | Jackson deserialization error → falls to the catch-all handler → **500** [INFERRED, NOT VERIFIED] |
| Generation Day | **Does not exist** anywhere (Java, SQL, DTOs). |
| `billingTrigger` / trigger selection | **Does not exist.** |
| `GenerationMode` enum (`AUTOMATIC`/`MANUAL`) | Exists but unused (only a commented-out DTO field). `BillingDataAcquisitionResponseDto.generationMode` is a display string — not this setting. Do not use. |
| `TriggerMode` | Written only as `MANUAL` on acquisition records; not a preference. |
| What determines due dates | Fixed Price, Recurring, T&M: `billingFrequencyId` (+ `durationValue/Unit` of the frequency master), `effectiveFrom`, `effectiveTo` (Recurring: term `recurringStartDate/EndDate`; Fixed Price: fixed-price `effectiveFrom/To` if set). Milestone/Installment: each `BillingPaymentEntry.billingDate`. Invoice due date: `paymentTermId` → `paymentDays`. |
| Persisted or computed | `invoiceGenerationType` is persisted on the configuration. Due dates are persisted on occurrences at approval (except T&M, computed each tick). |
| Scheduler: MANUAL vs AUTOMATIC | AUTOMATIC + APPROVED → processed up to invoice approval submission when the flag is on. MANUAL (or null) → never touched by automation; users run tax/invoice via manual endpoints. Switching an approved config to MANUAL requires an edit (→ PENDING_APPROVAL) and re-approval; existing runs then fail permanently ("no longer uses AUTOMATIC"). |
| Can the frontend know the flag is on? | **No.** No endpoint exposes `ar.automatic-billing.enabled`. Only indirect signals: `/api/v1/operations/billing-summary` run counts (restricted) and `/actuator/metrics` (JWT). |

**What the frontend may promise Finance users:** "When automatic generation is enabled for this environment, invoices for due billing periods are prepared and submitted for your approval automatically. Nothing is sent to the client until it is approved and sent by a user."

**What it must not promise:** a specific generation day or time; that the invoice date equals the billing date (it is the processing date); that invoices are sent automatically; that Fixed Price configurations with retention or advance are automated; that T&M periods are billed if timesheets are unapproved (they are retried); that automation is active in a given environment.

**Fields needed per screen**

| Screen | Fields |
|---|---|
| Wizard (Invoice Preferences step) | `invoiceGenerationType` (radio MANUAL/AUTOMATIC), `paymentTermId`, `billingFrequencyId` (not for Milestone Plan), `effectiveFrom`, `effectiveTo`; type step: fixed price / milestone plan / recurring / rate cards |
| Review | everything above + derived: `billingTypeName`, `billingFrequencyName`, `paymentTermName`, `taxRegionName/Code`, `currencyCode`, schedule (preview, labelled indicative) |
| Approval | `BillingConfigurationResponseDto` incl. `invoiceGenerationType`, `changes[]`, `previousApprovalStatus`, `previousBillingStatus`, `fixedPriceDetails.retentionPercentage/advanceReceived` (flag "automation will not bill this" if > 0), type details |
| Edit mode | same as wizard; warn that saving an APPROVED config returns it to PENDING_APPROVAL and pauses automation until re-approval |
| After approval | occurrence list (`GET /api/billing-occurrences?billingConfigurationId=`) as the authoritative schedule |

---

## 6. Status transition matrix

### 6.1 Billing configuration (`approvalStatus`)

| Current | Trigger | Actor | Next | Preconditions | Failure / recovery |
|---|---|---|---|---|---|
| (none) | create / createDraft | Finance Executive | DRAFT (billingStatus INACTIVE) | valid client/project/type/masters | 400/404 |
| DRAFT | submit | Finance Executive | PENDING_APPROVAL | effectiveFrom set, dates valid, no foreign lock | 400, 409 |
| PENDING_APPROVAL | approve | Config approver (BILLING_CONFIGURATION_APPROVAL) | APPROVED | APPROVAL lock, not the submitter, occurrences generate | 400 (all rolled back), 403, 409 |
| PENDING_APPROVAL | reject | Config approver | REJECTED (INACTIVE) | APPROVAL lock, reason | 400, 403, 409; pre-edit snapshot restored |
| APPROVED | full update / saveDraft (APPROVED+ACTIVE) / FP, recurring, milestone, rate-card update | Finance Executive | PENDING_APPROVAL (INACTIVE) | no foreign lock | automation paused until re-approval |
| APPROVED | renew (PRODUCT_SERVICE recurring) | Finance Executive | PENDING_APPROVAL (billingStatus unchanged) | active term, not renewed | 400 |
| REJECTED | full update | Finance Executive | DRAFT | | |
| DRAFT | delete | Finance Executive | (deleted) | no snapshot or run history | 400 |

`billingStatus` (APPROVED configs): ACTIVE / INACTIVE / EXPIRED recalculated every minute by `BillingConfigurationStatusScheduler` (skips `manuallyDeactivated`); `deactivate` (APPROVED+ACTIVE only) → INACTIVE + `manuallyDeactivated = true` (no endpoint re-activates except re-approval, which resets the flag). `expire()` exists in the service but has no endpoint.

### 6.2 Billing occurrence (`billing_schedule.periodStatus`)

| Current | Trigger | Actor | Next | Preconditions | Failure / recovery |
|---|---|---|---|---|---|
| (none) | config approve | System | SCHEDULED (tax PENDING) | type child data present | approval rolls back |
| SCHEDULED | billingDate ≤ today | `BillingOccurrenceStatusScheduler` / approve / orchestrator | TAX_PENDING | active | idempotent, row-locked |
| TAX_PENDING | calculate tax | Orchestrator or user (`POST /calculate-tax`) | TAX_CALCULATED | config approved, billable, tax region + tax configuration | 400; run fails permanently (fix data, re-queue) |
| TAX_CALCULATED | generate invoice | Orchestrator or user (`POST /invoice`) | INVOICED (`isInvoiced=true`) | tax CALCULATED and consistent | 400 |
| TAX_CALCULATED | stale tax recalculation | Operator | TAX_PENDING → TAX_CALCULATED | not invoiced, currency/region changed | 409 |
| SCHEDULED / TAX_PENDING | re-approval reconcile / deactivate | System / user | `isActive=false` | | rows retained, reactivated if period regenerated |

`PENDING` is written only by an uncalled method; `CANCELLED` is never written (only filtered).

### 6.3 Automatic billing run (`automatic_billing_run.status`)

| Current | Trigger | Actor | Next | Preconditions | Failure / recovery |
|---|---|---|---|---|---|
| (none) | discovery | Scheduler | SCHEDULED | config APPROVED + AUTOMATIC, event due | creation rejected → logged, retried next tick |
| SCHEDULED / FAILED (retry due) / PROCESSING (claim expired) | claim | Scheduler worker | PROCESSING | atomic claim | — |
| PROCESSING | step failure | Orchestrator | FAILED (`failureStep`, `failureReason`, `nextRetryAt` or null) | | auto-retry if temporary; else operator requeue |
| PROCESSING | invoice submitted | Orchestrator | PENDING_APPROVAL | last step SUBMIT_FOR_APPROVAL | — |
| PROCESSING | invoice already approved | Orchestrator | COMPLETED | | — |
| PROCESSING (claim expired, attempts exhausted or retry disabled) | recovery | Scheduler | FAILED | | operator requeue |
| FAILED | requeue | Operator (OPERATIONS_RECOVERY) or config re-approval | FAILED with `nextRetryAt = now` → next tick claims | not already re-queued | 409 if ineligible |
| PENDING_APPROVAL | invoice approved | After-commit callback / tick reconciliation / operator | COMPLETED | invoice APPROVED/READY_TO_SEND | rejected invoice: run stays PENDING_APPROVAL |

Steps (`currentStep`, `lastCompletedStep`, `failureStep`): `SOURCE_DATA_ACQUISITION`, `TAX_CALCULATION`, `INVOICE_CREATION`, `SUBMIT_FOR_APPROVAL`.

### 6.4 Billing snapshot (T&M) — `BillingSnapshotStatus`

`IN_PROGRESS`, `READY_FOR_TAX` (created) → `TAX_COMPLETED` (tax) → `INVOICED` (invoice). `CANCELLED` exists; an automatic run refuses a reused CANCELLED snapshot. `IN_TAX` is defined; no transition to it was traced in this audit (NOT VERIFIED).

### 6.5 Tax calculation — `TaxCalculationStatus`

`CALCULATED` or `FAILED`. A `FAILED` record blocks reuse ("cannot be reused"). Superseded calculations (BD-07 recovery) keep `supersededAt/By/Reason` and are detached from the occurrence.

### 6.6 Invoice — `InvoiceStatus`

| Current | Trigger | Actor | Next | Preconditions | Failure / recovery |
|---|---|---|---|---|---|
| (none) | generate | Orchestrator or Finance Executive | GENERATED | tax CALCULATED | 400 |
| GENERATED | submit | Orchestrator or Finance Executive | PENDING_APPROVAL | | |
| PENDING_APPROVAL | approve | Finance Manager (INVOICE_APPROVAL) | APPROVED → READY_TO_SEND (same call) | APPROVAL lock, not the submitter | PDF failure leaves APPROVED → `POST /document` |
| PENDING_APPROVAL | reject | Finance Manager | REJECTED | APPROVAL lock, reason | |
| REJECTED | refresh-after-correction / non-financial correction | Finance Executive | REJECTED | | |
| REJECTED | submit | Finance Executive | PENDING_APPROVAL | correction refreshed if required | 400 |
| APPROVED | generate document | Finance Manager | READY_TO_SEND | consistent data | 400/500 |

`READY_TO_SEND` does **not** mean sent.

### 6.7 Invoice delivery — `InvoiceDeliveryStatus`

`PENDING` (claim) → `SENT` | `FAILED` | `UNCERTAIN`. `FAILED` allows another send. `SENT` blocks further sends (resend not supported, BD-06). `UNCERTAIN` or `PENDING` older than 10 min → operator `resolve` → `SENT` or `FAILED`.

### 6.8 Receivable — `ReceivableStatus`

Created `OUTSTANDING` on SENT. **`PARTIALLY_PAID` and `SETTLED` are never assigned** — payments are not implemented.

---

## 7. Data and UI dependencies

### 7.1 Master data required for any configuration

| Data | Source | Consumed by |
|---|---|---|
| Client | `client` table, synced from CMS via Debezium CDC (`cdc/ClientCdcHandler`) | config create; invoice recipient email fallback |
| Project | `project_master_reference`, synced from PMS via CDC (`cdc/ProjectCdcHandler`) — PMS id, budget, budget currency, start/end dates, `primaryLocation` | currency (PROJECT context), date validation, tax destination jurisdiction, T&M timesheet lookup |
| Billing type | `billing_type_master` (exact names §3.0) | routing |
| Billing frequency | `billing_frequency_master` (`durationValue`, `durationUnit`); reference seed `src/main/resources/sql/billing_frequency_master_seed_reference.sql` (run manually). **"One-Time" is used by code but not seeded** — NOT VERIFIED per environment. | periods |
| Payment term | `payment_terms_master.paymentDays` | invoice due date |
| Tax region | `tax_region_master` (code + name; name must match project `primaryLocation` for jurisdiction resolution) | tax |
| Tax configuration | `tax_configuration` + components effective for (region, period start) | tax |
| Currency | `currency_master` (PRODUCT_SERVICE context) | |
| Company profile | `company_profile` (seller details, invoice content defaults) | invoice/PDF |

### 7.2 Per billing type

| Type | Frontend collects | Backend generates |
|---|---|---|
| T&M ("Timesheet Based") | pricingModel; hourlyRate (STANDARD); rate cards (role, rate, `ratePeriod` HOURLY, effective dates); billingFrequencyId; effectiveFrom/To | periods; snapshots from TMS timesheets; amounts |
| Fixed Price | billingFrequencyId (on the config, **before** creating the fixed-price record); contractValue, contractValueSource, retentionPercentage, advanceReceived, optional effective dates | occurrences and per-period amounts at approval |
| Milestone / Installment | totalContractValue, paymentStructure, entries (sequence, percentage, billingDate) | entry amounts; one occurrence per entry at approval |
| Recurring | contractValueSource, contractValue (MANUAL), recurringStartDate/EndDate, billingFrequencyId, renewal settings | prorated occurrences at approval |

External runtime data: TMS approved, billable timesheets (T&M only); PMS project data via CDC.

### 7.3 Backend-generated (never submitted by the frontend)

Occurrence ids/dates/amounts, approval and billing statuses, `manuallyDeactivated`, run records, snapshot/tax/invoice numbers and amounts, invoice date and due date, document, delivery outcome, receivables, change-tracking `changes[]`, `submittedByUserId` and approval actor fields.

---

## 8. Error handling, idempotency and security

### 8.1 Unique constraints protecting financial data

| Table | Constraint | Purpose |
|---|---|---|
| `automatic_billing_run` | `uk_automatic_billing_run_period (billing_configuration_id, billing_period_start, billing_period_end)`, `uk_automatic_billing_run_schedule (billing_schedule_id)` | one run per billing event |
| `billing_schedule` | `uk_billing_schedule_period (billing_configuration_id, period_start_date, period_end_date)`; `uk_billing_schedule_recurring_period` (nullable column — ineffective for non-recurring rows on MySQL [INFERRED]) | one occurrence per period |
| `invoice` | `uk_invoice_billing_schedule`, `uk_invoice_billing_snapshot` (V6) | one invoice per occurrence / snapshot |
| `tax_calculation` | unique snapshot id, unique schedule id | one tax calculation per source |
| `billing_snapshot` | `uk_billing_snapshot_project_period (project_id, start, end)` | one snapshot per project/period |
| `billing_snapshot_item` | `uk_billing_snapshot_item_billed_source (item_type, billed_source_reference_id)` (V8) | a timesheet is billed once |
| `billing_acquisition` | `uk_billing_acq_config_period` | |
| `invoice_document` | `uk_invoice_document_invoice` | one PDF per invoice |
| `receivable` | `uk_receivable_invoice` | one receivable per invoice |
| `billing_milestone_plan` | unique `billing_configuration_id` | |

Schema management: `spring.jpa.hibernate.ddl-auto=update` by default; Flyway disabled by default (`AR_FLYWAY_ENABLED=false`) and requires a DBA-verified baseline (see `application.properties` comments). `billing_schedule` has no Flyway DDL. Existence of these keys on real MySQL is covered only by a **skipped** Testcontainers test.

### 8.2 Duplicates and retries (frontend view)

- Double-clicking **generate invoice / calculate tax** → second call 400 (`DuplicateResourceException`, message "already…").
- Double-clicking **approve** → second call 400 ("Only invoices pending approval can be approved.").
- Double-clicking **send** → second call 400 ("in progress or its outcome is unknown" / "already been sent").
- Snapshot create for an existing period → 200 with `existingSnapshot = true`.
- Concurrent edits → 409 (optimistic locking or record lock).

### 8.3 Transaction boundaries (summary)

Configuration services: class-level `@Transactional` (approval generates occurrences atomically). Run state: `REQUIRES_NEW` per write. Orchestrator: no transaction; each called service commits. Invoice service: class-level `@Transactional`; within the single approve request, `approveInvoice` and `finalizeInvoiceDocument` run in two separate transactions (the controller itself is not transactional), so the approval commits even if the PDF step fails. Delivery: three separate transactions, SMTP outside any transaction. Receivable: its own transaction.

### 8.4 Failure statuses and recovery operations

| Situation | Visible as | Recovery |
|---|---|---|
| Run failed temporarily | run `FAILED`, `nextRetryAt` set | automatic |
| Run failed permanently (data/validation, G-32, rejected invoice) | run `FAILED`, `nextRetryAt` null | fix data → requeue (OPERATIONS_RECOVERY) or bill manually |
| Run approved but not completed | run `PENDING_APPROVAL` with approved invoice | automatic each tick; or `reconcile-approval` |
| Stale tax after currency/tax-region change | invoice generation refused | `recalculate-tax` |
| PDF generation failed at approval | invoice `APPROVED`, `documentAvailable=false` | `POST /document` |
| Delivery uncertain / stuck pending | delivery `UNCERTAIN` / old `PENDING` | `resolve` after checking mailbox |
| Sent without receivable | operations report finding | automatic every 15 min; or recovery endpoint |

### 8.5 Authentication and authorization dependencies

- JWT issued by UMS. `spring.security.oauth2.resourceserver.jwt.issuer-uri=${JWT_ISSUER_URI}` (required). Decoder resolves issuer metadata lazily on first request. Validation: signature, `iss`, `exp`/`nbf`, non-null `user_id` claim; `aud` only if `AR_JWT_AUDIENCES` is set (empty by default — BD-09).
- Claims read: `user_id` (Long), `email`, `name`, `roles` (→ `ROLE_<role>`), `permissions`.
- No UMS HTTP client exists; UMS is only the token issuer.
- UMS role/permission names for the four financial actions are **not defined anywhere in this repository**.
- TMS: user `Authorization` header forwarded during user requests; `TMS_SERVICE_TOKEN` used on scheduler threads.

### 8.6 CORS and base URL

`config/CorsConfig`, property `ar.cors.allowed-origins`: default `http://localhost:5173,http://localhost:3000`; `local` profile default `http://localhost:5173`; env `AR_CORS_ALLOWED_ORIGINS` overrides. Wildcards or scheme-less entries fail startup; empty list = no CORS. Methods GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD; all request headers; exposed `Authorization`, `Content-Disposition`, `X-Total-Count`; credentials allowed. k8s value comes from `ar-secrets` (NOT VERIFIED).

### 8.7 Sensitive fields — never log in the frontend

Bearer tokens/JWTs; client and seller emails, phone numbers, addresses, GSTIN/tax ids; invoice amounts and line items tied to client names; bank/payment instructions (`paymentInstructions`); recipient email on deliveries; `failureReason` text should be shown, not shipped to third-party telemetry. The backend sanitizes run failure reasons (`AutomaticBillingRunServiceImpl.sanitizeFailureReason`) but not every message.

### 8.8 Errors: display vs. escalate

| Display to user (actionable) | Requires operator / admin |
|---|---|
| 400 validation messages (dates, amounts, percentages, "already exists", wrong status) | 403 on approve/send/recover when the user believes they should have access (authority config) |
| 403 maker-checker ("cannot approve own submission") | 500 responses |
| 409 lock messages (prompt to open for approval / refresh) | Delivery `UNCERTAIN` |
| Delivery `FAILED` with reason (may resend) | Run `FAILED` without retry; "Billing Configuration … has unsupported or unknown billing type" |
| Approve succeeded but PDF failed (offer "Generate document") | "Tax configuration not available for region…" (master data) |
| `success:false` snapshot messages (unapproved timesheets, no timesheets) | TMS authentication failures |

---

## 9. Verified test coverage and limitations

### 9.1 What was run for this audit

**Nothing.** No tests were executed. Results below are from `target/surefire-reports` (previous run, timestamps 2026-10-21 18:51–18:53 +0530, with two security classes re-run at 19:33–19:36).

| Metric | Value |
|---|---|
| Tests run | 923 |
| Failures | **1** — `BillingOccurrenceServiceImplTest.effectiveFromChange_WithInvoiced_ThrowsException` (deliberately red pending Finance decision BD-05) |
| Errors | 0 |
| Skipped | **3** — all of `integration_mysql.MySqlIntegrationTest` (Docker unavailable) |
| Staleness | The full run predates commits `eb17b10` and `2eeeb4a` (CORS / `LocalContextPathFilter`); the full suite has **not** been recorded against HEAD. |

### 9.2 Test infrastructure

- No surefire/failsafe configuration, no tags, no `@Disabled`. `src/test/resources` does not exist; tests configure properties inline.
- **Database:** `@SpringBootTest` and `@DataJpaTest` use **H2 in MySQL mode** (`create-drop`). The only real-MySQL test (`MySqlIntegrationTest`: unique keys, V12–V15 migrations, atomic claim) has **never been recorded as passing**.
- **TMS:** `@MockBean TimesheetIntegration` in full-context tests; `TimesheetIntegrationImplTest` uses a local JDK `HttpServer`. No real TMS.
- **UMS/JWT:** `@MockBean JwtDecoder` / `CurrentUserService`; static validator unit tests. No real UMS.
- **SMTP:** `@MockBean EmailService`; `EmailServiceImplTest` mocks `JavaMailSender`. No real or embedded SMTP.
- **CDC:** disabled in all Spring tests.

### 9.3 Coverage by area

| Area | Coverage | Key tests |
|---|---|---|
| Scheduler | strong (Mockito + H2) | `AutomaticBillingSchedulerTest` (21), `AutomaticBillingSchedulerPersistenceTest` (15), `AutomaticBillingRecoveryPersistenceTest` (20) |
| Orchestrator | strong (H2, collaborators mocked) | `AutomaticBillingOrchestratorTest` (56) |
| Run service / claim | strong | `AutomaticBillingRunServiceImplTest` (29), `AutomaticBillingRunPersistenceTest` (15) |
| Retry policy | strong | `AutomaticBillingRetryPolicyTest` (9) |
| End-to-end (H2, mocks for TMS/SMTP/user) | 10 scenarios | `AutomaticBillingEndToEndTest` — **configurations are inserted directly as APPROVED**; draft → submit → approve is not exercised end to end |
| Occurrence billability / reconciliation | strong | FIR suite (48), billability tests, `BillingOccurrenceServiceImplTest` (59, 1 red) |
| Tax, invoice, delivery, receivable services | strong | `TaxCalculationServiceImplTest`, `InvoiceServiceImplTest` (127), `InvoiceDeliveryServiceImplTest` (18), persistence tests |
| Security | unit + some MockMvc | `FinancialActionAuthorizerTest`, `JwtTokenValidationTest`, `SecurityConfigurationTest`, `LocalCorsAndContextPathTest` |

### 9.4 Code paths with no meaningful tests

- `BillingConfigurationServiceImpl`: `create`, `createDraft`, `saveDraft`, `updateBillingConfiguration`, `submitForApproval`, `deactivateBillingConfiguration`, plain `reject`, `getAvailableProjectsForNewConfiguration`, `previewSchedule`.
- `BillingFixedPriceServiceImpl`, `BillingMilestonePlanServiceImpl`, `BillingTMRateCardServiceImpl` (CRUD); `RecurringBillingServiceImpl.update/delete/getBillingSchedule`.
- `BillingOccurrenceStatusScheduler`, `BillingConfigurationStatusScheduler` (every-minute jobs), `ReceivableReconciliationJob`.
- `LocalContextPathFilter` (despite the name of `LocalCorsAndContextPathTest`, no test covers it); `local`-profile CORS.
- Recovery/delivery/receivable controllers at HTTP level (only one 403 check); `AutomaticBillingRunRecoveryService.getEvents`.
- `BillingPeriodCalculatorServiceImpl` beyond `calculatePeriods` (preview path).
- Any test against real MySQL, TMS, UMS or SMTP.

### 9.5 Settings required before local automatic processing works

1. `SPRING_PROFILES_ACTIVE=local` (optional, logging/CORS) and the DB (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, MySQL with `sslMode=REQUIRED`), `JWT_ISSUER_URI`, `TMS_API_BASE_URL`, `MAIL_USERNAME`/`MAIL_PASSWORD`, PMS/CMS CDC connection settings (startup fails without required external config — `OperationalReadinessTest.missingRequiredExternalConfiguration_failsStartupInsteadOfRunningMisconfigured`).
2. `AR_AUTOMATIC_BILLING_ENABLED=true` (optionally a shorter `AR_AUTOMATIC_BILLING_CRON`).
3. `TMS_SERVICE_TOKEN` for T&M (scheduler threads have no user token).
4. `AR_AUTH_CONFIGURATION_APPROVAL`, `AR_AUTH_INVOICE_APPROVAL`, `AR_AUTH_INVOICE_DELIVERY`, `AR_AUTH_OPERATIONS_RECOVERY` (and `AR_OPERATIONS_REPORT_AUTHORITIES` for reports) set to authorities present in the test users' JWTs; two different users for maker-checker.
5. Master data: billing types with the exact names in §3.0, frequencies (incl. "One-Time" if used), payment terms, tax regions whose names match project `primaryLocation`, effective tax configurations, an active company profile, client email addresses.
6. Project and client rows synced by CDC.
7. A configuration with `invoiceGenerationType = AUTOMATIC`, type details, submitted and approved by a different user, with a billing date ≤ today (backfill is unlimited by default).
8. Be aware the default time zone is the JVM's (BD-10).

---

## 10. Frontend Integration Contract

### 10.1 Fields to collect and submit

- **Configuration** (`POST /api/billing-configurations`, `PUT /{id}`, or draft endpoints): `clientId`, `projectId`, `billingTypeId`, `billingContext`, `productName`/`productDescription` (PRODUCT_SERVICE), `currency`, `paymentTermId`, `billingFrequencyId` (all types except Milestone Plan), `taxRegionId`, `pricingModel` + `hourlyRate` (T&M), **`invoiceGenerationType` (`MANUAL` | `AUTOMATIC`, always send explicitly — also on drafts)**, `contractValue`, `expenseBillingEligible`, `effectiveFrom`, `effectiveTo`.
- **Fixed Price**: `contractValue`, `contractValueSource`, `retentionPercentage`, `advanceReceived`, optional `effectiveFrom/To`, `remarks`.
- **Milestone / Installment**: `totalContractValue`, `paymentStructure` (`FULL_PAYMENT` | `INSTALLMENTS`), `entries[{sequence, percentage, billingDate, remarks}]`.
- **Recurring**: `contractValueSource`, `contractValue`, `recurringStartDate`, `recurringEndDate`, `billingFrequencyId`, renewal fields (stored only), `recurringName`, `remarks`.
- **T&M rate cards**: `roleName`, `rate`, `ratePeriod` (use `HOURLY`), `effectiveFrom/To`.
- **Reasons**: `rejectionReason` (config), `reason` (invoice reject, recovery).

### 10.2 Fields to display on review and approval pages

`billingTypeName`, `clientName`, `projectName`, `currencyCode`, `paymentTermName`, `billingFrequencyName`, `taxRegionName/Code`, `effectiveFrom/To`, `contractValue`, **`invoiceGenerationType`**, `approvalStatus`, `billingStatus`, type details (`fixedPriceDetails`, `milestonePlanDetails`, `recurringDetails`, `tmRateCards`), `changes[]` with `previousApprovalStatus`/`previousBillingStatus`, `rejectionReason`. Warning if `invoiceGenerationType = AUTOMATIC` and Fixed Price retention or advance > 0. Invoice approval: `InvoiceApprovalSummaryResponseDto` / `InvoiceResponseDto` (lines, tax components, totals, invoice/due dates, `documentAvailable`).

### 10.3 Authoritative billing schedule source

`GET /api/billing-occurrences?billingConfigurationId={id}` **after approval** (Fixed Price, Milestone/Installment, Recurring). Before approval, `POST /api/billing-configurations/preview-schedule` is **indicative only** (Fixed Price and Recurring; amounts/dates may differ). T&M has no persisted schedule; show frequency + effective dates and the resulting snapshots/invoices.

### 10.4 Endpoints and payloads

As in §4.3–§4.5. Base URL is configured once (`http://localhost:8080` locally, `https://<host>/paves/ar` in k8s). Approve/reject require an APPROVAL record lock first. Handle unwrapped responses and the snapshot endpoint's `200 success:false`.

### 10.5 Statuses the UI must support

- Configuration `approvalStatus`: DRAFT, PENDING_APPROVAL, APPROVED, REJECTED; `billingStatus`: INACTIVE, ACTIVE, EXPIRED.
- Occurrence `periodStatus`: SCHEDULED, TAX_PENDING, TAX_CALCULATED, INVOICED (treat PENDING/CANCELLED defensively).
- Invoice: GENERATED, PENDING_APPROVAL, APPROVED, REJECTED, READY_TO_SEND; workspace adds READY_FOR_INVOICE.
- Delivery: PENDING, SENT, FAILED, UNCERTAIN.
- Receivable: OUTSTANDING (PARTIALLY_PAID / SETTLED defined but never set).
- Snapshot: IN_PROGRESS, READY_FOR_TAX, IN_TAX, TAX_COMPLETED, INVOICED, CANCELLED.
- Automatic run statuses (operations/recovery screens only): SCHEDULED, PROCESSING, FAILED, PENDING_APPROVAL, COMPLETED.

### 10.6 Automated vs. manual

| Automated (system) | Manual (user) |
|---|---|
| Occurrence generation at approval; SCHEDULED → TAX_PENDING; billingStatus updates | Create/edit/submit configuration; approve/reject (config approver) |
| *If flag on and config AUTOMATIC:* T&M snapshot, tax, invoice draft, submit for approval; retries; approval reconciliation | *Otherwise:* snapshot / tax / invoice / submit through manual endpoints |
| PDF finalization (as part of the approve call) | Invoice approve/reject (Finance Manager); document regeneration |
| Receivable creation after SENT; 15-min receivable reconciliation | Send invoice to client |
| | All recovery actions (operator) |

### 10.7 Configuration prerequisites and feature flags

`AR_AUTOMATIC_BILLING_ENABLED` (default false; not visible to the UI), `TMS_SERVICE_TOKEN`, `AR_AUTH_*` authorities (empty = nobody), `AR_OPERATIONS_REPORT_AUTHORITIES`, `AR_CORS_ALLOWED_ORIGINS`, `SERVER_SERVLET_CONTEXT_PATH`, `JWT_ISSUER_URI`, `AR_JWT_AUDIENCES`, master data per §7.1. The UI must not infer the flag; if it needs to, a backend endpoint must be added.

### 10.8 Unsupported fields — do not show or submit

- Generation Day, billing trigger, invoice generation time, auto-send — **not in the backend**.
- `GenerationMode`, `TriggerMode` — not preferences.
- `finalize` on the configuration request — ignored.
- `RenewalType = AUTO` as a promise of automatic renewal — stored only, nothing renews automatically.
- "Installment" as a billing type — use Milestone Plan + `INSTALLMENTS`.
- "Manual" billing type — no behaviour; use `invoiceGenerationType = MANUAL` instead.
- Expense billing (`expenseBillingEligible`) as functional — expenses are always 0 (BD-03).
- Receivable payment/settlement actions — no endpoints.
- Invoice resend after SENT — not supported (BD-06).
- An "automatically generated" badge on invoices or a per-configuration automation status — no backing data in any frontend-accessible API.

### 10.9 Outstanding blockers

**Backend**
1. No API exposes automation state per configuration/occurrence/invoice or the feature flag.
2. Schedule preview diverges from generated occurrences (Fixed Price amounts; month-end dates).
3. Approval succeeds with zero occurrences when type details are missing.
4. `invoiceGenerationType` can be null on approved configurations (drafts not validated at submit).
5. Framework errors (bad enum/UUID/JSON) return 500, not 400.
6. Billing type matching is inconsistent across layers (§3.0).
7. Failing BD-05 test; full suite not re-run on HEAD; MySQL tests never run; configuration lifecycle and status schedulers untested.
8. `LocalContextPathFilter` active in all profiles and untested with Spring Security forward dispatch.
9. Status schedulers hard-coded to every minute (commented-out daily cron).
10. Invoice date is the processing date, not the billing date (backfill implication).
11. `manuallyDeactivated` not re-checked by the orchestrator for already-created runs.

**External integration**
1. UMS role/permission names and JWT audience (BD-09) — required for any approval/send/recovery.
2. TMS service token for scheduled T&M acquisition.
3. SMTP credentials (Gmail) per environment.
4. CDC connectivity for client/project data.
5. Production values in `ar-secrets` (CORS, authorities, flag) are not visible in the repo.

**Business decisions** (`BUSINESS_DECISIONS.md`, all open as of 2026-10-21; status confirmed against code where applicable)
- BD-01/BD-02 retention and advance on Fixed Price (automation refuses them).
- BD-03 expense billing (absent).
- BD-04 rounding policy (equal split HALF_EVEN for Fixed Price, HALF_UP per T&M line, day-proration for Recurring — unconfirmed).
- BD-05 moving Effective From past an invoiced occurrence (failing test).
- BD-06 resending a sent invoice.
- BD-07 tax correction after currency/tax-region change (operator recalculation exists).
- BD-08 alert thresholds and routing.
- BD-09 UMS authorities and audience.
- BD-10 backfill age and time zone.
