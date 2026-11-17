package com.AccountReceivableManagement.entity_enums.operations;

import lombok.Getter;

/**
 * Financial reconciliation checks over the persisted billing chain
 * (source → snapshot/occurrence → tax calculation → invoice → document →
 * delivery → receivable, and the automatic billing run that links them).
 * Each check only reads; a finding is reported for investigation and
 * authorized correction, never corrected automatically. Severity, impact,
 * owner and corrective action are the exception-management defaults of
 * {@code OPERATIONS_MONITORING_AND_RECONCILIATION.md}.
 */
@Getter
public enum ReconciliationCheck {

    // ---------------------------------------------------------------- source amounts

    INVOICE_SOURCE_MISSING(Severity.HIGH, RecordType.INVOICE,
            "Invoice cannot be traced to a billing occurrence or snapshot - its amount is unverifiable.",
            Owner.AR_ENGINEERING,
            "Identify how the invoice was created; restore or document the source link. Do not send until traced."),
    OCCURRENCE_AMOUNT_MISMATCH(Severity.HIGH, RecordType.INVOICE,
            "Invoice subtotal differs from its billing occurrence amount - client billed a different amount.",
            Owner.FINANCE,
            "If not yet sent: reject and correct. If sent: raise a credit/debit note through the correction process."),
    SNAPSHOT_AMOUNT_MISMATCH(Severity.HIGH, RecordType.INVOICE,
            "Invoice subtotal differs from its billing snapshot total - client billed a different amount.",
            Owner.FINANCE,
            "If not yet sent: reject and run financial correction. If sent: correction process."),
    SNAPSHOT_ITEMS_MISMATCH(Severity.HIGH, RecordType.BILLING_SNAPSHOT,
            "Snapshot lines do not add up to the snapshot total that was billed.",
            Owner.AR_ENGINEERING,
            "Compare snapshot lines with TMS timesheets; a difference of a paisa per line is the pre-UAT-D2 "
                    + "rounding pattern (MEDIUM)."),

    // ---------------------------------------------------------------- invoice totals and tax

    INVOICE_LINES_MISMATCH(Severity.HIGH, RecordType.INVOICE,
            "Invoice line items do not add up to the subtotal (missing or incorrect lines).",
            Owner.FINANCE,
            "Do not approve/send. Reject and correct; a paisa per line is the pre-UAT-D2 rounding pattern (MEDIUM)."),
    INVOICE_TOTAL_MISMATCH(Severity.CRITICAL, RecordType.INVOICE,
            "Grand total is not subtotal + tax, or a total is missing.",
            Owner.AR_ENGINEERING,
            "Stop sending this invoice; investigate the generating code path; correct through the correction process."),
    INVOICE_TAX_BREAKDOWN_MISMATCH(Severity.HIGH, RecordType.INVOICE,
            "Tax components do not add up to the invoice's total tax.",
            Owner.FINANCE,
            "Tax review with Finance; reject and correct if not yet sent."),
    INVOICE_TAX_CALCULATION_MISMATCH(Severity.HIGH, RecordType.INVOICE,
            "Invoice totals differ from its tax calculation, or the tax calculation is missing.",
            Owner.FINANCE,
            "Tax review: compare GET /api/v1/invoices/{id} with the occurrence/snapshot tax calculation."),

    // ---------------------------------------------------------------- invoice ↔ occurrence ↔ run

    DUPLICATE_INVOICE_FOR_SOURCE(Severity.CRITICAL, RecordType.BILLING_OCCURRENCE,
            "More than one invoice for the same billing occurrence or snapshot - duplicate billing.",
            Owner.AR_ENGINEERING,
            "Pause automatic billing; verify uk_invoice_billing_schedule/_snapshot exist (runbook §7); "
                    + "Finance decides which invoice to cancel."),
    OCCURRENCE_MISSING(Severity.HIGH, RecordType.INVOICE,
            "Invoice references a billing occurrence that no longer exists.",
            Owner.AR_ENGINEERING,
            "Find the change that removed the occurrence (configuration reconciliation); restore traceability."),
    OCCURRENCE_INVOICED_WITHOUT_INVOICE(Severity.MEDIUM, RecordType.BILLING_OCCURRENCE,
            "Occurrence is marked invoiced but no invoice exists - the period would never be billed.",
            Owner.AR_ENGINEERING,
            "Confirm with Finance whether it was billed outside AR; otherwise correct the occurrence (audited)."),
    OCCURRENCE_NOT_MARKED_INVOICED(Severity.MEDIUM, RecordType.INVOICE,
            "Invoice exists but its occurrence is not marked invoiced - risk of billing the period again.",
            Owner.AR_ENGINEERING,
            "Correct the occurrence flag (audited); the unique key still prevents a second invoice."),
    RUN_WITHOUT_INVOICE(Severity.HIGH, RecordType.AUTOMATIC_BILLING_RUN,
            "Run reports PENDING_APPROVAL/COMPLETED but its invoice is missing.",
            Owner.AR_ENGINEERING,
            "Investigate the run's steps and logs ([run=<id>]); do not re-queue before the invoice is located."),
    RUN_INVOICE_OCCURRENCE_MISMATCH(Severity.HIGH, RecordType.AUTOMATIC_BILLING_RUN,
            "Run's invoice belongs to a different occurrence than the run.",
            Owner.AR_ENGINEERING,
            "Investigate; Finance holds approval of the affected invoice."),
    INVOICE_LINKED_TO_MULTIPLE_RUNS(Severity.MEDIUM, RecordType.INVOICE,
            "Several runs claim the same invoice.",
            Owner.AR_ENGINEERING,
            "Investigate the runs; check uk_automatic_billing_run_* exist."),
    OCCURRENCE_OVERDUE_WITHOUT_RUN(Severity.MEDIUM, RecordType.BILLING_OCCURRENCE,
            "Due occurrence of an automatic configuration past the grace period with no run and no failure "
                    + "reason - billing is late.",
            Owner.AR_SUPPORT,
            "Check scheduler state (enabled, ticks, backfill window); runbook §5."),

