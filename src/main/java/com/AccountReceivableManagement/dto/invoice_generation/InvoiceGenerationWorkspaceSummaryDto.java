package com.AccountReceivableManagement.dto.invoice_generation;

import lombok.*;

import java.math.BigDecimal;

/**
 * KPI data for the Invoice Generation workspace. Candidate amounts are kept
 * apart from invoiced amounts: a TAX_COMPLETED snapshot is never counted as
 * invoiced.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceGenerationWorkspaceSummaryDto {

    private long readyForInvoiceCount;

    /** Grand total (tax-inclusive) of all candidates; not an invoiced amount. */
    private BigDecimal readyForInvoiceAmount;

    private long generatedCount;

    private long pendingApprovalCount;

    private long approvedCount;

    private long rejectedCount;

    /** Number of invoices created, i.e. snapshots already INVOICED. */
    private long invoicedCount;

    /** Sum of grand totals of existing, non-rejected invoices. */
    private BigDecimal totalInvoicedAmount;
}
