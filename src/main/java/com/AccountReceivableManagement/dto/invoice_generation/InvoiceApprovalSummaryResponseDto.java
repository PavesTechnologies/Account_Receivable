package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/invoices/pending-approval} - the Invoice
 * Approval workspace queue. Copied directly from the persisted Invoice (and
 * its latest SUBMITTED history entry); nothing is recalculated.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceApprovalSummaryResponseDto {

    private UUID invoiceId;

    private String invoiceNumber;

    private InvoiceStatus status;

    private UUID billingSnapshotId;

    private String billingSnapshotNumber;

    private String clientName;

    private String projectName;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private String currencyCode;

    private BigDecimal grandTotal;

    private LocalDateTime submittedAt;

    private String submittedBy;
}
