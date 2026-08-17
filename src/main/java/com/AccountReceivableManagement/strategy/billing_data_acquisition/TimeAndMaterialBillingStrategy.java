package com.AccountReceivableManagement.strategy.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Acquires approved timesheets from TMS for Time & Material billing
 * and retrieves active hourly rates from billing_tm_rate_card for the configuration.
 */
@Component
@RequiredArgsConstructor
public class TimeAndMaterialBillingStrategy implements BillingAcquisitionStrategy {

    private final TimesheetIntegration timesheetIntegration;
    private final BillingTMRateCardRepository billingTMRateCardRepository;

    @Override
    public BillingType getSupportedBillingType() {
        return BillingType.TIME_AND_MATERIAL;
    }

    @Override
    public BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration, BillingSnapshotCreateRequestDto request) {
        List<TimesheetDto> timesheets = timesheetIntegration.getApprovedTimesheets(
                request.getProjectId(), request.getBillingPeriodStart(), request.getBillingPeriodEnd());

        if (timesheets == null) {
            timesheets = Collections.emptyList();
        }

        UUID configId = request.getBillingConfigurationId() != null
                ? request.getBillingConfigurationId()
                : (configuration != null ? configuration.getBillingConfigurationId() : null);

        for (TimesheetDto timesheet : timesheets) {
            LocalDate workDate = timesheet.getWorkDate() != null ? timesheet.getWorkDate() : request.getBillingPeriodStart();
            BigDecimal hourlyRate = null;

            if (configId != null) {
                List<BillingTMRateCard> activeRates = billingTMRateCardRepository
                        .findActiveRatesByConfigurationAndDate(configId, workDate);

                if (activeRates != null && !activeRates.isEmpty()) {
                    hourlyRate = activeRates.get(0).getRate();
                } else {
                    List<BillingTMRateCard> allActive = billingTMRateCardRepository
                            .findByBillingConfiguration_BillingConfigurationIdAndIsActiveTrue(configId);
                    if (allActive != null && !allActive.isEmpty()) {
                        hourlyRate = allActive.get(0).getRate();
                    }
                }
            }

            if (hourlyRate == null && configuration != null && configuration.getHourlyRate() != null) {
                hourlyRate = configuration.getHourlyRate();
            }

            timesheet.setHourlyRate(hourlyRate);
        }

        return BillingAcquisitionResultDto.builder()
                .billingConfigurationId(configId)
                .timesheets(timesheets)
                .build();
    }
}
