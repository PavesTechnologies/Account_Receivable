package com.AccountReceivableManagement.service_Imple.dashboard;

import com.AccountReceivableManagement.dto.dashboard.DashboardPeriod;

import java.time.LocalDate;

/**
 * A period-to-date window ending on {@code asOf}, and the same span one period
 * earlier. Example: MTD as of 22 Oct = 1–22 Oct, prior = 1–22 Sep. Month-end
 * clamping follows {@link LocalDate#minusMonths} (31 Mar → 28/29 Feb).
 */
record PeriodWindow(LocalDate start, LocalDate end, LocalDate priorStart, LocalDate priorEnd) {

    static PeriodWindow of(DashboardPeriod period, LocalDate asOf) {
        LocalDate start = switch (period) {
            case MTD -> asOf.withDayOfMonth(1);
            case QTD -> LocalDate.of(asOf.getYear(), ((asOf.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YTD -> asOf.withDayOfYear(1);
        };
        int months = period.months();
        return new PeriodWindow(start, asOf, start.minusMonths(months), asOf.minusMonths(months));
    }
}
