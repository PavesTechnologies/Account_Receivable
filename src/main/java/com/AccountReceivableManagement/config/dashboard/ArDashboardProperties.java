package com.AccountReceivableManagement.config.dashboard;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the AR dashboard ({@code ar.dashboard.*}). Every value has a
 * default here, so no property needs to be set.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ar.dashboard")
public class ArDashboardProperties {

    /** Overdue receivables older than this many days past due are "critical". */
    private int criticalOverdueDays = 60;

    /** Calendar months in the invoicing trend, including the current one. */
    private int trendMonths = 6;

    /** Events returned in the recent-activity feed. */
    private int activityLimit = 10;

    /** Ledger rows returned when no limit is given. */
    private int ledgerDefaultLimit = 50;

    /** Upper bound on the ledger limit a caller may request. */
    private int ledgerMaxLimit = 200;
}
