package com.AccountReceivableManagement.service_interface.software_invoice_integration;

import com.AccountReceivableManagement.dto.software_charge_generation.SoftwareChargeLineDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;

import java.util.List;

/**
 * Epic 4 (Revised Architecture) - Phase 5. Converts already-calculated
 * {@link SoftwareChargeLineDto}s (Phase 4) into {@link BillingSnapshotItem}s
 * for the existing Billing Data Acquisition / invoice generation pipeline.
 * Performs no recalculation and no duplicate checks - quantity, unit price
 * and amount are carried over from the Phase 4 charge line as-is.
 */
public interface SoftwareInvoiceIntegrationService {

    List<BillingSnapshotItem> toInvoiceLines(List<SoftwareChargeLineDto> softwareCharges);
}
