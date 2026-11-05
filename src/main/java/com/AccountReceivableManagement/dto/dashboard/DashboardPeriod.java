package com.AccountReceivableManagement.dto.dashboard;

import java.util.Locale;

/** Reporting window for period-based dashboard figures, always ending on the as-of date. */
public enum DashboardPeriod {
    MTD(1),
    QTD(3),
    YTD(12);

    private final int months;

    DashboardPeriod(int months) {
        this.months = months;
    }

    /** Length of the window in months; the prior window is shifted back by this much. */
    public int months() {
        return months;
    }

    /** Case-insensitive parse; null or blank means MTD. */
    public static DashboardPeriod parse(String value) {
        if (value == null || value.isBlank()) return MTD;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid period '" + value + "'. Use MTD, QTD or YTD.");
        }
    }
}
