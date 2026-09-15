package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/invoices/approval-workspace} - every
 * invoice that has entered the approval workflow (PENDING_APPROVAL,
 * APPROVED, or REJECTED), so the workspace keeps showing history after an
 * invoice is approved rather than emptying out. Copied directly from the
 * persisted Invoice and its InvoiceApprovalHistory; nothing recalculated.
 * KPIs (total invoices, pending/approved/rejected counts, total approval
 * value) are derivable client-side from this list's {@code status} and
 * {@code grandTotal} - no separate aggregation endpoint is needed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceApprovalWorkspaceResponseDto {

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

    private InvoiceApprovalAction lastAction;

    private LocalDateTime lastActionAt;

    /**
     * {@code true} only when {@code status == REJECTED} and no correction
     * refresh has occurred since the latest rejection. Always {@code false}
     * for every other status. Derived from the already bulk-fetched
     * {@code InvoiceApprovalHistory} - no extra per-invoice query.
     */
    private boolean correctionRequired;

    /**
     * Timestamp of the most recent {@code CORRECTED} approval-history entry,
     * or {@code null} if this invoice has never been refreshed after a
     * rejection.
     */
    private LocalDateTime lastCorrectedAt;
}
