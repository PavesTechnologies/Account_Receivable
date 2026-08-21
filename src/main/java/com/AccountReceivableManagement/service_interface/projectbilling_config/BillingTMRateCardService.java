package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardResponseDto;
import java.util.List;
import java.util.UUID;

public interface BillingTMRateCardService {

    BillingTMRateCardResponseDto addRateCard(
            UUID billingConfigurationId,
            BillingTMRateCardRequestDto request);

    BillingTMRateCardResponseDto updateRateCard(
            UUID rateCardId,
            BillingTMRateCardRequestDto request);

    BillingTMRateCardResponseDto getRateCard(
            UUID rateCardId);

    List<BillingTMRateCardResponseDto> getAllRateCards(
            UUID billingConfigurationId);

    void deleteRateCard(
            UUID rateCardId);

    BillingTMRateCardResponseDto saveRateCard(
            UUID billingConfigurationId,
            BillingTMRateCardRequestDto request);

}
