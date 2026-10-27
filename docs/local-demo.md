# AR backend - local demo mode

Run the Account Receivable backend on your machine for API development and demos **without touching shared
infrastructure**. The `local-demo` profile uses a dedicated local MySQL schema, starts no CDC consumer, writes nothing to
the shared AR database, and cannot call TMS, PMS, RMS, XMS, UMS or a real SMTP server.

## Prerequisites (Windows, PowerShell 5.1 or later)

* **Java 21** (the project targets Java 21 / Spring Boot 3.2.5) and **Maven 3.9+** with `mvn` on the PATH (the wrapper calls `mvn`; tested with 3.9.11).
* **MySQL Server 8.4.x** (tested with 8.4.8) **and the `mysql` command-line client on the PATH.** A server may already be installed
  without its client on the PATH - check with `mysql --version`.
* Git, and a clone of this repository on the branch that contains this document.
* **You do not need a `.env` file**, and the demo never reads one. Do not point it at one.

## Quick start

```
# 1. start your own MySQL on 127.0.0.1:3399 (section 1), then, from the repository root:
.\scripts\local-demo\setup-demo-env.ps1 -GenerateAll -Remember -ProvisionDatabase   # your own credentials + ar_demo schema/user
.\scripts\local-demo\start-demo.ps1                                                   # starts the backend (local-demo only)
# 2. once, after the backend has started and created the tables. The backend keeps running in terminal 1, so use a SECOND terminal
#    and first re-run .\scripts\local-demo\setup-demo-env.ps1 there (with -Remember it reloads your stored values):
$env:MYSQL_PWD = $env:AR_DEMO_DB_PASSWORD   # the mysql client reads the password from the session; it is never typed, printed or put on the command line
mysql --host=127.0.0.1 --port=3399 --user=ar_demo_app --database=ar_demo < src\main\resources\sql\demo\local_demo_seed.sql
Remove-Item Env:MYSQL_PWD
# 3. a token for the API calls in section 5:
.\scripts\local-demo\new-token.ps1
```

`setup-demo-env.ps1` is described in section 2. Each person creates their own credentials; never share or commit them.

## What is isolated

| Concern | In `local-demo` |
|---|---|
| AR database | A local MySQL schema you create (`AR_DEMO_DB_URL`, default `127.0.0.1:3399/ar_demo`). Hibernate creates the tables on first start. |
| Client / project / milestone data | Not synchronised. The seed script provides it (what CDC would have delivered). |
| Debezium engines, CDC runners, table initializer, milestone initial sync, inbox processing | **Not created** (gated off; the Debezium configuration classes are not created either). |
| Billing status schedulers (every minute) | **Not created** - seeded data is not changed by the clock. |
| TMS / XMS | Not reachable (`tms.api.base-url` is pinned to a dead local port, XMS is off). Billing snapshots come from the seed. |
| Email | Delivery is **simulated**. `DemoEmailService` replaces the real email service and `LogOnlyMailSender` replaces the SMTP sender: no connection is ever made and the invoice delivery is recorded as **`SIMULATED`, never `SENT`** (see "Email in the demo"). |
| Authentication | HS256 tokens signed with your local secret (`AR_DEMO_JWT_SECRET`). `SecurityConfig` is unchanged, so a valid token is still required. **Authentication is enforced; role authorization mostly is not** - see "Authentication and authorization". |

## Safety model (why you can run it next to shared systems)

1. **Profile file** `application-local-demo.properties` overrides every property that references a shared variable
   (`DB_*`, `RMS_DB_*`, `PMS_DB_*`, `TMS_API_BASE_URL`, `JWT_ISSUER_URI`, `MAIL_*`, `XMS_*`, `AUTH_JWT_*`). It reads only
   `AR_DEMO_*` variables, because `.env` is loaded in every profile.
