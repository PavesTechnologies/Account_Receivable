package com.AccountReceivableManagement.service_interface.invoice_generation;

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
}
