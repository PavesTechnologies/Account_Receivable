package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingTypeRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTypeResponseDto;

import java.util.List;
import java.util.UUID;

public interface BillingTypeMasterService {

    BillingTypeResponseDto createBillingType(BillingTypeRequestDto request);

    BillingTypeResponseDto updateBillingType(UUID billingTypeId,
                                             BillingTypeRequestDto request);

    BillingTypeResponseDto getBillingTypeById(UUID billingTypeId);

    List<BillingTypeResponseDto> getAllBillingTypes();

    List<BillingTypeResponseDto> getActiveBillingTypes();

    void deleteBillingType(UUID billingTypeId);
}
