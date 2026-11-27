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

/**
 * One billing period of a Time &amp; Material configuration as seen by Data Acquisition: what is newly
 * billable in it (approved, billable records no live snapshot claims yet), whether that can be acquired
 * now, and the batches/invoices it already has.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPeriodAcquisitionDto {

    private Integer periodNumber;
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private LocalDate billingDate;
    /** First or last period cut short by the schedule's start or end. */
    private boolean partialPeriod;
    /** The period's billing date has passed (it is due). An open period can still be acquired. */
    private boolean periodEnded;

    /** Newly eligible (unclaimed) timesheets; null when TMS could not be read. */
    private Integer newTimesheetCount;
    private BigDecimal newBillableHours;
    /** Hours x net rate of the newly eligible, invoice-included timesheets; null when not priced. */
    private BigDecimal estimatedTimesheetAmount;
    /** Newly eligible (unclaimed) expenses; 0 when expense billing is off, null when XMS could not be read. */
    private Integer newExpenseCount;
    private BigDecimal newExpenseAmount;

    private boolean hasNewItems;
    /** The period already has at least one live (non-cancelled) batch. */
    private boolean previouslyAcquired;
    /** Every newly eligible timesheet has a valid hourly rate for its work date; null when nothing to price. */
    private Boolean rateValidationPassed;

    private BillingPeriodReadiness readiness;
    @Builder.Default
    private List<String> blockingReasons = new ArrayList<>();

    @Builder.Default
    private List<ExistingBillingBatchDto> batches = new ArrayList<>();

    /**
     * Display only: snapshots of this configuration whose period overlaps this one but is not one of the
     * configuration's billing periods (e.g. a calendar-month snapshot on a weekly schedule, created by the
     * legacy snapshot endpoint). Not counted in {@code batches} or {@code previouslyAcquired}; whether a
     * record is already billed is decided per source record by its claim, never by period.
     */
    @Builder.Default
    private List<ExistingBillingBatchDto> overlappingSnapshots = new ArrayList<>();
}
