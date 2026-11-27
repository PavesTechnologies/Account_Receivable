package com.AccountReceivableManagement.dto.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Response of {@code GET /api/billing-data-acquisition/configurations/{id}/periods}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPeriodDiscoveryResponseDto {

    private UUID billingConfigurationId;
    private Long projectId;
    private String billingFrequency;
    /** First day of the eligible billing range (the configuration's Effective From). */
    private LocalDate scheduleStart;
    /**
     * Last day of the returned periods: the end of the billing range (earlier of Effective To and project
     * end), or - for an open-ended schedule - the end of the period containing {@code asOf}.
     */
    private LocalDate scheduleEnd;
    private boolean expenseBillingEnabled;
    private LocalDate asOf;

    /** Configuration-level problems (e.g. a source system unavailable); per-period ones are on each period. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<BillingPeriodAcquisitionDto> periods = new ArrayList<>();
}
