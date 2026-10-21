# Automatic Billing — Operations Runbook

Audience: AR operators, on-call engineers and release managers.
Scope: the automatic billing pipeline (scheduler → run → snapshot/occurrence → tax → invoice → `PENDING_APPROVAL`)
and the manual steps that follow it (approval → document → `READY_TO_SEND` → send → receivable).

All paths below are relative to the service base `https://<host>/paves/ar` (context path `/paves/ar`).
All `/api/**` and `/actuator/metrics` calls need a UMS JWT (`Authorization: Bearer …`).

---

## 1. What automation does — and never does

| Automation does | Automation never does |
|---|---|
| Finds due billing events (occurrences; T&M periods) of **APPROVED + AUTOMATIC** configurations | Approve or reject invoices (Finance Manager, manual) |
| Creates one `automatic_billing_run` per billing event | Generate the client document before approval |
| Acquires source data, calculates tax, creates the invoice | Send invoices to clients (manual `POST …/send`) |
| Submits the invoice → `PENDING_APPROVAL` | Create receivables (only after a successful send) |
| Retries temporary failures (bounded), recovers interrupted runs, backfills missed events | Reset runs or delete financial records |

---

## 2. Configuration

Secrets come from the Kubernetes secret `ar-secrets` (`envFrom` in `k8s/backend/ar/deployment.yaml`), populated by the
shared Jenkins library (`envSecret: 'dev-ar'` in `Jenkinsfile`). Never commit them; `.env` is git-ignored and local only.
A missing required variable stops the application at startup (unresolvable placeholder) — it never runs half-configured.

### Required (no default — startup fails without them)

| Variable | Used for |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | AR database |
| `RMS_DB_*`, `PMS_DB_*` | Client / Project CDC sources |
| `TMS_API_BASE_URL` | TMS (must include the `/tms` prefix; the client appends `/api/timesheets/billing`) |
| `JWT_ISSUER_URI` | UMS JWT validation |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Invoice delivery (Gmail SMTP) |

### Automatic billing (all optional, safe defaults)

| Variable → property | Default | Meaning |
|---|---|---|
| `AR_AUTOMATIC_BILLING_ENABLED` → `ar.automatic-billing.enabled` | **`false`** | Master switch. Off = the scheduler touches nothing. |
| `AR_AUTOMATIC_BILLING_CRON` → `ar.automatic-billing.cron` | `0 */5 * * * *` | Tick schedule (6-field Spring cron, validated at startup) |
| `AR_AUTOMATIC_BILLING_BATCH_SIZE` → `…batch-size` | `50` (1–500) | Max billing events per tick (new + resumed + retried) |
| `AR_AUTOMATIC_BILLING_CLAIM_DURATION` → `…claim-duration` | `PT15M` | Lease of a worker on a run; renewed before every step |
| `AR_AUTOMATIC_BILLING_RETRY_ENABLED` → `…retry.enabled` | `true` | Retry temporary failures / recover interrupted runs |
| `AR_AUTOMATIC_BILLING_RETRY_MAX_ATTEMPTS` → `…retry.max-attempts` | `3` (1–20) | Attempts in total |
| `AR_AUTOMATIC_BILLING_RETRY_INITIAL_DELAY` / `_MULTIPLIER` / `_MAX_DELAY` | `PT15M` / `2.0` / `PT6H` | Backoff: 15 min, 30 min, 60 min … capped |
| `AR_AUTOMATIC_BILLING_BACKFILL_ENABLED` → `…backfill.enabled` | `true` | Pick up billing events whose date passed unprocessed |
| `AR_AUTOMATIC_BILLING_BACKFILL_MAX_AGE_DAYS` → `…backfill.max-age-days` | `0` (no limit) | Older events are left to the manual flow |

### Other operational settings

| Variable → property | Default | Meaning |
|---|---|---|
| `TMS_API_CONNECT_TIMEOUT` / `TMS_API_READ_TIMEOUT` | `PT10S` / `PT60S` | TMS call bounds (timeouts are retried) |
| `AR_LOG_LEVEL` → `logging.level.com.AccountReceivableManagement` | `INFO` | Application log level |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | See §7 |
| `SPRING_PROFILES_ACTIVE=local` | – | **Local only**: SQL + bind values in logs. Never in shared environments. |
| `client-cdc.enabled` / `project-cdc.enabled` (`CLIENT_CDC_ENABLED` / `PROJECT_CDC_ENABLED`) | `true` | CDC connectors (can now be disabled) |

