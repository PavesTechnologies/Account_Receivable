package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingResponseDto;

import java.util.List;
import java.util.UUID;

public interface RecurringBillingService {

    RecurringBillingResponseDto create(
            UUID billingConfigurationId,
            RecurringBillingRequestDto request);

    RecurringBillingResponseDto update(
            UUID recurringConfigurationId,
            RecurringBillingRequestDto request);

    RecurringBillingResponseDto get(
            UUID recurringConfigurationId);

    List<RecurringBillingResponseDto> getByBillingConfiguration(
            UUID billingConfigurationId);

    List<BillingPeriodDto> getBillingSchedule(
            UUID recurringConfigurationId);

    void delete(
            UUID recurringConfigurationId);
}
