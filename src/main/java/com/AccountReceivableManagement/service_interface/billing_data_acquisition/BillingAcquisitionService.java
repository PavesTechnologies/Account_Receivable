package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;

import java.time.LocalDate;
import java.util.UUID;

public interface BillingAcquisitionService {

    /**
     * Creates or updates a manual billing acquisition record for a given configuration
     * and billing period.
     */
    AcquireDataResponseDto createManualAcquisition(UUID billingConfigurationId, LocalDate startDate, LocalDate endDate);

    /**
     * Overload accepting request DTO.
     */
    AcquireDataResponseDto createManualAcquisition(BillingAcquisitionRequestDto requestDto);
}
