package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceResponseDto;

import java.util.List;
import java.util.UUID;

public interface BillingFixedPriceService {

    BillingFixedPriceResponseDto create(
            UUID billingConfigurationId,
            BillingFixedPriceRequestDto request);

    BillingFixedPriceResponseDto update(
            UUID fixedPriceConfigurationId,
            BillingFixedPriceRequestDto request);

    BillingFixedPriceResponseDto get(
            UUID fixedPriceConfigurationId);

    List<BillingFixedPriceResponseDto> getAll(
            UUID billingConfigurationId);

    void delete(
            UUID fixedPriceConfigurationId);
}