    // ---------------------------------------------------------------- approval, document, delivery

    READY_TO_SEND_WITHOUT_DOCUMENT(Severity.HIGH, RecordType.INVOICE,
            "Invoice is READY_TO_SEND but has no finalized document.",
            Owner.AR_ENGINEERING,
            "Do not send; regenerate the document (POST /api/v1/invoices/{id}/document) after investigation."),
    APPROVED_WITHOUT_DOCUMENT(Severity.MEDIUM, RecordType.INVOICE,
            "Approved invoice has no finalized document (document generation failed).",
            Owner.FINANCE,
            "Fix the reported data, then POST /api/v1/invoices/{id}/document."),
    DOCUMENT_FOR_UNAPPROVED_INVOICE(Severity.HIGH, RecordType.INVOICE,
            "A finalized document exists for an invoice that is not approved.",
            Owner.AR_ENGINEERING,
            "Investigate how the document was created; the document must not be sent."),
    SENT_INVOICE_NOT_APPROVED(Severity.CRITICAL, RecordType.INVOICE,
            "Invoice was sent to the client but is not in an approved status.",
            Owner.FINANCE,
            "Finance Manager reviews the sent invoice; correction process if wrong."),

    // ---------------------------------------------------------------- receivables

    SENT_WITHOUT_RECEIVABLE(Severity.HIGH, RecordType.INVOICE,
            "Invoice was sent but no receivable exists - revenue not tracked for collection.",
            Owner.AR_SUPPORT,
            "Recover with ReceivableService.createReceivableFromInvoice (runbook §5). Do not resend the invoice."),
    RECEIVABLE_WITHOUT_SENT_DELIVERY(Severity.HIGH, RecordType.RECEIVABLE,
            "Receivable exists for an invoice that was never successfully sent.",
            Owner.FINANCE,
            "Confirm whether the client received the invoice; authorized correction of the receivable."),
    RECEIVABLE_AMOUNT_MISMATCH(Severity.CRITICAL, RecordType.RECEIVABLE,
            "Receivable amount differs from the invoice grand total.",
            Owner.FINANCE,
            "Authorized correction of the receivable; never edit balances to force a match."),
    RECEIVABLE_BALANCE_UNEXPECTED(Severity.HIGH, RecordType.RECEIVABLE,
            "Outstanding balance differs from the invoice amount although no payment process exists yet, "
                    + "or exceeds it.",
            Owner.FINANCE,
            "Investigate who changed the balance (audit); authorized correction."),
    DUPLICATE_RECEIVABLE(Severity.CRITICAL, RecordType.INVOICE,
            "More than one receivable for the same invoice - amount owed counted twice.",
            Owner.AR_ENGINEERING,
            "Verify uk_receivable_invoice exists (runbook §7); Finance decides the correction.");

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public enum RecordType { INVOICE, BILLING_OCCURRENCE, BILLING_SNAPSHOT, AUTOMATIC_BILLING_RUN, RECEIVABLE }

    /** Default owner role (see the ownership table of the procedures document). */
    public enum Owner { FINANCE, AR_SUPPORT, AR_ENGINEERING }

    private final Severity severity;
    private final RecordType recordType;
    private final String businessImpact;
    private final Owner owner;
    private final String correctiveAction;

    ReconciliationCheck(Severity severity, RecordType recordType, String businessImpact, Owner owner,
            String correctiveAction) {
        this.severity = severity;
        this.recordType = recordType;
        this.businessImpact = businessImpact;
        this.owner = owner;
        this.correctiveAction = correctiveAction;
    }
}