**Time zone (deployment decision):** "due today" uses the JVM default zone. Containers default to UTC, so India-dated
billing becomes due at 05:30 IST. Set `TZ=Asia/Kolkata` (or `-Duser.timezone=Asia/Kolkata`) in the deployment if
billing dates are Indian business dates.

---

## 3. Enabling, pausing and disabling automatic billing

The switch is read at startup. Changing it = update the secret + restart; manual billing keeps working throughout
(rolling update, `maxUnavailable: 0`).

```bash
# Pause / disable (in-flight runs stop at their next step boundary; nothing is lost - see §6)
kubectl -n backend patch secret ar-secrets --type merge -p '{"stringData":{"AR_AUTOMATIC_BILLING_ENABLED":"false"}}'
kubectl -n backend rollout restart deployment/ar-deployment
kubectl -n backend rollout status deployment/ar-deployment

# Enable (only after the checklist in §10)
kubectl -n backend patch secret ar-secrets --type merge -p '{"stringData":{"AR_AUTOMATIC_BILLING_ENABLED":"true"}}'
kubectl -n backend rollout restart deployment/ar-deployment
```

If `ar-secrets` is (re)generated by the shared Jenkins library, change the value at its source too, or the next
pipeline run restores the old value. ArgoCD (`k8s/argocd/apps/ar-app.yaml`, `selfHeal: true`) syncs manifests from
the `intranet-devops` repository — manifest changes must be made there, not with `kubectl edit`.

Startup log confirms the state:
`[AutomaticBillingScheduler] enabled - cron '…', batch size …` or `… disabled (ar.automatic-billing.enabled=false)`.

---

## 4. Health and monitoring

### Probes (no JWT, status only)
| Path | Checks | Use |
|---|---|---|
| `GET /paves/ar/actuator/health/liveness` | application only | `livenessProbe` (restart) |
| `GET /paves/ar/actuator/health/readiness` | application + AR database | `readinessProbe` (traffic) |
| `GET /paves/ar/actuator/health` | overall status; details only with a JWT | dashboards |
| `GET /paves/ar/health` | static "UP" (legacy) | load balancers |

TMS, UMS and SMTP are deliberately **not** probe dependencies: their outages must not restart or drain AR.
Exposed actuator endpoints: `health`, `info`, `metrics` only.

### Metrics — `GET /paves/ar/actuator/metrics/<name>` (JWT)
| Metric | Kind | Watch for |
|---|---|---|
| `ar.automatic_billing.runs.needs_intervention` | gauge | > 0 — FAILED, no automatic retry left (§5) |
| `ar.automatic_billing.runs.stuck` | gauge | > 0 for longer than one tick — scheduler not recovering |
| `ar.automatic_billing.runs.retry_scheduled` | gauge | growing — a dependency is failing |
| `ar.automatic_billing.occurrences.due_without_run` | gauge | stays > 0 across ticks — backlog / scheduler disabled or failing |
| `ar.automatic_billing.runs.pending_approval` | gauge | approval backlog (business) |
| `ar.automatic_billing.tick` | timer | duration approaching the cron interval |
| `ar.automatic_billing.runs.processed` (`outcome`) | counter | `FAILED` / `ERROR` share |
| `ar.automatic_billing.failures` (`step`, `retryable`) | counter | failures by step: `SOURCE_DATA_ACQUISITION` (TMS/data), `TAX_CALCULATION`, `INVOICE_CREATION`, `SUBMIT_FOR_APPROVAL` |
| `ar.tms.request.failures` (`status`) | counter | `401`/`403` = credentials; `5xx`/`io` = TMS outage |
| `ar.invoice.document.failures` | counter | approved invoices left `APPROVED` (no document) |
| `ar.invoice.delivery.failures` | counter | client emails not sent |
| `ar.invoice.delivery.outcomes` (`outcome`) | counter | `UNCERTAIN` / `UNRECORDED` — resolve (§6.1) |
| `ar.invoice.deliveries.unknown_outcome` | gauge | > 0 — deliveries blocking their invoice until resolved (§6.1) |
| `ar.receivables.sent_without_receivable` | gauge | > 0 after the next reconciliation (15 min) — investigate (§6.1) |
| `ar.receivable.reconciliation` (`outcome`) | counter | `failed` — a sent invoice's receivable cannot be created |
| `ar.automatic_billing.runs.approved_not_completed` | gauge | > 0 across ticks — approval reconciliation failing |

