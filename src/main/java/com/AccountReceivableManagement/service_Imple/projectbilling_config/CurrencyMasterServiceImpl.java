package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.CurrencyRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.CurrencyResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.CurrencyMasterService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CurrencyMasterServiceImpl implements CurrencyMasterService {

    private final CurrencyMasterRepository currencyRepository;

    @Override
    public CurrencyResponseDto createCurrency(CurrencyRequestDto request) {

        if (currencyRepository.existsByCurrencyCodeIgnoreCase(request.getCurrencyCode())) {
            throw new GlobalExceptionHandler.DuplicateBillingConfigurationException("Currency code already exists.");
        }

        if (currencyRepository.existsByCurrencyNameIgnoreCase(request.getCurrencyName())) {
            throw new GlobalExceptionHandler.DuplicateBillingConfigurationException("Currency name already exists.");
        }

        CurrencyMaster currency = CurrencyMaster.builder()
                .currencyCode(request.getCurrencyCode().trim().toUpperCase())
                .currencyName(request.getCurrencyName().trim())
                .currencySymbol(request.getCurrencySymbol())
                .description(request.getDescription())
                .isActive(true)
                .build();

        CurrencyMaster savedCurrency = currencyRepository.save(currency);

        return mapToResponse(savedCurrency);
    }

    @Override
    public CurrencyResponseDto updateCurrency(UUID currencyId,
                                              CurrencyRequestDto request) {

        CurrencyMaster currency = currencyRepository.findById(currencyId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Currency not found."));

        if (!currency.getCurrencyCode().equalsIgnoreCase(request.getCurrencyCode())
                && currencyRepository.existsByCurrencyCodeIgnoreCase(request.getCurrencyCode())) {

            throw new GlobalExceptionHandler.DuplicateBillingConfigurationException("Currency code already exists.");
        }

        if (!currency.getCurrencyName().equalsIgnoreCase(request.getCurrencyName())
                && currencyRepository.existsByCurrencyNameIgnoreCase(request.getCurrencyName())) {

            throw new GlobalExceptionHandler.DuplicateBillingConfigurationException("Currency name already exists.");
        }

        currency.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        currency.setCurrencyName(request.getCurrencyName().trim());
        currency.setCurrencySymbol(request.getCurrencySymbol());
        currency.setDescription(request.getDescription());

        CurrencyMaster updatedCurrency = currencyRepository.save(currency);

        return mapToResponse(updatedCurrency);
    }

    @Override
    public CurrencyResponseDto getCurrencyById(UUID currencyId) {

        CurrencyMaster currency = currencyRepository.findById(currencyId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Currency not found."));

        return mapToResponse(currency);
    }

    @Override
    public List<CurrencyResponseDto> getAllCurrencies() {

        return currencyRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CurrencyResponseDto> getActiveCurrencies() {

        return currencyRepository.findByIsActiveTrueOrderByCurrencyCodeAsc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteCurrency(UUID currencyId) {

        CurrencyMaster currency = currencyRepository.findById(currencyId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Currency not found."));

        currency.setIsActive(false);

        currencyRepository.save(currency);
    }

    private CurrencyResponseDto mapToResponse(CurrencyMaster currency) {

        return CurrencyResponseDto.builder()
                .currencyId(currency.getCurrencyId())
                .currencyCode(currency.getCurrencyCode())
                .currencyName(currency.getCurrencyName())
                .currencySymbol(currency.getCurrencySymbol())
                .description(currency.getDescription())
                .isActive(currency.getIsActive())
                .createdAt(currency.getCreatedAt())
                .updatedAt(currency.getUpdatedAt())
                .build();
    }
}
