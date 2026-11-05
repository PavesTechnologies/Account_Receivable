package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One invoice for the dashboard ledger. Receivable fields are null until the
 * invoice is sent (a receivable row is created only on a successful send).
 */
public record LedgerRow(
        UUID invoiceId,
        String invoiceNumber,
        String clientName,
        String projectName,
        String billingType,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currencyCode,
        BigDecimal subtotal,
        BigDecimal totalTaxAmount,
        BigDecimal grandTotal,
        InvoiceStatus status,
        UUID receivableId,
        BigDecimal outstandingBalance,
        LocalDate receivableDueDate) {
}
