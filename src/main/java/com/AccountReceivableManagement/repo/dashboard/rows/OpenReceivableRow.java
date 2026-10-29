package com.AccountReceivableManagement.repo.dashboard.rows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A receivable that still carries an outstanding balance. */
public record OpenReceivableRow(
        UUID invoiceId,
        String invoiceNumber,
        String clientName,
        String currencyCode,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal outstandingBalance) {
}
