package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAcquisitionRequestDto {

    @NotNull(message = "billingConfigurationId is required")
    private UUID billingConfigurationId;

    @NotNull(message = "billingPeriodStart is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodStart;

    @NotNull(message = "billingPeriodEnd is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodEnd;

    /** System-generated snapshot reference from POST /billing-snapshots */
    @NotNull(message = "Snapshot acquisition must complete successfully before recording acquisition.")
    private UUID snapshotId;

    /** Acquisition status determined by the snapshot engine (e.g. READY | PARTIALLY_READY) */
    private String status;
}
