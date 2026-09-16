package com.AccountReceivableManagement.service_interface.tax_calculation;

import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;

import java.util.UUID;

public interface TaxCalculationService {

    /**
     * Calculates tax for the given, already-acquired billing snapshot and
     * persists the result. The snapshot must be in {@code READY_TO_TAX}
     * status; on success the snapshot transitions to {@code TAX_COMPLETED}.
     */
    TaxCalculationResponseDto calculateTax(UUID billingSnapshotId);

    /**
     * Calculates tax for the given billing schedule (Fixed Price or Recurring)
     * and persists the result. The schedule must be in {@code TAX_PENDING}
     * status; on success the schedule transitions to {@code TAX_CALCULATED}.
     */
    TaxCalculationResponseDto calculateTaxForSchedule(UUID billingScheduleId);

    TaxCalculationResponseDto getTaxCalculationBySnapshotId(UUID billingSnapshotId);

    TaxCalculationResponseDto getTaxCalculationByScheduleId(UUID billingScheduleId);
}
