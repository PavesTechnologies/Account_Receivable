package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingFrequencyRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFrequencyResponseDto;

import java.util.List;
import java.util.UUID;

public interface BillingFrequencyMasterService {
    BillingFrequencyResponseDto createBillingFrequency(BillingFrequencyRequestDto request);

    BillingFrequencyResponseDto updateBillingFrequency(UUID billingFrequencyId,
                                                       BillingFrequencyRequestDto request);

    BillingFrequencyResponseDto getBillingFrequencyById(UUID billingFrequencyId);

    List<BillingFrequencyResponseDto> getAllBillingFrequencies();

    List<BillingFrequencyResponseDto> getActiveBillingFrequencies();

    void deleteBillingFrequency(UUID billingFrequencyId);

    BillingFrequencyResponseDto activateBillingFrequency(UUID billingFrequencyId);
}