No metrics backend or alerting platform is configured in this repository. **Alert thresholds are a deployment
decision** (no SLA is documented); the conditions in the table are the recommended triggers. A Prometheus scraper
can be added later by adding `micrometer-registry-prometheus` and exposing `prometheus` — no code changes.

**Operations reports and threshold alerts (Phase 15):** `GET /api/v1/operations/billing-summary`,
`/reconciliation`, `/alerts` (read-only; need an authority from `AR_OPERATIONS_REPORT_AUTHORITIES`), and the
optional scheduled evaluation logging `[OperationsAlert]` — see `OPERATIONS_MONITORING_AND_RECONCILIATION.md`.

### Logs
Every line logged while a run is processed carries `[run=<runId>]`. Each processed run ends with one line:

```
[AutomaticBillingRunOutcome] run=… configuration=… occurrence=…|- type=… period=YYYY-MM-DD..YYYY-MM-DD
  status=… lastCompletedStep=… failureStep=… attempt=… invoice=… nextRetryAt=… reason=<sanitized>
```
Failure reasons are sanitized (tokens, passwords, credentials in URLs redacted) in logs and in the database.
SQL and bind values are never logged outside the `local` profile. Routine logs carry identifiers, not amounts,
client contact details or TMS response bodies (G-31); email failures name the recipient domain only. Recovery
actions log `[AutomaticBillingRunRequeued]`, `[AutomaticBillingRunReconciled]`, `[InvoiceDeliveryResolved]`,
`[ReceivableRecovered]`/`[ReceivableReconciled]` with the actor. The CDC configuration still prints its database
usernames (not passwords) at startup.

**Pausing automatic billing safely:** `AR_AUTOMATIC_BILLING_ENABLED=false` (§3) stops every automatic step,
including approval reconciliation (use §6.1 meanwhile). The receivable reconciliation job is independent of it
(`AR_RECEIVABLE_RECONCILIATION_ENABLED`). Manual Finance actions are unaffected. **Scheduler health:** the
`ar.automatic_billing.tick` timer's count increases every cron interval (5 min by default); a flat count means
the scheduler is not running.

---

## 5. Investigating runs

There is no per-run API; run counts by status/step/type are in `GET /api/v1/operations/billing-summary`
and cross-record discrepancies in `GET /api/v1/operations/reconciliation`. For individual runs use read-only SQL
(MySQL 8 — ids are `BINARY(16)`, read them with `BIN_TO_UUID`).

```sql
-- Runs needing attention, newest first
SELECT BIN_TO_UUID(automatic_billing_run_id) run_id, status, current_step, last_completed_step,
       failure_step, attempt_count, next_retry_at, failure_reason, failed_at,
       BIN_TO_UUID(billing_configuration_id) configuration_id, BIN_TO_UUID(billing_schedule_id) occurrence_id,
       billing_period_start, billing_period_end, BIN_TO_UUID(invoice_id) invoice_id
FROM automatic_billing_run
WHERE status IN ('FAILED','PROCESSING')
ORDER BY updated_at DESC;
```

**Retryable vs business-data error**
- `status = 'FAILED' AND next_retry_at IS NOT NULL` → temporary failure; it **will** be retried automatically. Do nothing.
- `status = 'FAILED' AND next_retry_at IS NULL` → needs a person: either a business/data error (validation message
  in `failure_reason`, e.g. missing tax configuration, rate, currency, schedule over-allocated, timesheet not approved)
  or `attempt_count` reached `max-attempts`.
