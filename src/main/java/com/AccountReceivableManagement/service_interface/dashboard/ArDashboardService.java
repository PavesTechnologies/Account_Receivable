package com.AccountReceivableManagement.service_interface.dashboard;

import com.AccountReceivableManagement.dto.dashboard.ArDashboardSummaryDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceRowDto;
import com.AccountReceivableManagement.dto.dashboard.DashboardInvoiceView;
import com.AccountReceivableManagement.dto.dashboard.DashboardPeriod;

import java.time.LocalDate;
import java.util.List;

/** Read-only aggregations for the AR dashboard. */
public interface ArDashboardService {

    /**
     * KPIs, billing-cycle pipeline, trend, aging, billing-model mix, worklist
     * and recent activity.
     *
     * @param asOf reference date; null means today
     */
    ArDashboardSummaryDto getSummary(DashboardPeriod period, LocalDate asOf);

    /**
     * One page of the dashboard ledger, soonest due first.
     *
     * @param search free text matched against invoice number, client and project; null or blank for none
     * @param limit  rows to return; null means the configured default, capped at the configured maximum
     * @param asOf   reference date for overdue/aging; null means today
     */
    List<DashboardInvoiceRowDto> getInvoices(DashboardInvoiceView view, String search, Integer limit, LocalDate asOf);
}
