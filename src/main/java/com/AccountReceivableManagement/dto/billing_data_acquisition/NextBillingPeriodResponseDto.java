package com.AccountReceivableManagement.dto.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response of {@code GET /api/billing-data-acquisition/configurations/{id}/next-period}: the single billing
 * period the Finance Executive should acquire next, derived from period discovery. Read-only. Counts this
 * endpoint cannot determine are null, never guessed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextBillingPeriodResponseDto {

    private Long projectId;
    private UUID billingConfigurationId;
    private String billingFrequency;
    private LocalDate effectiveFrom;

    /** Null when the schedule has no period that still needs acquiring. */
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private boolean partialPeriod;
    private boolean periodEnded;

    /** True only for an ended period whose newly eligible records all pass validation. */
    private boolean acquisitionAllowed;
    /** The period already has a live batch that has been invoiced. */
    private boolean alreadyInvoiced;

    private Integer returnedRecordCount;
    private Integer eligibleRecordCount;
    private BigDecimal eligibleHours;
    private Integer alreadyClaimedCount;
    private Integer notApprovedCount;
    private Integer invalidHoursCount;
    @Builder.Default
    private List<String> alreadyClaimedTimesheetIds = new ArrayList<>();

    /** The most recent live batch of an earlier period, if any. */
    private UUID previousSnapshotId;
    private LocalDate previousPeriodStart;
    private LocalDate previousPeriodEnd;
    private String previousSnapshotStatus;
    private boolean previousPeriodInvoiced;

    private String reason;

    /** Earlier periods with unbilled records that cannot be acquired now; they do not block this period. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
