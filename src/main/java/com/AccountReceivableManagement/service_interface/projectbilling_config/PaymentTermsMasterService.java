package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.PaymentTermsRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.PaymentTermsResponseDto;

import java.util.List;
import java.util.UUID;

public interface PaymentTermsMasterService {

    PaymentTermsResponseDto createPaymentTerm(
            PaymentTermsRequestDto request);

    PaymentTermsResponseDto updatePaymentTerm(
            UUID paymentTermId,
            PaymentTermsRequestDto request);

    PaymentTermsResponseDto getPaymentTermById(
            UUID paymentTermId);

    List<PaymentTermsResponseDto> getAllPaymentTerms();

    List<PaymentTermsResponseDto> getActivePaymentTerms();

    void deletePaymentTerm(UUID paymentTermId);

    void activatePaymentTerm(UUID paymentTermId);
}