- `status = 'PROCESSING' AND claim_expires_at < NOW()` → interrupted (crash/restart); the next tick recovers it.
- `status = 'PROCESSING' AND claim_expires_at >= NOW()` → a worker is on it. **Do not touch.**

**Occurrences awaiting processing**
```sql
SELECT BIN_TO_UUID(s.billing_schedule_id) occurrence_id, s.billing_date, s.period_status, s.billing_amount
FROM billing_schedule s JOIN billing_configuration c ON c.billing_configuration_id = s.billing_configuration_id
WHERE c.approval_status = 'APPROVED' AND c.invoice_generation_type = 'AUTOMATIC' AND c.manually_deactivated = 0
  AND s.is_active = 1 AND s.is_invoiced = 0 AND s.billing_date <= CURDATE()
  AND s.period_status IN ('SCHEDULED','TAX_PENDING','TAX_CALCULATED')
  AND NOT EXISTS (SELECT 1 FROM automatic_billing_run r WHERE r.billing_schedule_id = s.billing_schedule_id)
ORDER BY s.billing_date;
```

**Does an invoice / tax calculation already exist for a billing event?** (unique per occurrence and per snapshot)
```sql
SELECT BIN_TO_UUID(invoice_id), invoice_number, status FROM invoice
 WHERE billing_schedule_id = UUID_TO_BIN('<occurrence-id>') OR billing_snapshot_id = UUID_TO_BIN('<snapshot-id>');
SELECT BIN_TO_UUID(tax_calculation_id), status, taxable_amount, total_tax_amount, grand_total FROM tax_calculation
 WHERE billing_schedule_id = UUID_TO_BIN('<occurrence-id>') OR billing_snapshot_id = UUID_TO_BIN('<snapshot-id>');
```
Or via API: `GET /api/billing-occurrences/{occurrenceId}/tax-calculation`, `GET /api/v1/billing-snapshots/{snapshotId}/tax-calculation`,
`GET /api/v1/billing-snapshots/{snapshotId}/invoice`, `GET /api/v1/invoices/{invoiceId}`.

**Tax calculation failures** (`failure_step = 'TAX_CALCULATION'`): check, for the configuration's tax region, an active
tax configuration effective on the period start (`GET /api/v1/tax-rate-configurations/tax-region/{taxRegionId}`), its
active components, and that the project's primary location maps to a tax region (`GET /api/tax-region`).

**Approved invoice reached `READY_TO_SEND`?** `GET /api/v1/invoices/{invoiceId}` → `status = READY_TO_SEND`,
`documentAvailable = true`. Status `APPROVED` with `documentAvailable = false` means document generation failed
(the approve response said so): fix the data and call `POST /api/v1/invoices/{invoiceId}/document`. Download:
`GET /api/v1/invoices/{invoiceId}/document`.

**Delivery created exactly one receivable?** `GET /api/v1/invoices/{invoiceId}/deliveries` (latest first) and
```sql
SELECT COUNT(*) FROM receivable WHERE invoice_id = UUID_TO_BIN('<invoice-id>');   -- 0 before a SENT delivery, never > 1
```
A SENT delivery without a receivable (logged `[ReceivableCreationFailed]`) is created automatically by the receivable
reconciliation job (every 15 min, `ar.receivable-reconciliation.*`, gauge `ar.receivables.sent_without_receivable`),
or at once with `POST /api/v1/recovery/invoices/{invoiceId}/receivable` (§6.1). **Never resend** the invoice to
create a receivable — a second ordinary send is refused anyway.

**Delivery outcome unknown?** `GET /api/v1/invoices/{invoiceId}/deliveries`: `UNCERTAIN` (SMTP timed out after the
message was handed over) or `PENDING` older than `AR_INVOICE_DELIVERY_PENDING_TIMEOUT` (default 10 min; the process
stopped, or the outcome could not be recorded) — the email may or may not have reached the client. The invoice
cannot be sent again until it is resolved (§6.1).

---

## 6. Incident procedures

**Pause** — disable (§3). In-flight runs keep their completed steps; nothing is rolled back or duplicated.

**Find in-progress work** — the `PROCESSING` query in §5. A run with a live claim belongs to a running pod.

