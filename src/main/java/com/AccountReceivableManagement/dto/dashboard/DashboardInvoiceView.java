package com.AccountReceivableManagement.dto.dashboard;

import java.util.Locale;

/** Ledger filter for the dashboard invoice list. */
public enum DashboardInvoiceView {
    ALL,
    /** Sent, still carrying a balance, and past its due date. */
    OVERDUE,
    PENDING_APPROVAL,
    /** Approved but not yet sent to the client. */
    AWAITING_SEND,
    /** Sent to the client (a receivable exists). */
    SENT;

    /** Case-insensitive parse (dashes allowed); null or blank means ALL. */
    public static DashboardInvoiceView parse(String value) {
        if (value == null || value.isBlank()) return ALL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid view '" + value + "'. Use ALL, OVERDUE, PENDING_APPROVAL, AWAITING_SEND or SENT.");
        }
    }
}