2. **Isolation guard** (`com.AccountReceivableManagement.demo.LocalDemoIsolationValidator`) runs twice - early (via
   `META-INF/spring.factories`) and again before any bean exists - and **refuses to start** if the datasource or any guarded
   endpoint is not loopback/blank, a gated component is not explicitly disabled, a co-active `dev`/`prod`/... profile is
   present, or the JWT secret is missing or shorter than 32 characters. It also catches an OS variable such as
   `SPRING_DATASOURCE_URL` that would override the profile. It never prints credentials.
3. **Pinned property**: `tms.api.base-url` is the relaxed form of the OS variable `TMS_API_BASE_URL`, which outranks config
   files, so the early guard pins it to the local value.
4. **Gates** with defaults of *enabled* (so default, `local` and production behaviour is unchanged):
   `ar.cdc.infrastructure.enabled`, `ar.cdc.inbox-processor.enabled`, `ar.billing.status-schedulers.enabled`, plus the
   existing `client-cdc.enabled`, `project-cdc.enabled`, `project-cdc.milestone-initial-sync.enabled`.
5. The demo beans (`DemoJwtConfig`, `DemoMailConfig`, `DemoEmailService`, `LocalDemoIsolationConfig`) are `@Profile("local-demo")` only.
6. **DevTools restart is switched off by the wrapper** (`-Dspring.devtools.restart.enabled=false` on the application JVM). DevTools reads
   that setting from JVM system properties before any config file is loaded, so the profile file cannot do it; a bare start would
   relaunch the app as `restartedMain` and restart it on every classpath change.

## 1. A dedicated local MySQL (8.4.x recommended)

Use a **new, empty** server or schema. **Do not point the demo at an existing local database unless it is yours and
disposable** - the seed script itself refuses to run unless the database is `ar_demo` and its business tables are empty. A MySQL service may already run on port 3306 on your machine: leave it alone, or create a dedicated schema
and user in it.

Portable server (example, Windows; adjust paths; a data directory that does not exist yet):

```
mysqld --no-defaults --initialize-insecure --datadir=C:\ar-demo\data
mysqld --no-defaults --datadir=C:\ar-demo\data --port=3399 --bind-address=127.0.0.1 --mysqlx=OFF
```

Then, in a second terminal (the server is bound to loopback only; `root` has no password on a fresh `--initialize-insecure`
instance - fine for a throw-away local server, never for anything reachable):

```
mysql --host=127.0.0.1 --port=3399 --user=root
CREATE DATABASE ar_demo CHARACTER SET utf8mb4;
CREATE USER 'ar_demo_app'@'localhost' IDENTIFIED BY '<choose a password>';
CREATE USER 'ar_demo_app'@'127.0.0.1' IDENTIFIED BY '<the same password>';
GRANT ALL PRIVILEGES ON ar_demo.* TO 'ar_demo_app'@'localhost';
GRANT ALL PRIVILEGES ON ar_demo.* TO 'ar_demo_app'@'127.0.0.1';
```

Both accounts are created because, depending on the server's name-resolution settings, a connection to `127.0.0.1` can match either.
`setup-demo-env.ps1 -ProvisionDatabase` (section 2) does exactly this for you - and nothing more.

## 2. Credentials (set in the shell, **not** in `.env`)

Two values are required, and each person makes their own: `AR_DEMO_DB_PASSWORD` (the password of the MySQL user `ar_demo_app`)
and `AR_DEMO_JWT_SECRET` (signs the demo tokens: at least 32 characters, at least 10 different characters, no `<` or `>`, and not
the same as the database password). Optional: `AR_DEMO_DB_URL` (loopback only; default `jdbc:mysql://127.0.0.1:3399/ar_demo`),
`AR_DEMO_DB_USERNAME` (default `ar_demo_app`), `AR_DEMO_SERVER_PORT` (default 8080).

**Recommended - the setup script** (`scripts\local-demo\setup-demo-env.ps1`):

