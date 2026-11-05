# AR Module — Finance Business-Decision Register

> Status (2026-10-21): **no decision below has been made by Finance.** Each entry records what the code does today,
> what is documented, the financial impact, the options, and who must decide. Nothing here was implemented on an
> assumed policy. Where a missing decision could silently produce a wrong invoice, the affected **automatic** path
> stops for a person instead (noted per entry); manual flows are unchanged.

| ID | Area | Blocks release? | Decision owner | Status |
|---|---|---|---|---|
| BD-01 | Retention (Fixed Price) | Yes, for configurations with retention > 0 | Finance Controller (+ tax advisor) | Open — automation refuses those configurations |
| BD-02 | Advance received (Fixed Price) | Yes, for configurations with an advance > 0 | Finance Controller (+ tax advisor) | Open — automation refuses those configurations |
| BD-03 | Expense billing | No (feature absent) | Finance + Product owner | Open |
| BD-04 | Rounding policy | Confirm before UAT sign-off | Finance Controller | Open — current convention unconfirmed |
| BD-05 | Effective From moved past an INVOICED occurrence | Yes (one failing test encodes it) | Finance billing owner | Open |
| BD-06 | Resending an invoice already sent | No | AR Manager | Open — not possible today |
| BD-07 | Correcting tax after a configuration's currency/tax region changed | No (invoicing is refused) | Finance Controller | Open |
| BD-08 | Operational thresholds and alert routing | Yes (production monitoring) | Operations / AR support lead | Open |
| BD-09 | UMS roles/permissions and JWT audience | Yes | Security / UMS owner | Open |
| BD-10 | Backfill age and time zone | Yes (before enabling automation) | Finance + Operations | Open (runbook §9) |

---

## BD-01 Retention (Fixed Price)

- **Existing behaviour.** `retentionPercentage` (0–100, required) is captured and validated on the Fixed Price
  configuration (`BillingFixedPriceRequestDto`, `BillingFixedPriceServiceImpl`). A `retentionAmount` (contract × % ,
  HALF_UP, 2 dp) is shown in configuration responses. Occurrences, tax, invoices and receivables **ignore it**: each
  invoice bills the full occurrence amount. T&M, Milestone and Recurring have no retention at all.
- **Documented expectation.** None found (no requirement, UAT scenario or functional document mentions retention).
- **Financial impact.** If retention should be withheld, invoices and receivables are overstated by the retention
  amount and no retained balance exists to release later.
- **Options.** (A) Deduct retention on each invoice and track a retained balance released by a later "retention
  release" invoice. (B) Invoice gross; track retention as a separate receivable bucket collected later.
  (C) Informational only — no billing effect (then the automation guard is removed).
  Each option also needs: whether tax is on gross or net, and release triggers/dates.
- **Implementation status.** Not implemented. **Guard (G-32):** automatic billing fails a Fixed Price run whose
  configuration has retention > 0 at the first step (nothing created; run needs intervention; message says
  "bill it manually after review"). Find affected configurations:
  ```sql
  SELECT BIN_TO_UUID(billing_configuration_id), retention_percentage, advance_received
    FROM billing_fixed_price_configuration WHERE is_active = 1 AND (retention_percentage > 0 OR advance_received > 0);
  ```

## BD-02 Advance received (Fixed Price)

- **Existing behaviour.** `advanceReceived` (≥ 0, must not exceed the billable amount after retention) is captured;
  `remainingReceivable = billable − advance` is display-only. It is never deducted from occurrences or invoices
  and never applied against receivables. No advance/prepayment invoice type exists.
- **Documented expectation.** None found.
- **Financial impact.** Clients can be invoiced again for amounts already paid in advance; receivables overstate
  what is owed.
- **Options.** (A) Offset the advance against the first invoice(s) until exhausted. (B) Issue an advance invoice
  when received and show it as an adjustment on later invoices. (C) Informational only. Tax treatment of advances
  (e.g. GST at receipt) must be decided with (A)/(B).
- **Implementation status.** Not implemented; same automation guard as BD-01.

## BD-03 Expense billing

- **Existing behaviour.** `BillingConfiguration.expenseBillingEligible` is captured; `BillingItemType.EXPENSE` and
  `billing_snapshot.expense_amount` exist, but no expense source is integrated: `expenseAmount` is always 0
  (`BillingSnapshotServiceImpl`) and the eligibility flag is not read by billing.
- **Documented expectation.** A DTO comment only ("Time & Material only").
- **Financial impact.** Eligible client-billable expenses are never invoiced (under-billing); no wrong amount appears
  on an invoice.
- **Options / questions.** Expense source (TMS, expense system, manual entry); client-billable vs internal
  reimbursement; approval required before billing; pass-through vs markup; tax treatment; invoice-line
  representation; duplicate prevention (one billed reference per expense, like timesheets).
