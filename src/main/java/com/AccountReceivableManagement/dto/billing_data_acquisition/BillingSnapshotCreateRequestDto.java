package com.AccountReceivableManagement.dto.billing_data_acquisition;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /billing-snapshots} — the minimal input
 * needed to start Billing Data Acquisition for one project and one
 * billing period.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshotCreateRequestDto {

    @NotNull(message = "Project is required.")
    private Long projectId;

    /**
     * Unique identifier of the Billing Configuration selected in Project Billing Setup.
     */
    private UUID billingConfigurationId;

    @NotNull(message = "Billing period start date is required.")
    private LocalDate billingPeriodStart;

    @NotNull(message = "Billing period end date is required.")
    private LocalDate billingPeriodEnd;
}
