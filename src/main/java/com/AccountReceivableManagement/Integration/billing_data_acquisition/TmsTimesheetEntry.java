package com.AccountReceivableManagement.Integration.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Raw shape of one entry in the TMS billing-timesheets response.
 * Deserialization target only — never leaves the Integration layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmsTimesheetEntry {

    private String timesheetId;

    private Long resourceId;

    private String resourceName;

    private BigDecimal hours;
}
