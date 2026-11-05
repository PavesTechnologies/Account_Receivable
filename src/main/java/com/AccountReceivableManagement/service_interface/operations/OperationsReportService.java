package com.AccountReceivableManagement.service_interface.operations;

import com.AccountReceivableManagement.dto.operations.BillingOperationsSummaryDto;
import com.AccountReceivableManagement.dto.operations.OperationsAlertDto;
import com.AccountReceivableManagement.dto.operations.ReconciliationReportDto;

import java.util.List;

/**
 * Read-only operations reporting over persisted billing records: an
 * execution/business summary, financial reconciliation and evaluation of the
 * configured alert thresholds. Never writes and never corrects a record.
 */
public interface OperationsReportService {

    /** @param windowDays days of run history to break down by creation date (1-366) */
    BillingOperationsSummaryDto getSummary(int windowDays);

    ReconciliationReportDto reconcile();

    /** Every configured condition with its current value; {@code raised} when above its threshold. */
    List<OperationsAlertDto> evaluateAlerts();
}
