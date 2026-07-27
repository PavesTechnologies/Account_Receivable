package com.AccountReceivableManagement.Integration.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.TimesheetDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Isolates Epic 2 from how approved, billable timesheets are actually
 * obtained from the TMS. The Time &amp; Material strategy depends only
 * on this contract.
 */
public interface TimesheetIntegration {

    /**
     * Retrieves approved billable timesheets for the supplied project
     * and billing period.
     *
     * @param projectId project identifier
     * @param billingPeriodStart billing period start date
     * @param billingPeriodEnd billing period end date
     * @return approved billable timesheet entries
     */
    List<TimesheetDto> getApprovedTimesheets(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);
}