```
.\scripts\local-demo\setup-demo-env.ps1                        # prompts (hidden); press Enter to have a strong random value generated
.\scripts\local-demo\setup-demo-env.ps1 -GenerateAll -Remember  # no prompts; generate both and remember them (encrypted, this Windows user only)
.\scripts\local-demo\setup-demo-env.ps1 -ProvisionDatabase     # optional: also create schema ar_demo and the app user on YOUR local MySQL
.\scripts\local-demo\setup-demo-env.ps1 -Forget                # delete the remembered credentials and clear the session variables
```

* It sets the two variables for **this PowerShell session only**; run it again in each new shell (with `-Remember` it reloads your
  stored values, so a generated database password keeps matching your MySQL user).
* Values are generated with a cryptographic random number generator, are never printed, never written to the repository, never
  passed on a command line. `-Remember` stores them under `%LOCALAPPDATA%\ar-local-demo\credentials.xml`, outside the repository,
  encrypted with Windows DPAPI (readable only by your Windows user on this machine).
* It then runs `start-demo.ps1 -CheckOnly`, so the real isolation rules are applied, and removes what it set if they refuse.
  It starts nothing and cannot select any profile other than `local-demo`.
* `-ProvisionDatabase` is optional and additive: it creates `ar_demo` and the user (for `localhost` and `127.0.0.1`) with
  `CREATE ... IF NOT EXISTS` and `GRANT ... ON ar_demo.*`, and **never drops, alters, deletes or seeds anything**. It first verifies
  that the target is loopback, the schema is exactly `ar_demo`, the server is on the expected port and bound to loopback only, and
  you typed `ar_demo` to confirm. It asks for the MySQL admin password (hidden; or use `MYSQL_PWD` / `-AdminNoPassword` for a fresh
  `--initialize-insecure` server), then proves the app user can log in. If the user already exists with a different password it
  stops and tells you; it will not change it.
* `scripts\local-demo\test-setup-demo-env.ps1` tests all of this without contacting any database.

**Manual alternative** (what the script does for you):

```
$env:AR_DEMO_DB_PASSWORD = Read-Host -Prompt 'DB password (hidden)' -AsSecureString | ForEach-Object { [System.Net.NetworkCredential]::new('', $_).Password }
$env:AR_DEMO_JWT_SECRET  = Read-Host -Prompt 'JWT secret (hidden, 32+ characters)' -AsSecureString | ForEach-Object { [System.Net.NetworkCredential]::new('', $_).Password }
```

Do not combine `local-demo` with another environment profile; the guard refuses `dev`, `prod`, `production`, `staging`,
`stage`, `qa` and `uat`.

> **Why the wrapper matters.** `.env` is loaded in every profile and contains `spring.profiles.active=local`, so a plain
> `mvn spring-boot:run` starts the **`local`** profile (the shared-infrastructure one), not the demo. Always start the demo
> with the wrapper below; it passes the profile on the command line, which outranks both the OS environment and `.env`, so
> it cannot fall back to `local`.

## 3. Start the backend

```
.\scripts\local-demo\start-demo.ps1             # runs: mvn spring-boot:run -Dspring-boot.run.profiles=local-demo
.\scripts\local-demo\start-demo.ps1 -CheckOnly  # runs every check, starts nothing
```

The wrapper runs from the repository root, sets `SPRING_PROFILES_ACTIVE=local-demo`, and **refuses to start (exit code 2,
"Nothing was started")** when: a profile other than `local-demo` is requested; `AR_DEMO_DB_PASSWORD` or `AR_DEMO_JWT_SECRET`
is missing; the secret is shorter than 32 characters, has fewer than 10 distinct characters, contains `<`/`>` (an unedited
placeholder) or equals the DB password; `AR_DEMO_DB_URL` is not loopback; `AR_DEMO_SERVER_PORT` is invalid; or an
environment variable or JVM option would override the datasource, profile list or config location. It never prints secret
values and never reads or edits `.env`. `scripts\local-demo\test-start-demo.ps1` exercises these refusals.

Expect, near the top of the log:

```
The following 1 profile is active: "local-demo"
local-demo isolation OK: datasource host is loopback, CDC engines/runners/milestone sync/inbox processing/billing schedulers are disabled, ...
```

