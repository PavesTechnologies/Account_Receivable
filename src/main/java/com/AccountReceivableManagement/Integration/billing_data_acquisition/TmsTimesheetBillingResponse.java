package com.AccountReceivableManagement.Integration.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Raw shape of the TMS {@code GET /tms/api/timesheets/billing} response.
 * Deserialization target only — never leaves the Integration layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmsTimesheetBillingResponse {

    private Long projectId;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private List<TmsTimesheetEntry> timesheets;
}
