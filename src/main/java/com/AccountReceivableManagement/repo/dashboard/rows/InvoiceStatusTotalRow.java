package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;

import java.math.BigDecimal;

/** Invoice grand totals grouped by status and currency. */
public record InvoiceStatusTotalRow(InvoiceStatus status, String currencyCode, BigDecimal amount, Long count) {
}
