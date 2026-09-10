package com.AccountReceivableManagement.service_interface.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalHistoryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalWorkspaceResponseDto;
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

    InvoiceResponseDto getInvoiceByBillingSnapshotId(UUID billingSnapshotId);

    /**
     * Lists every already-generated invoice, most recent first, for the
     * Invoice Generation workspace. Purely read-only - generates nothing.
     */
    List<InvoiceSummaryResponseDto> getAllInvoices();

    /**
     * {@code GENERATED -> PENDING_APPROVAL}. Only the status changes and an
     * approval-history entry is recorded; every financial field, the
     * BillingSnapshot, and the TaxCalculation are left untouched.
     */
    InvoiceResponseDto submitForApproval(UUID invoiceId);

    /**
     * {@code PENDING_APPROVAL -> APPROVED}. Only the status changes and an
     * approval-history entry is recorded; every financial field, the
     * BillingSnapshot, and the TaxCalculation are left untouched.
     */
    InvoiceResponseDto approveInvoice(UUID invoiceId);

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
