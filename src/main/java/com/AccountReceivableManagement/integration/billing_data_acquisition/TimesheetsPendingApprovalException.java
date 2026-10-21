package com.AccountReceivableManagement.integration.billing_data_acquisition;

/**
 * Thrown when timesheets exist for a billing period but are not yet approved
 * in TMS. This is a waiting condition, not a permanent failure, so automatic
 * billing retries the period.
 */
public class TimesheetsPendingApprovalException extends RuntimeException {

    public TimesheetsPendingApprovalException(String message) {
        super(message);
    }

    public TimesheetsPendingApprovalException(String message, Throwable cause) {
        super(message, cause);
    }
}
