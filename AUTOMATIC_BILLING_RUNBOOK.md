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
SQL and bind values are never logged outside the `local` profile.

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
A SENT delivery without a receivable (logged `… was sent but its receivable could not be created`) is recovered by
`ReceivableService.createReceivableFromInvoice` — **do not resend** the invoice.

---

## 6. Incident procedures

**Pause** — disable (§3). In-flight runs keep their completed steps; nothing is rolled back or duplicated.

**Find in-progress work** — the `PROCESSING` query in §5. A run with a live claim belongs to a running pod.

**Resume after the cause is fixed** — enable (§3). The scheduler then, in order: resumes `SCHEDULED` runs, recovers
interrupted runs (expired claim), retries due `FAILED` runs, then picks up new and missed events. Every step is
find-or-create on unique keys (run per event; snapshot per project+period; one claim per TMS timesheet; tax and
invoice per occurrence/snapshot; receivable per invoice), so resuming never duplicates financial records.

**A `FAILED` run without retry after the data was corrected** — no API exists to re-queue a run. Make it due again
(audited change, after confirming the cause is fixed):
```sql
UPDATE automatic_billing_run SET next_retry_at = NOW()
 WHERE automatic_billing_run_id = UUID_TO_BIN('<run-id>') AND status = 'FAILED';
```
It resumes from `last_completed_step`. Never delete runs, snapshots, tax calculations, invoices or receivables, and
never set runs back to `SCHEDULED` in bulk.

**External dependency outage** — TMS/UMS/SMTP: the app stays up (not probe dependencies). T&M runs fail at
`SOURCE_DATA_ACQUISITION`; `5xx`/timeouts retry automatically within the limits, `401/403` do not (credentials).
After the outage: runs with `next_retry_at` continue by themselves; exhausted ones per the query above.
Database outage: readiness goes DOWN (traffic stops), liveness stays UP (no restart loop); runs resume afterwards.

**Rolling back application code** — redeploy the previous image through the existing pipeline / ArgoCD (do not
rely on the mutable `:latest` tag — see §9). Schema changes from §7 are additive and stay; earlier code ignores the
new tables/columns, except that `READY_TO_SEND` invoices (Phase 10) are unknown to pre-Phase-10 code: roll back
past Phase 10 only after confirming no invoice has that status, or not at all.

**What a rollback cannot undo** — emails already sent, receivables created after a SENT delivery, invoice numbers
issued. Correct those through the existing invoice correction / receivable processes.

---

## 7. Database migrations

There is no migration tool. Schema changes are applied by Hibernate (`ddl-auto=update`, additive only) **and** the
reviewed scripts in `src/main/resources/db/migration`, which carry the checks Hibernate cannot do (duplicate
detection before unique keys, backfills). Run them manually, in order, against each environment **before** deploying
the code that needs them; every script deletes nothing and documents its prerequisite queries.

| Script | Change | Prerequisite / impact |
|---|---|---|
| `V1`–`V5` | earlier phases | as documented in each script |
| `V6__invoice_billing_snapshot_unique.sql` | `uk_invoice_billing_snapshot` | STEP 1 must return no duplicates |
| `V7__automatic_billing_run.sql` | new table `automatic_billing_run` | id type check (STEP 1) |
| `V8__billing_snapshot_item_billed_source_unique.sql` | column + `uk_billing_snapshot_item_billed_source` | nullable column on a large table (MySQL 8: instant); unique index build is online; optional backfill after the duplicate check |
| `V9__invoice_document_ready_to_send.sql` | new table `invoice_document` (PDF `LONGBLOB`) | id type check; storage grows with approved invoices |

`ddl-auto=update` silently skips a unique key it cannot create (existing duplicates) — that is why the scripts'
duplicate checks must be run and their constraints verified:
```sql
SELECT index_name, table_name FROM information_schema.statistics WHERE table_schema = DATABASE()
 AND index_name IN ('uk_invoice_billing_snapshot','uk_invoice_billing_schedule','uk_invoice_number',
   'uk_automatic_billing_run_period','uk_automatic_billing_run_schedule','uk_billing_snapshot_item_billed_source',
   'uk_invoice_document_invoice','uk_receivable_invoice');
```
Once every script is applied, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` makes a schema mismatch stop startup instead
of being patched silently (recommended for production; decision pending).

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

Known blocker for gate 1: 58 legacy tests in 8 classes currently fail (see the Phase 12 report); they must be fixed
or explicitly quarantined before the gate can be made blocking.

---

## 9. Deployment checklist — before `AR_AUTOMATIC_BILLING_ENABLED=true`

- [ ] Migrations V6–V9 applied; constraint query in §7 returns all keys.
- [ ] Probes in `intranet-devops` `k8s/backend/ar/deployment.yaml` use `/actuator/health/liveness|readiness`
      (the old `/paves/ar/health` path answered 401 before this release).
- [ ] Image deployed by immutable tag; previous tag recorded for rollback.
- [ ] **Service-to-service TMS authentication exists** (otherwise scheduled T&M fails with 401 — non-T&M types are unaffected).
- [ ] `TZ` decided and set; `AR_AUTOMATIC_BILLING_BACKFILL_MAX_AGE_DAYS` decided (0 = bill every missed due event).
- [ ] Active company profile, tax configurations and tax regions present (invoice and tax prerequisites).
- [ ] Alert routing for the §4 conditions agreed (or a daily manual check of the §5 queries).
- [ ] CORS origin(s) of the production frontend configured (currently `http://localhost:5173` only, `CorsConfig`).
- [ ] Start with one environment and a small `AR_AUTOMATIC_BILLING_BATCH_SIZE`; watch the first ticks' outcome lines.
