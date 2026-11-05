package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceApprovalAction;

import java.time.LocalDateTime;

/** One invoice approval-history entry with its invoice number. */
public record ApprovalEventRow(InvoiceApprovalAction action, String invoiceNumber, String actionBy, LocalDateTime actionAt) {
}
