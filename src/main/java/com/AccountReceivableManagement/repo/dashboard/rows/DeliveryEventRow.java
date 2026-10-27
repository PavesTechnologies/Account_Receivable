package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;

import java.time.LocalDateTime;

/** One invoice delivery attempt with its invoice number. */
public record DeliveryEventRow(
        InvoiceDeliveryStatus status,
        String invoiceNumber,
        LocalDateTime sentAt,
        LocalDateTime failedAt,
        LocalDateTime updatedAt) {
}
