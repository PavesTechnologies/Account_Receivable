package com.AccountReceivableManagement.dto.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Epic 2's own consumer-side view of an approved timesheet entry, as
 * returned by {@code TimesheetIntegration}. Mirrors what the TMS exposes,
 * defined locally so Epic 2 never depends on TMS's internal DTOs.
 * {@code hourlyRate} is populated by the Strategy from the Billing
 * Configuration (Epic 1), not by the TMS integration — TMS owns
 * operational data only, never commercial data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetDto {

    private Long resourceId;

    private String resourceName;

    private String sourceReferenceId;

    private BigDecimal hours;

    private BigDecimal hourlyRate;

    private boolean approved;

    private boolean billable;
}