and **no** Debezium, inbox-processing or scheduler lines. If the guard refuses, it lists every violation (host names, flag
names and lengths only) and the application does not start. Hibernate creates the (empty) tables in `ar_demo`. Stop the
backend before seeding or leave it running; the seed only inserts rows.

## 4. Seed the demo data - first run only

```
$env:MYSQL_PWD = $env:AR_DEMO_DB_PASSWORD   # the mysql client reads the password from the session; it is never typed, printed or put on the command line
mysql --host=127.0.0.1 --port=3399 --user=ar_demo_app --database=ar_demo < src\main\resources\sql\demo\local_demo_seed.sql
Remove-Item Env:MYSQL_PWD
```

Do **not** add `--force`. **The seed is not re-runnable.** It never deletes or updates any data. Before it inserts anything
a guard procedure aborts it (with a message telling you what to do) unless:

1. the current database is named `ar_demo`;
2. every table it writes to exists and is InnoDB (so the single transaction is atomic);
3. every business table - clients, projects, milestones, billing configurations/snapshots/schedules, tax configurations and
   calculations, invoices, receivables and the other tables that reference them - is **empty**. Reference/master tables may
   already hold rows; the seed adds its own with `INSERT IGNORE` and refers to them by their unique name/code.

All inserts then run in one transaction; if a statement fails the client stops, the connection closes and MySQL rolls the
whole run back. To run it again (or after any failed attempt you are unsure about), recreate the dedicated local schema:

```
DROP DATABASE ar_demo;  CREATE DATABASE ar_demo CHARACTER SET utf8mb4;   -- then re-grant if needed
```

start the backend once so Hibernate creates the tables, stop it, and run the seed again. Never run it against a shared
database. In any schema not named `ar_demo` the script prints a refusal and stops at its first statement, before creating anything.

**Verified on a disposable local MySQL 8.4.8:** run 1 inserted 61 rows into 20 tables; run 2 was refused (exit 1) and the row counts and
`CHECKSUM TABLE` of all 45 tables were identical afterwards; a wrong schema was refused with nothing created. **One side effect:** a refusal that happens
*after* the schema check (tables missing, not InnoDB, or data already present) leaves the empty stored procedure `ar_demo_seed_guard` in `ar_demo`. It holds
no data, the next run drops it first, and recreating the schema removes it.

### What it creates