- **Implementation status.** Feature absent; nothing changed.

## BD-04 Rounding

- **Existing behaviour.** All money at scale 2 (hard-coded; not currency-aware).
  - HALF_UP: T&M line (hours × rate), each tax component (taxable × rate), retention display.
    Subtotal = sum of rounded lines; tax total = sum of rounded components; grand total = taxable + tax (no further rounding).
  - HALF_EVEN: allocation of contract value — Fixed Price equal split and last-period remainder, recurring
    proration and remainder, milestone percentage and remainder, schedule preview.
  - One-time Fixed Price occurrence uses the raw contract value.
- **Documented expectation.** `UAT_AND_ROLLOUT_PLAN.md` describes HALF_UP per line and per tax component as "the
  tax engine's convention" — not a Finance-approved rule. HALF_EVEN is not documented anywhere.
- **Financial impact.** Cent-level differences between methods; per-component rounding can differ from rounding
  the tax total; currencies with 0 or 3 decimals (JPY, KWD) would be wrong.
- **Options.** Confirm current conventions as policy; or unify allocation on HALF_UP; decide component- vs
  total-level tax rounding; decide currency-aware scale.
- **Implementation status.** Unchanged (no test was altered to make numbers reconcile).

## BD-05 Effective From moved past an INVOICED occurrence

- **Existing behaviour.** Moving Effective From later is refused when a TAX_PENDING or TAX_CALCULATED occurrence
  falls before the new date, but **not** for an INVOICED one (it is kept, later periods are regenerated).
- **Evidence both ways.** G-05/G-01 treat billed amounts as final and refuse re-dating an INVOICED one-time
  occurrence; `BillingConfigurationServiceImpl` preserves INVOICED rows "for audit". Two existing tests assert the
  move is allowed; `BillingOccurrenceServiceImplTest.effectiveFromChange_WithInvoiced_ThrowsException` asserts it
  is refused and **fails** today. `BILLING_OCCURRENCE_VERIFICATION_REPORT.md` lists the policy as open.
- **Financial impact.** An invoiced period ends up outside the contract's effective window.
- **Options.** (A) Refuse (as for TAX_CALCULATED) — the failing test then passes, the two others change.
  (B) Allow — the failing test is changed to assert the INVOICED row is kept unchanged.
- **Implementation status.** Undecided; the test stays red so the CI gate cannot pass silently.

## BD-06 Resending an invoice already sent

- **Existing behaviour (G-19).** An invoice is delivered once by the ordinary send; a second send is refused. A
  FAILED attempt may be retried; an UNCERTAIN one is resolved by operations.
- **Options.** An explicit, audited "resend copy" (reason required, no receivable effect, possibly another
  recipient) — or keep resends outside the system.
- **Implementation status.** No resend mechanism (none was specified).

## BD-07 Correcting tax after a configuration's currency or tax region changed (G-12)

- **Existing behaviour.** Invoice generation for an occurrence refuses when the configuration's currency or tax
  region differs from what its tax calculation recorded (G-12).
- **Options.** A Finance/Operations-authorised "recalculate tax" action for un-invoiced occurrences (audited), keeping
  historical calculations superseded rather than deleted.
- **Implementation status.** Implemented in recovery layer (`TaxRecalculationRecoveryService`,
  `POST /api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax`, Flyway V15 schema). Requires
  `OPERATIONS_RECOVERY` authority. A reason is mandatory. The existing tax calculation is retained with
  `superseded_at`, `superseded_by`, `superseded_by_user_id`, `superseded_reason`, and `superseded_billing_schedule_id`,
  and fresh tax is calculated under the new approved configuration. If the occurrence is already invoiced,
  recalculation is strictly refused.

## BD-08 Operational thresholds and alert routing

No SLO or threshold is documented; every `ar.operations.alerts.thresholds.*` is empty (nothing evaluated) and no
alert routing exists in this repository. Owner to set thresholds (recommended triggers: runbook §4) and route
`[OperationsAlert]` / metrics to the team's alerting platform.

## BD-09 UMS authorities and JWT audience

`AR_AUTH_CONFIGURATION_APPROVAL`, `AR_AUTH_INVOICE_APPROVAL`, `AR_AUTH_INVOICE_DELIVERY`,
`AR_AUTH_OPERATIONS_RECOVERY`, `AR_OPERATIONS_REPORT_AUTHORITIES` are empty (= nobody) until the UMS role or
permission names are agreed. `AR_JWT_AUDIENCES` is empty (audience not validated) until UMS defines the audience
of tokens issued for AR.

## BD-10 Backfill age and time zone

`AR_AUTOMATIC_BILLING_BACKFILL_MAX_AGE_DAYS` (0 = bill every missed due event) and the container `TZ` decide which
billing dates are "due today". Decide both before enabling automatic billing (runbook §9).