**Resume after the cause is fixed** — enable (§3). The scheduler then, in order: resumes `SCHEDULED` runs, recovers
interrupted runs (expired claim), retries due `FAILED` runs, then picks up new and missed events. Every step is
find-or-create on unique keys (run per event; snapshot per project+period; one claim per TMS timesheet; tax and
invoice per occurrence/snapshot; receivable per invoice), so resuming never duplicates financial records.

**A `FAILED` run without retry after the data was corrected** — re-queue it through the recovery API (§6.1), after
confirming the cause is fixed. A permanent failure (validation, missing tax configuration, data) fails again until
the data is corrected. It resumes from `last_completed_step`. Never delete runs, snapshots, tax calculations,
invoices or receivables, and never edit run rows with SQL — the API keeps the failure history and the actor.

### 6.1 Recovery API (Batch 4)

All require a JWT holding one of `AR_AUTH_OPERATIONS_RECOVERY` (empty = nobody). An ineligible action changes
nothing and answers `409` with the reason; every action is attributed to the authenticated user.

| Situation | Call | Effect |
|---|---|---|
| `FAILED` run (or `PROCESSING` with an expired claim) to retry | `POST /paves/ar/api/v1/recovery/automatic-billing-runs/{runId}/requeue` body `{"reason":"…"}` | due now; processed by the next tick even when automatic retry is disabled; failure kept in `…/events` |
| Run history (requeues, reconciliations, the failures they replaced) | `GET …/automatic-billing-runs/{runId}/events` | read only |
| `PENDING_APPROVAL` run whose invoice is already `APPROVED`/`READY_TO_SEND` | `POST …/automatic-billing-runs/{runId}/reconcile-approval` | `COMPLETED`; also done by every scheduler tick (gauge `ar.automatic_billing.runs.approved_not_completed`) |
| Sent invoice without receivable | `POST /paves/ar/api/v1/recovery/invoices/{invoiceId}/receivable` body `{"reason":"…"}` | creates the one receivable (refused if not sent/approved, or it exists) |
| Delivery `UNCERTAIN` / stale `PENDING` | check the sending mailbox (Sent folder of `MAIL_USERNAME`) or ask the client, then `POST /paves/ar/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` body `{"outcome":"SENT"|"FAILED","reason":"…"}` | `SENT`: receivable created, no email; `FAILED`: the invoice may be sent again by Finance |

A run waiting on a **rejected** invoice stays `PENDING_APPROVAL` — the invoice goes through correction and
re-approval; nothing in recovery approves or sends anything.

**Automatic retry disabled** (`AR_AUTOMATIC_BILLING_RETRY_ENABLED=false`): nothing is attempted again on its own.
An interrupted run is closed as `FAILED` ("automatic retry is disabled") so it shows under
`ar.automatic_billing.runs.needs_intervention` instead of staying `PROCESSING`; only runs re-queued through the API
(or by a configuration re-approval) are processed. Approval reconciliation still runs (it records a decision Finance
already made). To find runs needing a decision: `GET /paves/ar/api/v1/operations/billing-summary` →
`automaticBillingRuns.needingIntervention`, or the `FAILED`/`next_retry_at IS NULL` query in §5.

**External dependency outage** — TMS/UMS/SMTP: the app stays up (not probe dependencies). T&M runs fail at
`SOURCE_DATA_ACQUISITION`; `5xx`/timeouts retry automatically within the limits, `401/403` do not (credentials).
After the outage: runs with `next_retry_at` continue by themselves; exhausted ones per the query above.
Database outage: readiness goes DOWN (traffic stops), liveness stays UP (no restart loop); runs resume afterwards.

**Rolling back application code** — redeploy the previous image through the existing pipeline / ArgoCD by its
commit-SHA tag (never `:latest` — see §9). Schema changes from §7 are additive and stay; earlier code ignores the
new tables/columns, except: `READY_TO_SEND` invoices (Phase 10) are unknown to pre-Phase-10 code, and delivery
status `UNCERTAIN` (V14 release) is unknown to earlier code — reading such a delivery fails there. Before rolling
back past the V14 release, resolve every `UNCERTAIN` delivery (§6.1):
```sql
SELECT COUNT(*) FROM invoice_delivery WHERE status = 'UNCERTAIN';   -- must be 0
```
Earlier code also re-allows sending an already-sent invoice (G-19): brief Finance not to resend during a rollback.

