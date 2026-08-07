package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingSubscriptionRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingSubscriptionResponseDto;

import java.util.List;
import java.util.UUID;

public interface BillingSubscriptionService {

    BillingSubscriptionResponseDto create(
            UUID billingConfigurationId,
            BillingSubscriptionRequestDto request);

    BillingSubscriptionResponseDto update(
            UUID subscriptionConfigurationId,
            BillingSubscriptionRequestDto request);

    BillingSubscriptionResponseDto get(
            UUID subscriptionConfigurationId);

    BillingSubscriptionResponseDto getByBillingConfiguration(
            UUID billingConfigurationId);

    void delete(
            UUID subscriptionConfigurationId);
}
