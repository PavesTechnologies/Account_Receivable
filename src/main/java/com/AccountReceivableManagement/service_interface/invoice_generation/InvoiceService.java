package com.AccountReceivableManagement.service_interface.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalHistoryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalWorkspaceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceNonFinancialCorrectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceRejectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceSummaryResponseDto;

import java.util.List;
import java.util.UUID;

public interface InvoiceService {

    /**
     * Generates the frozen invoice for an already tax-completed billing
     * snapshot and persists it. The snapshot must be in
     * {@code TAX_COMPLETED} status and must not already have an invoice; on
     * success the snapshot transitions to {@code INVOICED}. No tax is
     * recalculated - the persisted {@code TaxCalculation} is copied as-is.
     */
    InvoiceResponseDto generateInvoice(UUID billingSnapshotId);

    InvoiceResponseDto generateInvoiceForSchedule(UUID billingScheduleId);

    InvoiceResponseDto getInvoiceByBillingSnapshotId(UUID billingSnapshotId);

    /**
     * Lists every already-generated invoice, most recent first, for the
     * Invoice Generation workspace. Purely read-only - generates nothing.
     */
    List<InvoiceSummaryResponseDto> getAllInvoices();

    /**
     * {@code GENERATED -> PENDING_APPROVAL}, or {@code REJECTED ->
     * PENDING_APPROVAL} for a corrected, resubmitted invoice. Only the
     * status changes and an approval-history entry is recorded; every
     * financial field, the BillingSnapshot, and the TaxCalculation are left
     * untouched.
     */
    InvoiceResponseDto submitForApproval(UUID invoiceId);

    /**
     * {@code PENDING_APPROVAL -> APPROVED}. Only the status changes and an
     * approval-history entry is recorded; every financial field, the
     * BillingSnapshot, and the TaxCalculation are left untouched.
     */
    InvoiceResponseDto approveInvoice(UUID invoiceId);

    /**
     * {@code PENDING_APPROVAL -> REJECTED}. The rejection reason is
     * mandatory and is stored verbatim as the {@code comment} of the
     * recorded {@code REJECTED} approval-history entry; every financial
     * field, the BillingSnapshot, and the TaxCalculation are left untouched.
     */
    InvoiceResponseDto rejectInvoice(UUID invoiceId, InvoiceRejectionRequestDto request);

    /**
     * Refreshes a {@code REJECTED} invoice's frozen financial snapshot -
     * billing period, currency, payment term, subtotal, total tax, grand
     * total, line items, and tax components - from the latest authoritative
     * {@code BillingSnapshot}/{@code TaxCalculation}. Not a generic invoice
     * editor: no amount is ever supplied by the caller, only re-copied
     * as-is from persisted upstream data, exactly like
     * {@link #generateInvoice(UUID)}. {@code invoiceId} and
     * {@code invoiceNumber} are preserved and {@code status} remains
     * {@code REJECTED} - the caller must still explicitly call
     * {@link #submitForApproval(UUID)} to resubmit. Records a
     * {@code CORRECTED} ({@code REJECTED -> REJECTED}) approval-history
     * entry so {@link #submitForApproval(UUID)} can verify a correction
     * occurred after the latest rejection before allowing resubmission.
     */
    InvoiceResponseDto refreshAfterCorrection(UUID invoiceId);

    /**
     * Phase 2C - corrects only {@code clientName} and {@code projectName} on
     * a {@code REJECTED} invoice; every financial field, the
     * BillingSnapshot, and the TaxCalculation are left untouched. Not a
     * generic invoice editor: no other field is accepted. {@code invoiceId},
     * {@code invoiceNumber}, and {@code status} are preserved and
     * {@code status} remains {@code REJECTED} - the caller must still
     * explicitly call {@link #submitForApproval(UUID)} to resubmit. Records
     * a {@code CORRECTED} ({@code REJECTED -> REJECTED}) approval-history
     * entry, the same action used by {@link #refreshAfterCorrection(UUID)},
     * so {@link #submitForApproval(UUID)} can verify a correction occurred
     * after the latest rejection before allowing resubmission. Blocked when
     * the invoice is not currently {@code REJECTED}, or when it is
     * {@code REJECTED} but already corrected since the latest rejection
     * (i.e. {@code correctionRequired} is already {@code false}).
     */
    InvoiceResponseDto correctNonFinancialFields(
            UUID invoiceId,
            InvoiceNonFinancialCorrectionRequestDto request
    );

    /**
     * {@code true} only when {@code invoiceId}'s current status is
     * {@code REJECTED} and no {@code CORRECTED} approval-history entry has
     * been recorded since its latest {@code REJECTED} entry - the same
     * derivation {@link #submitForApproval(UUID)} and
     * {@link #correctNonFinancialFields(UUID, InvoiceNonFinancialCorrectionRequestDto)}
     * already enforce. Exposed so other correction-workflow orchestrators
     * (e.g. Phase 2B financial-correction/reacquisition) can apply the same
     * "already corrected since the latest rejection" guard without
     * duplicating the history-based computation.
     */
    boolean isCorrectionRequired(UUID invoiceId);

    List<InvoiceApprovalHistoryResponseDto> getApprovalHistory(UUID invoiceId);

    /**
     * Lists every invoice currently {@code PENDING_APPROVAL}, for the
     * Invoice Approval workspace queue. Purely read-only.
     */
    List<InvoiceApprovalSummaryResponseDto> getPendingApprovalInvoices();

    /**
     * Lists every invoice that has entered the approval workflow -
     * currently {@code PENDING_APPROVAL}, {@code APPROVED}, or
     * {@code REJECTED} - so the Invoice Approval Dashboard keeps showing
     * history after an invoice leaves the pending queue, instead of
     * emptying out. {@code InvoiceApprovalHistory} is the source of truth
     * for workflow membership. Purely read-only.
     */
    List<InvoiceApprovalWorkspaceResponseDto> getApprovalWorkspaceInvoices();
}
