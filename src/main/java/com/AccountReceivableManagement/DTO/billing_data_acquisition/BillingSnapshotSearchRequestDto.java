package com.AccountReceivableManagement.DTO.billing_data_acquisition;

import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingSnapshotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filter criteria for {@code GET /billing-snapshots}. Every field
 * is optional; an all-null instance matches every snapshot.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshotSearchRequestDto {

    /**
     * Restrict results to this project, if supplied.
     */
    private Long projectId;

    /**
     * Restrict results to this client, if supplied.
     */
    private UUID clientId;

    /**
     * Restrict results to snapshots in this lifecycle status, if supplied.
     */
    private BillingSnapshotStatus status;

    /**
     * Restrict results to snapshots whose billing period starts on or
     * after this date, if supplied.
     */
    private LocalDate billingPeriodStart;

    /**
     * Restrict results to snapshots whose billing period ends on or
     * before this date, if supplied.
     */
    private LocalDate billingPeriodEnd;
}
