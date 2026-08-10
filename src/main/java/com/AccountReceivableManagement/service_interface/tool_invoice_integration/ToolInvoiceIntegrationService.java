package com.AccountReceivableManagement.service_interface.tool_invoice_integration;

import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargePreviewDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;

import java.util.List;

/**
 * Epic 4 - Tool / Software / License Billing (Phase 5, Story 4.4).
 * Converts already-calculated {@link ToolChargePreviewDto}s (Phase 4) into
 * {@link BillingSnapshotItem}s for the existing Billing Data Acquisition /
 * invoice generation pipeline. Performs no recalculation - quantity, unit
 * price and amount are carried over from the Phase 4 preview as-is.
 */
public interface ToolInvoiceIntegrationService {

    List<BillingSnapshotItem> toInvoiceLines(List<ToolChargePreviewDto> toolCharges);
}