**What a rollback cannot undo** — emails already sent, receivables created after a SENT delivery, invoice numbers
issued. Correct those through the existing invoice correction / receivable processes.

---

## 7. Database migrations

Schema changes are applied by Hibernate (`ddl-auto=update`, additive only) **and** the reviewed scripts in
`src/main/resources/db/migration`, which carry the checks Hibernate cannot do (duplicate detection before unique
keys, backfills). V1–V11 were written for manual application on top of a Hibernate-created schema — none creates
the core tables — so they must never be executed by a tool against an existing database.

| Script | Change | Prerequisite / impact |
|---|---|---|
| `V1`–`V5` | earlier phases | as documented in each script |
| `V6__invoice_billing_snapshot_unique.sql` | `uk_invoice_billing_snapshot` | STEP 1 must return no duplicates |
| `V7__automatic_billing_run.sql` | new table `automatic_billing_run` | id type check (STEP 1) |
| `V8__billing_snapshot_item_billed_source_unique.sql` | column + `uk_billing_snapshot_item_billed_source` | nullable column on a large table (MySQL 8: instant); unique index build is online; optional backfill after the duplicate check |
| `V9__invoice_document_ready_to_send.sql` | new table `invoice_document` (PDF `LONGBLOB`) | id type check; storage grows with approved invoices |
| `V10__recurring_renewal_approval_status.sql` | nullable `billing_subscription_configuration.renewal_approval_status` | additive; review renewals created before it (query in the script) |
| `V11__approval_actor_attribution.sql` | maker/checker columns on `billing_configuration`, `invoice_approval_history.action_by_user_id` | additive, nullable |
| `V12__tax_calculation_currency.sql` | nullable `tax_calculation.currency_code` | additive; older calculations keep NULL (checked on tax region only) |
| `V13__automatic_billing_run_recovery.sql` | `automatic_billing_run.requeued_at`, table `automatic_billing_run_event` | additive |
| `V14__invoice_delivery_accountability.sql` | requester/resolution columns on `invoice_delivery` | additive, nullable |

