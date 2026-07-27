package com.AccountReceivableManagement.Validator.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.ValidationResultDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates acquired Time &amp; Material data before it reaches the Builder.
 * Fail-fast: any rule violation immediately fails the whole acquisition —
 * invalid data is never silently dropped or cleaned up.
 */
@Component
public class BillingAcquisitionValidator {

    public ValidationResultDto validate(BillingAcquisitionResultDto acquisitionResult) {
        if (acquisitionResult == null) {
            return ValidationResultDto.failure("Billing acquisition result is missing.");
        }

        List<TimesheetDto> timesheets = acquisitionResult.getTimesheets();
        if (timesheets == null || timesheets.isEmpty()) {
            return ValidationResultDto.failure("No timesheets were acquired for the requested billing period.");
        }

        Set<String> seenSourceReferenceIds = new HashSet<>();
        for (TimesheetDto timesheet : timesheets) {
            if (!timesheet.isApproved()) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] is not approved.");
            }
            if (!timesheet.isBillable()) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] is not billable.");
            }
            if (timesheet.getHours() == null || timesheet.getHours().compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] has invalid hours.");
            }
            if (timesheet.getHourlyRate() == null || timesheet.getHourlyRate().compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] has invalid hourly rate.");
            }
            if (!seenSourceReferenceIds.add(timesheet.getSourceReferenceId())) {
                return ValidationResultDto.failure(
                        "Timesheet [" + timesheet.getSourceReferenceId() + "] is a duplicate source reference.");
            }
        }

        return ValidationResultDto.success(acquisitionResult);
    }
}
