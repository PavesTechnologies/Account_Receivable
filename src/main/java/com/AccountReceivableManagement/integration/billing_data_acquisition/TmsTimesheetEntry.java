package com.AccountReceivableManagement.integration.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Raw shape of one entry in the TMS billing-timesheets response.
 * Deserialization target only — never leaves the Integration layer.
 *
 * Fields match the TMS GET /api/timesheets/billing response contract exactly:
 * timesheetId, resourceId, resourceName, workDate, hours, role,
 * approvalStatus, billable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmsTimesheetEntry {

    /** TMS-side unique identifier for this timesheet entry. */
    private String timesheetId;

    /** Employee/resource identifier in TMS. */
    private Long resourceId;

    /** Full display name of the employee. Shown as "Employee" in the UI table. */
    private String resourceName;

    /** Date the work was performed. Shown as "Work Date" in the UI table. */
    private LocalDate workDate;

    /** Approved billable hours for this entry. */
    private BigDecimal hours;

    /**
     * Project role of the resource (e.g. "Developer", "Tester").
     * Required for ROLE_BASED billing mode to look up the per-role rate.
     */
    private String role;

    /**
     * TMS approval status. Must be "APPROVED" — TMS contract guarantees
     * only approved entries are returned, but we carry the value through
     * for display in the UI "Approval Status" column.
     */
    private String approvalStatus;

    /** TMS billable flag. Must be true — TMS contract guarantees only billable entries are returned. */
    private boolean billable;
}
