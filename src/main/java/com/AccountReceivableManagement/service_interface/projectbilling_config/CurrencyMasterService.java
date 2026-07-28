package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.CurrencyRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.CurrencyResponseDto;

import java.util.List;
import java.util.UUID;

public interface CurrencyMasterService {

    CurrencyResponseDto createCurrency(CurrencyRequestDto request);

    CurrencyResponseDto updateCurrency(UUID currencyId,
                                       CurrencyRequestDto request);

    CurrencyResponseDto getCurrencyById(UUID currencyId);

    List<CurrencyResponseDto> getAllCurrencies();

    List<CurrencyResponseDto> getActiveCurrencies();

    void deleteCurrency(UUID currencyId);
}