2 clients; 5 projects (ACTIVE x2, **PLANNING**, **ARCHIVED**, COMPLETED); 5 milestones on project 9002 (2
`READY_FOR_BILLING`, 2 `NOT_READY`, 1 cancelled / `NOT_BILLABLE`); USD/INR, Net 15/30, billing types and frequencies;
a time-and-material configuration (project 9001, 3 role rates) and a milestone-plan configuration (project 9002, 4
installments), both approved and active; **tax master data**: region `DEMO-US` ("Demo United States", USD), a regime, an
active configuration for that region effective before the billing periods, and its components (6% state and 2% federal,
applied only for the same jurisdiction, and a 10% cross-jurisdiction component); and two billing snapshots,
`BS-DEMO-000001` (August, taxable 2137.50) and `BS-DEMO-000002` (September, taxable 1342.50), both **`READY_FOR_TAX`**, with
7 time entries. The snapshots carry the tax region and the source/destination jurisdictions the tax service reads (`DEMO-US`
for both - project 9001's location equals the tax region name, as snapshot creation derives it). **No tax-calculation,
invoice, receivable or history rows are seeded**: nothing claims downstream work that has no record. The company profile is
not seeded either (see below).

## 5. Call the API

```
$token = .\scripts\local-demo\new-token.ps1                          # FINANCE_EXECUTIVE, 120 minutes
$h = @{ Authorization = "Bearer $token"; 'Content-Type' = 'application/json' }
$base = 'http://localhost:8080/paves/ar'
Invoke-RestMethod -Headers $h "$base/api/billing-types"
.\scripts\local-demo\new-token.ps1 -Roles VIEWER -TtlMinutes 30      # a token without the FINANCE_EXECUTIVE role
```

Swagger UI (`/paves/ar/swagger-ui.html`) is public; use its *Authorize* button with the token for calls.

### Tax and invoice workflow (real API, in this order)

Snapshot ids: `de080000-0000-0000-0000-000000000001` (BS-DEMO-000001) and `de080000-0000-0000-0000-000000000002`
(BS-DEMO-000002).

**a. Create the company profile.** Invoice generation requires an active company profile, so create it through the real API
(there is only one company profile; creating a second is rejected). Only `legalName` is mandatory; the other fields are
optional and validated (`email` must be a valid address; length limits apply):

```
$body = '{"legalName":"Demo Company Ltd","addressLine1":"1 Demo Street","city":"Demo City","country":"Demo Land","email":"billing@demo-company.invalid"}'
Invoke-RestMethod -Method Post -Headers $h -Body $body "$base/api/v1/company-profile"
```

**b. Calculate tax** (creates the `CALCULATED` tax-calculation record; no request body):

```
$snap = 'de080000-0000-0000-0000-000000000001'
Invoke-RestMethod -Method Post -Headers $h "$base/api/v1/billing-snapshots/$snap/tax-calculation"
```

Expected (the real service's rules, HALF_UP, scale 2): BS-DEMO-000001 tax 171.00 (128.25 + 42.75), total 2308.50;
BS-DEMO-000002 tax 107.40 (80.55 + 26.85), total 1449.90.

**c. Generate the invoice** (needs the `CALCULATED` tax calculation from b and the company profile from a):

```
Invoke-RestMethod -Method Post -Headers $h "$base/api/v1/billing-snapshots/$snap/invoice"
```

Calling c before b, or before a, fails with the application's normal error - that is the real prerequisite check, not a demo
defect.

### Authentication and authorization (what the code really enforces)

* **Authentication is enforced.** `SecurityConfig` requires a valid, signed, unexpired token with the demo issuer for every
  `/api/**` request (and for any other non-public request). No token, an expired token, or a token signed with another secret gets
  `401`.
* **Endpoint-specific role authorization is generally NOT enforced.** `SecurityConfig` turns the token's `roles` into
  `ROLE_*` authorities, but no URL rule uses them and no controller or service uses `@PreAuthorize`/`hasRole` (verified by
  search). Any authenticated user - including a token minted with `-Roles VIEWER` - can call the endpoints in this guide.
  This was observed: a `VIEWER` token created the company profile (HTTP 201). It is how the application behaves outside the
  demo as well; the demo neither adds nor removes it, and nothing here should be read as role-based protection.
* **The only role check** is on the company *signature* endpoints (`PUT`/`DELETE .../company-profile/{id}/signature`), which
  require the `FINANCE_EXECUTIVE` role through `FinanceExecutiveAuthorizer`; reading the signature needs only authentication.
* Other safeguards that exist are record locks (for example, invoice approval needs an `APPROVAL` lock held by the caller) and
  business-state validation - these are not authorization by role.
* Treat the demo token's user and roles as labels, not as a security boundary.

### Email in the demo

Nothing is ever sent. Invoice delivery (`POST /api/v1/invoices/{id}/send`, available once the invoice is `APPROVED`) runs the
normal workflow - recipient resolution, PDF generation, delivery record, receivable creation - but the email step is simulated:

| | real delivery (outside the demo) | `local-demo` |
|---|---|---|
| `invoice_delivery.status` | `SENT` | **`SIMULATED`** |
| `sent_at` | set | **not set** (nothing was sent) |
| `provider_message_id` | the provider's `Message-ID` | `demo-simulated-<uuid>` (cannot be a real id) |
| response `deliveryStatus` / message | `SENT` / "Invoice sent to client successfully." | `SIMULATED` / "Invoice delivery SIMULATED (local-demo): no email was sent." |
| receivable | created after the `SENT` record commits | created after the `SIMULATED` record commits, so the demo can continue |

Implications: `SIMULATED` is a new, additive delivery status (the column is `VARCHAR(20)`; existing values and records are
unchanged and production never writes it). Receivable creation accepts a `SIMULATED` delivery as well as a `SENT` one; with no
`SIMULATED` rows (every non-demo database) that rule changes nothing. Any consumer that treats only `SENT` as "delivered" - for
example a screen filtering on it - will correctly not count a demo delivery.

The Intranet frontend (Invoice Generation and Invoice Detail pages) reads the returned `deliveryStatus` and shows `SIMULATED` as
**"Simulated — no email sent"**, never as "Sent to Client". A frontend build from before that change ignores the status and shows any HTTP 200 as sent.

### What the tax lookup actually requires

The service reads the snapshot's `tax_region_id`/`tax_region_code` and its source/destination tax jurisdiction codes, then
looks up the **active** tax configuration for that region whose `effective_from` is on or before the billing period and whose
`effective_to` is empty or not before it, and uses its **active** components. A component applies by `applicability_type`:
`SAME_JURISDICTION` only when source and destination match, `DIFFERENT_JURISDICTION` only when they differ. A snapshot without
a tax region, or a region with no active configuration, fails. The seed provides exactly this, and the automated tests run the
real repository query and `TaxCalculationServiceImpl` against the seed's own rows (on an in-memory H2 database).

> **Status of these claims.** The seed script, the tax lookup and the calculation arithmetic are validated offline by tests.
> The HTTP calls above, the seed against a real MySQL server, and invoice generation end to end have **not** been executed in
> this phase; confirm them on your first local run.

## What works and what does not

* **Works against the seeded local data:** master data, billing configurations (all types), rate cards, milestone plans,
  occurrences, record locks, billing-snapshot reads, client budget summary, receivables, company profile, exchange rates.
* **Tax calculation and invoice generation:** the seed supplies the tax master data and snapshot tax fields they need; follow the workflow in section 5 (company profile, then tax calculation, then invoice).
* **Does not work by design:** creating a *new* snapshot through TMS acquisition, XMS expense acquisition, real email
  (simulated: nothing is sent), and CDC synchronisation (nothing is consumed; the seed is the data).
* **CDC is not tested by `local-demo`.** All CDC components are switched off in this profile on purpose, so it can neither exercise
  nor break CDC. Test CDC through the existing development workflow or an isolated CDC environment, started with its normal
  configuration (not with `start-demo.ps1`). Default, `local`, `dev` and `prod` behaviour is unchanged: the new CDC/scheduler gates
  default to enabled and only this profile turns them off.
* The schema comes from Hibernate (`ddl-auto=update`), not from the manual `db/migration` scripts, so things only those
  scripts create (for example check constraints) are absent. `project_status` is `VARCHAR(30)` in a fresh schema.
* The billing status schedulers are off, so statuses do not change with the clock.

## Verifying isolation

* The two log lines above; `SELECT DATABASE(), @@port;` on the local server shows the schema and port in use.
* The suppressed-email line: `[local-demo] Email NOT sent: delivery simulated, no SMTP connection is made`, and a delivery record with status `SIMULATED`.
* The DevTools switch: the application thread is named `main`, not `restartedMain`, only one application JVM exists, and nothing listens on the LiveReload port 35729.
* Tests (`LocalDemo*Test` in `src/test/java/.../demo`): property resolution against a simulated shared environment, every
  guard rejection, bean absence/presence, demo-only beans outside the profile, default behaviour preserved, and the seed
  (structure and safety, column-by-column against the Hibernate schema, tax rules). `scripts\local-demo\test-start-demo.ps1`
  checks the wrapper's refusals.

## Reset

Stop the backend, then `DROP DATABASE ar_demo; CREATE DATABASE ar_demo CHARACTER SET utf8mb4;` on the local server (re-grant the
app user if needed), start the backend once, and seed again. Delete the data directory to discard
the whole local server.
