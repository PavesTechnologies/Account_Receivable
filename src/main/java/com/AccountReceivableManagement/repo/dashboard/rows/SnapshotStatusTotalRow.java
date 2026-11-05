package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Billing snapshot totals per status and currency, with the oldest snapshot's creation time. */
public record SnapshotStatusTotalRow(
        BillingSnapshotStatus status,
        String currencyCode,
        BigDecimal amount,
        Long count,
        LocalDateTime oldestCreated) {
}
