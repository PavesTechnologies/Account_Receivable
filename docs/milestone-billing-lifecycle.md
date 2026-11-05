# Milestone billing lifecycle (Phase 4A: partial, repeatable billing)

Applies to billing plans with `paymentStructure = MILESTONES`. `FULL_PAYMENT` and `INSTALLMENTS` are unchanged
(percentages must still total exactly 100, the last entry still absorbs rounding).

## What establishes the state (no new flag, no new column)

```
billing_payment_entry (pms_milestone_id, billing_date)            the plan's claim on a milestone
        |  same billing_configuration + period_start = period_end = billing_date
        v
billing_schedule (period_status, is_invoiced, is_active)          the occurrence generated at approval
        |  invoice.billing_schedule_id
        v
invoice (status)                                                  GENERATED / PENDING_APPROVAL / APPROVED / REJECTED
```

There is no foreign key from an entry to its occurrence; occurrence generation creates exactly one single-day occurrence per
entry (unique per configuration + date), so (configuration, billing date) is the link. The plan save therefore rejects two
milestone entries on the same billing date.

| State (`MilestoneBillingState`) | Records | Milestone can be |
|---|---|---|
| `AVAILABLE` | no active entry in an active plan | selected, if billable + `READY_FOR_BILLING` |
| `RESERVED` | active entry; no occurrence yet, or `SCHEDULED` | kept or removed by editing the plan (occurrences are deleted and regenerated on every re-approval) |
| `IN_BILLING` | occurrence `TAX_PENDING` / `TAX_CALCULATED` | **frozen**: not changeable, not removable |
| `INVOICED` | occurrence `INVOICED` or `is_invoiced = true` | **billed for good** |

Derived on read (`GET /api/projects/{pmsProjectId}/milestones` now returns `billingState`, `billingConfigurationId`,
`eligibleForBilling`, `ineligibilityReason`).

## Rules enforced on the server

1. **Eligibility of a newly selected milestone:** not deleted in PMS, `billable = true`, `billingReadiness = READY_FOR_BILLING`,
   same project. `executionStatus` is **not** checked: AR has no documented meaning for any of its values (only `COMPLETED` appears,
   in demo data) and PMS folds completion into `billingReadiness`. A not-yet-ready milestone is simply not selectable; it
   becomes selectable when PMS marks it ready, with no new plan.
2. **Partial allocation:** each entry is 0 < % <= 100 with at most 2 decimals; the percentages of the plan total at most 100
   (no longer exactly 100). A percentage is a share of the plan's `totalContractValue`, the same convention as the other
   structures. Amount = `total * % / 100`, HALF_EVEN, 2 decimals; every amount must be > 0. The last entry no longer absorbs
   "the rest" - only when the plan is allocated to exactly 100% is rounding drift settled on the last *new* entry, so a fully
   allocated plan bills exactly the contract value. Billed (frozen) amounts are never adjusted.
3. **Later cycles = editing the same plan** (one active plan per configuration is unchanged). An edit must send every frozen
   entry back unchanged (percentage, billing date, sequence) and cannot change the total contract value or the payment
   structure once any entry is frozen. New entries are added; the remaining balance is `total - frozen amounts`.
4. **No duplicate billing:** a milestone with an entry in billing/invoiced state - in *any* configuration, whatever its status,
   EXPIRED included - cannot be selected again. A milestone reserved by another live plan is still rejected (existing rule).
   The milestone rows are locked `FOR UPDATE` (ascending id) before these checks, so concurrent saves are serialised.
5. **Delete:** a plan with any frozen entry cannot be deleted (it would release milestones already being billed).
6. **PMS changes after reservation:** frozen entries are unaffected (what has been billed no longer depends on PMS). For
   entries still `RESERVED`, submission and approval of the configuration re-check eligibility; a milestone PMS has since deleted
   or taken out of `READY_FOR_BILLING` blocks submit/approve until it is removed from the plan or PMS makes it ready again.

## Business decisions still needed

* **Is `SCHEDULED` -> `TAX_PENDING` the right "billing has started" point?** The scheduler moves an occurrence to `TAX_PENDING`
  on its billing date; I treat that as the point of no return. An earlier or later cut-off is a one-line change
  (`BillingMilestonePlanServiceImpl.IN_BILLING_STATUSES`).
* **Invoice rejection / cancellation:** there is no cancel/void path. A `REJECTED` invoice is corrected and resubmitted on the
  same occurrence, so the milestone stays billed. If invoice cancellation is ever introduced it must also reset the occurrence;
  until then a mistakenly invoiced milestone cannot be re-billed.
* **Edits reset approval:** editing an APPROVED+ACTIVE plan (to add the next milestone) sends the configuration to
  PENDING_APPROVAL + INACTIVE (existing behaviour for every plan type). Frozen occurrences are not touched, but nothing new
  progresses until Finance re-approves. If adding a milestone should not pause billing, that needs a separate "append-only edit"
  approval path.
