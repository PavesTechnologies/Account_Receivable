package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingSnapshotService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceFinancialCorrectionService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates Phase 2B's "first mile" - see the interface Javadoc. Contains
 * no financial computation, no BillingSnapshot/TaxCalculation/Invoice field
 * mutation of its own: it only sequences three existing, otherwise-unmodified
 * capabilities -
 * {@link BillingSnapshotService#rebuildBillingSnapshot(UUID)},
 * {@link TaxCalculationService#calculateTax(UUID)}, and
 * {@link InvoiceService#refreshAfterCorrection(UUID)} - inside one
 * transaction, with the one piece of new logic in between (clearing the
 * snapshot's existing TaxCalculation) being pure preparation for the second
 * of those calls to succeed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceFinancialCorrectionServiceImpl implements InvoiceFinancialCorrectionService {

    private final InvoiceRepository invoiceRepository;

    private final InvoiceService invoiceService;

    private final BillingSnapshotService billingSnapshotService;

    private final TaxCalculationRepository taxCalculationRepository;

    private final TaxCalculationService taxCalculationService;

    @Override
    public InvoiceResponseDto reacquireForFinancialCorrection(UUID invoiceId) {

        /*
         * Row-level lock held for the rest of this transaction: two
         * concurrent financial-correction requests for the same invoice must
         * not both rebuild the same BillingSnapshot or race to insert a
         * TaxCalculation. The second request blocks here until the first
         * commits, then correctly observes correctionRequired == false below
         * and is rejected - no schema change, matching the project's
         * existing DB-constraint-based concurrency convention (see
         * TaxCalculation.billingSnapshotId's own unique-constraint backstop
         * inside TaxCalculationServiceImpl.calculateTax()).
         */
        Invoice invoice =
                invoiceRepository.findByIdForUpdate(invoiceId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler
                                        .ResourceNotFoundException(
                                        "Invoice could not be found."
                                )
                        );

        if (invoice.getStatus() != InvoiceStatus.REJECTED) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Only rejected invoices can undergo financial correction."
            );
        }

        if (!invoiceService.isCorrectionRequired(invoiceId)) {

            throw new GlobalExceptionHandler
                    .ValidationException(
                    "Invoice must have a pending correction before financial recalculation."
            );
        }

        UUID billingSnapshotId = invoice.getBillingSnapshotId();

        /*
         * Re-fetches authoritative source data (TMS, for Time & Material)
         * and rebuilds the SAME BillingSnapshot row in place - no new
         * snapshot, no project+period search; the invoice already names its
         * exact snapshot. Leaves the snapshot completely untouched if
         * acquisition validation fails.
         */
        BillingSnapshot rebuiltSnapshot =
                billingSnapshotService.rebuildBillingSnapshot(
                        billingSnapshotId
                );

        /*
         * The existing TaxCalculation is 1:1 with the snapshot and blocks
         * recalculation both by its own unique billingSnapshotId column and
         * by TaxCalculationServiceImpl.calculateTax()'s explicit
         * existsByBillingSnapshotId guard. Deleting it - cascading its
         * TaxCalculationComponent children via the existing
         * CascadeType.ALL/orphanRemoval mapping - is the only way to let the
         * existing, unmodified calculateTax() run again for this snapshot.
         * Flushed immediately so calculateTax()'s own existsByBillingSnapshotId
         * check (a fresh query, not a cached read) sees the deletion.
         */
        taxCalculationRepository
                .findByBillingSnapshotId(billingSnapshotId)
                .ifPresent(existing -> {
                    taxCalculationRepository.delete(existing);
                    taxCalculationRepository.flush();
                });

        /*
         * The existing, unmodified calculateTax(): now succeeds because the
         * snapshot is READY_FOR_TAX (set by rebuildBillingSnapshot) and no
         * TaxCalculation row remains for it. Transitions the snapshot to
         * TAX_COMPLETED on success, exactly as it always has for first-time
         * calculation.
         */
        taxCalculationService.calculateTax(rebuiltSnapshot.getId());

        /*
         * The existing, unmodified refreshAfterCorrection(): copies the
         * freshly corrected snapshot/tax figures onto the still-REJECTED
         * invoice, replaces its items/tax components, and records the single
         * CORRECTED approval-history entry. invoiceId, invoiceNumber, and
         * status are never touched by it - identical guarantee whether the
         * correction that fed it was financial (this flow) or non-financial
         * (Phase 2C).
         */
        return invoiceService.refreshAfterCorrection(invoiceId);
    }
}