**Flyway (G-27).** Flyway is in the build but off (`AR_FLYWAY_ENABLED=false`). Adopting an environment — once, by a
DBA, in a maintenance window:
1. Back up the database.
2. Verify which scripts are applied (each script's own checks, and the column/constraint queries below). Let `N` be
   the highest version such that it and every earlier one are fully applied.
3. Deploy with `AR_FLYWAY_ENABLED=true AR_FLYWAY_BASELINE_ON_MIGRATE=true AR_FLYWAY_BASELINE_VERSION=N`. Flyway
   records baseline `N` in `flyway_schema_history` and applies only `V(N+1)`… (additive) before Hibernate starts.
4. Check: `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;` — all `success = 1`.
5. Remove `AR_FLYWAY_BASELINE_ON_MIGRATE` (it only acts on a schema without history; leaving it is harmless).
6. In staging first, then production: `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. Startup then fails on any schema
   mismatch instead of Hibernate patching it silently.

A failed migration leaves Flyway's history marked failed and stops startup: restore or repair the schema by hand
(the scripts are additive, so this means completing or removing the partial change), then `flyway repair` and
redeploy. Never edit an applied script (its checksum is validated); fix forward with a new version.

**A fresh database** has no baseline script for the core tables: start once with Flyway off and `ddl-auto=update`
(Hibernate creates the schema from the entities, including every V-script change), then adopt it as above with
`N` = the latest version. Not verified against MySQL in this repository's CI — see the MySQL integration test below.

`ddl-auto=update` silently skips a unique key it cannot create (existing duplicates) — verify the constraints:
```sql
SELECT index_name, table_name FROM information_schema.statistics WHERE table_schema = DATABASE()
 AND index_name IN ('uk_invoice_billing_snapshot','uk_invoice_billing_schedule','uk_invoice_number',
   'uk_automatic_billing_run_period','uk_automatic_billing_run_schedule','uk_billing_snapshot_item_billed_source',
   'uk_invoice_document_invoice','uk_receivable_invoice');
SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = DATABASE()
 AND (table_name, column_name) IN (('tax_calculation','currency_code'),('automatic_billing_run','requeued_at'),
   ('invoice_delivery','resolution_reason'),('invoice_approval_history','action_by_user_id'));
```

**MySQL verification.** `MySqlIntegrationTest` (Testcontainers MySQL 8.0.42; needs Docker) checks the unique keys,
the V11→V14 Flyway upgrade with data kept, and the atomic run claim on MySQL:
`./mvnw test -Dtest=MySqlIntegrationTest`. It is skipped where Docker is unavailable and has **not** been run yet.

---

## 8. CI/CD quality gates

Pipelines are defined by shared Jenkins libraries outside this repository (`Jenkinsfile` → `buildPipeline`,
`Jenkinsfile.cd` → `deployToDev`, `JenkinsfileProd` → `deployToProd`). The `Dockerfile` builds with `-DskipTests`,
so tests must be a separate, blocking pipeline stage. Required gates (to be confirmed in the shared libraries):

1. `./mvnw -B verify` — compile + all tests (self-contained: H2, no external systems; never needs `.env`).
2. Sonar analysis (`sonarProjectKey` already configured) and the existing dependency/security checks.
3. Image build and push with an immutable tag (commit SHA), not only `:latest`.
4. Migration scripts (§7) applied and verified by a DBA/approver before the deploy step.
5. Deploy through ArgoCD; post-deploy check: `GET /paves/ar/actuator/health/readiness` = `UP`.
6. Production: manual approval (existing `deployToProd` process); never on compile success alone.

Gate 1 status: the legacy failures are resolved except one test that encodes an undecided Finance policy
(`BillingOccurrenceServiceImplTest.effectiveFromChange_WithInvoiced_ThrowsException`, BD-05 in
`BUSINESS_DECISIONS.md`); decide it, then make the gate blocking. Add `MySqlIntegrationTest` with Docker on the agent.

---

## 9. Deployment checklist — before `AR_AUTOMATIC_BILLING_ENABLED=true`

- [ ] Migrations V6–V14 applied (or the environment adopted by Flyway, §7); constraint/column queries in §7 return all rows.
- [ ] `MySqlIntegrationTest` passed on a Docker-enabled agent.
- [ ] Probes in `intranet-devops` `k8s/backend/ar/deployment.yaml` use `/actuator/health/liveness|readiness`
      (the old `/paves/ar/health` path answered 401 before this release).
- [ ] Image deployed by immutable tag; previous tag recorded for rollback.
- [ ] **Service-to-service TMS authentication exists** (otherwise scheduled T&M fails with 401 — non-T&M types are unaffected).
- [ ] `TZ` decided and set; `AR_AUTOMATIC_BILLING_BACKFILL_MAX_AGE_DAYS` decided (0 = bill every missed due event).
- [ ] Active company profile, tax configurations and tax regions present (invoice and tax prerequisites).
- [ ] Alert routing for the §4 conditions agreed (or a daily manual check of the §5 queries), including
      `DELIVERIES_UNKNOWN_OUTCOME`, `RUNS_APPROVED_NOT_COMPLETED`, `SENT_WITHOUT_RECEIVABLE` (thresholds: operations decision).
- [ ] `AR_CORS_ALLOWED_ORIGINS` set to the production frontend origin(s) if it is served cross-origin (empty = none).
- [ ] `AR_AUTH_INVOICE_APPROVAL`, `AR_AUTH_INVOICE_DELIVERY`, `AR_AUTH_CONFIGURATION_APPROVAL`,
      `AR_AUTH_OPERATIONS_RECOVERY`, `AR_OPERATIONS_REPORT_AUTHORITIES` set to the agreed UMS roles/permissions.
- [ ] Finance decisions in `BUSINESS_DECISIONS.md` that block release are closed.
- [ ] Start with one environment and a small `AR_AUTOMATIC_BILLING_BATCH_SIZE`; watch the first ticks' outcome lines.
