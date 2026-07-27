package com.AccountReceivableManagement.DTO.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Neutral result of a {@code BillingAcquisitionStrategy}'s data acquisition,
 * shielding the Strategy contract from the differing data shapes each
 * billing type acquires. Story 2.1 (Time &amp; Material) populates only
 * {@code timesheets}; later stories add their own field here rather than
 * changing the Strategy interface.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAcquisitionResultDto {

    @Builder.Default
    private List<TimesheetDto> timesheets = new ArrayList<>();
}
