package com.AccountReceivableManagement.Strategy.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.Integration.billing_data_acquisition.TimesheetIntegration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Story 2.1: acquires approved timesheets from the TMS for Time &amp;
 * Material billing. This is the one place operational data (TMS hours)
 * and commercial data (Epic 1's hourly rate) come together — TMS never
 * knows the rate, and the Validator/Builder downstream never need to know
 * where it came from. Acquisition and this one merge step only; validation,
 * entity mapping, and totals belong to later layers.
 */
@Component
@RequiredArgsConstructor
public class TimeAndMaterialBillingStrategy implements BillingAcquisitionStrategy {

    private final TimesheetIntegration timesheetIntegration;

    @Override
    public BillingType getSupportedBillingType() {
        return BillingType.TIME_AND_MATERIAL;
    }

    @Override
    public BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration, BillingSnapshotCreateRequestDto request) {
        List<TimesheetDto> timesheets = timesheetIntegration.getApprovedTimesheets(
                request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd());

        timesheets.forEach(timesheet -> timesheet.setHourlyRate(configuration.getHourlyRate()));

        return BillingAcquisitionResultDto.builder()
                .timesheets(timesheets)
                .build();
    }
}
