package com.AccountReceivableManagement.service_interface.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;

import java.util.UUID;

/**
 * Phase 2B's "first mile": orchestrates re-fetching authoritative source
 * data, rebuilding a REJECTED invoice's existing BillingSnapshot in place,
 * and recalculating its TaxCalculation, so that the existing, unmodified
 * {@code InvoiceServiceImpl.refreshAfterCorrection(UUID)} has genuinely
 * corrected upstream data to copy onto the Invoice. Distinct from Phase 2C's
 * {@code InvoiceService.correctNonFinancialFields(UUID,
 * InvoiceNonFinancialCorrectionRequestDto)} - that workflow edits
 * {@code clientName}/{@code projectName} directly; this one never edits any
 * Invoice field itself, only supplies corrected authoritative data for the
 * existing refresh to copy.
 */
public interface InvoiceFinancialCorrectionService {

    /**
     * Re-acquires authoritative source data (TMS, for Time &amp; Material)
     * for a {@code REJECTED} invoice's existing BillingSnapshot, rebuilds
     * that snapshot in place, recalculates its TaxCalculation, and calls the
     * existing {@code refreshAfterCorrection(UUID)} to propagate the
     * corrected figures onto the invoice. The invoice stays {@code REJECTED}
     * - the caller must still explicitly resubmit. Requires the invoice to
     * currently be {@code REJECTED} with a pending correction
     * ({@code correctionRequired == true}); blocked otherwise. No BillingSnapshot,
     * TaxCalculation, or Invoice financial field is ever set directly by this
     * method - every commercial figure originates from
     * {@code BillingSnapshotService.rebuildBillingSnapshot(UUID)} and the
     * existing, unmodified {@code TaxCalculationService.calculateTax(UUID)}.
     */
    InvoiceResponseDto reacquireForFinancialCorrection(UUID invoiceId);
}
