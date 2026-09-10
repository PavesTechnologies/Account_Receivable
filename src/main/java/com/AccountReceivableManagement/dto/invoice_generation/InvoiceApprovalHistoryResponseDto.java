package com.AccountReceivableManagement.dto.invoice_generation;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row of {@code GET /api/v1/invoices/{invoiceId}/approval-history}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceApprovalHistoryResponseDto {

    private InvoiceApprovalAction action;

    private InvoiceStatus previousStatus;

    private InvoiceStatus newStatus;

    private String actionBy;

    private LocalDateTime actionAt;

    private String comment;
}