* **Gate after approval (Phase 4A.1):** readiness is validated when a milestone is selected, at submit/approve, and again,
  under a row lock, when the scheduler moves an occurrence `SCHEDULED -> TAX_PENDING`
  (`BillingOccurrenceTransactionServiceImpl`). An occurrence whose milestone is deleted / not billable / not
  `READY_FOR_BILLING`, or whose configuration is not APPROVED, is left SCHEDULED (no exception, retried each run). It is not
  re-checked later (tax calculation, invoice generation): from `TAX_PENDING` on the entry is frozen by design. What to do with an
  occurrence held for a long time (alert, cancel) is undecided - there is no cancel path.
* **Expiry (Phase 4B.5):** `EXPIRED` is only a `billingStatus` flip (`BillingConfigurationStatusScheduler`: project end date or
  `effectiveTo` passed); nothing touches occurrences. The claim queries already treat EXPIRED as finished, so for MILESTONES
  configurations an EXPIRED configuration now also stops progressing: the scheduler transition and the approval-time promotion
  leave its `SCHEDULED` occurrences alone (no cancellation; `TAX_PENDING` and later rows are untouched), and
  `update`/`delete` of an expired MILESTONES plan are refused (they would revive the configuration). At transition time an
  occurrence is also held while another live configuration holds, or has billed, the same milestone, so two configurations
  can never both invoice it (this also covers an expired configuration revived by a project end-date extension; both sides
  then wait for a human). **Unresolved policy:** for a Milestone Plan `effectiveTo` is otherwise unused for billing, yet it
  can expire the configuration before its last billing date; whether that should stop billing is a business decision. Other
  billing types and the other payment structures are unchanged.
* **Entry-level frozen state (Phase 4B.6):** each entry of a MILESTONES plan in the plan response (`get`, `getByBillingConfiguration`,
  `update`, and the configuration response) carries `frozen`, `occurrenceStatus` and `invoiced`, derived by the same
  `BillingMilestonePlanServiceImpl.billingStarted/singleDayOccurrences/isFrozen` that update/delete use (on locked rows there,
  plain reads here). No occurrence or `SCHEDULED` => `frozen=false`. The fields are null for FULL_PAYMENT / INSTALLMENTS entries
  and for the response of a plan that has just been created. They do not depend on PMS, so a milestone deleted in PMS does not hide them.
* **Revalidation and listing (Phase 4B.9):** `revalidateMilestoneClaims` (submit/approve) now takes the configuration's occurrence
  locks first and derives frozen entries with the same rule as update/delete (no plain `findEntriesInBilling` read), then locks the
  milestone rows, then refuses a milestone another configuration has billed or has billing under way for (expired ones
  included; the plan's own frozen entries are exempt), then the live-claim conflict check. `findMilestoneAssignmentRows` now follows
  the claim rule: an EXPIRED configuration's rows are listed only once billing has started (invoiced flag or TAX_PENDING /
  TAX_CALCULATED / INVOICED), so an expired unbilled configuration no longer reserves a milestone in the list. The frozen rule
  exists once in Java (`billingStarted` / `singleDayOccurrences` / `isFrozen`, status list `IN_BILLING_STATUSES`) and once as a shared
  JPQL definition (`OCCURRENCE_OF_ENTRY` + `BILLING_STARTED` constants in `BillingPaymentEntryRepository`, Phase 4B.10) used by
  `findEntriesInBilling`, `findBilledAssignments` and `findMilestoneAssignmentRows`; H2 parity tests classify every
  `BillingPeriodStatus` (and the inactive / other-day / multi-day cases) identically across Java and the three queries.
* **Effective start (Phase 4B.12):** for Milestone Plan configurations only, an unstarted `SCHEDULED` occurrence is held (no exception)
  by both the scheduler transition and the approval-time promotion when the configuration is `INACTIVE` or `EXPIRED`, has no
  effective-from date, or `today < effectiveFrom` (checked directly, so a lagging stored status cannot let it through). One
  shared helper, `BillingOccurrenceTransactionServiceImpl.milestoneNotYetBillableReason`. Billing dates earlier than
  effective-from are still accepted; such occurrences are held and then progress as overdue ones once the configuration starts
  (catch-up) - to be confirmed with the business owner.
* **Contract-wide cap:** not enforced; see the Phase 4A.1 review. `projectBudget` (PMS) is the only project-level value and no
  billing type is capped by it today.
* **Edit vs scheduler race (Phase 4A.2):** the transition re-reads its occurrence under a row lock and requires APPROVED. A
  MILESTONES plan `update`/`delete` first locks the configuration's active occurrences (`SELECT ... FOR UPDATE`, ascending id,
  `BillingScheduleRepository.findActiveByConfigurationForUpdate`) and decides "frozen" in Java from those locked rows (same
  billing date, `is_invoiced` or TAX_PENDING / TAX_CALCULATED / INVOICED). Lock order: occurrence rows, then milestone rows.
  Create, FULL_PAYMENT and INSTALLMENTS never take these locks. Verified with mocks and H2 only - not on MySQL.
* **Contract value:** there is no contract value independent of the plan (`totalContractValue`, copied to
  `billing_configuration.contract_value`). Two billing configurations of one project could each allocate 100%. Whether AR
  should cap across configurations needs a decision on the authoritative project contract value.
* **AR % vs PMS `allocationPercentage`:** still not required to match (PMS value is shown as reference only).
