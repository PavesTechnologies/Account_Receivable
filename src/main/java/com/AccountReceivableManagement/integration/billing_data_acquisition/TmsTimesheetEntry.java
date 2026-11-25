package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Fields match the TMS GET /api/timesheets/billing response contract:
 * timesheetId, resourceId, resourceName, workDate, hours, role,
 * approvalStatus. One record per TMS timesheet that has billable entries for
 * the project; {@code hours} is the sum of those billable entries only. TMS
 * sends no billable flag on this endpoint (billable by its contract, see
 * {@code TimesheetIntegrationImpl#resolveBillable}); an explicit
 * {@code billable} / {@code isBillable}, if ever sent, is still honoured.
 * Any other TMS fields are ignored.
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

    /**
     * Optional explicit billable flag sent as {@code "billable"}. Boxed so an
     * omitted value (the normal case for this endpoint) stays distinguishable
     * from an explicit {@code false}, which is never overridden.
     */
    private Boolean billable;

    /**
     * The same optional flag sent as {@code "isBillable"} (TMS's entry-level
     * name). Kept as its own property, not a {@code @JsonAlias} of
     * {@link #billable}, so a record carrying both with different values is
     * detected instead of the last one silently winning. Resolved by
     * {@code TimesheetIntegrationImpl#resolveBillable}.
     */
    @JsonProperty("isBillable")
    private Boolean isBillable;
}
