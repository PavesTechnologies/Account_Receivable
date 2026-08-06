package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingFixedPriceService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class BillingFixedPriceServiceImpl implements BillingFixedPriceService {

    private final BillingFixedPriceRepository billingFixedPriceRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;

    @Override
    public BillingFixedPriceResponseDto create(
            UUID billingConfigurationId,
            BillingFixedPriceRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        BillingTypeMaster billingType = configuration.getBillingType();

        if (!billingType.getBillingTypeName()
                .equalsIgnoreCase("Fixed Price")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Fixed Price configuration can only be created for Fixed Price billing.");
        }

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        if (billingFixedPriceRepository
                .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Fixed Price configuration already exists.");
        }

        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        BillingFixedPriceConfiguration fixedPrice =
                BillingFixedPriceConfiguration.builder()
                        .billingConfiguration(configuration)
                        .contractValue(request.getContractValue())
                        .effectiveFrom(request.getEffectiveFrom())
                        .effectiveTo(request.getEffectiveTo())
                        .remarks(request.getRemarks())
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        BillingFixedPriceConfiguration saved =
                billingFixedPriceRepository.save(fixedPrice);

        return mapToResponse(saved);
    }

    @Override
    public BillingFixedPriceResponseDto update(
            UUID fixedPriceConfigurationId,
            BillingFixedPriceRequestDto request) {

        BillingFixedPriceConfiguration fixedPrice =
                billingFixedPriceRepository.findById(fixedPriceConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Fixed Price Configuration not found."));

        BillingConfiguration configuration =
                fixedPrice.getBillingConfiguration();

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        fixedPrice.setContractValue(request.getContractValue());
        fixedPrice.setEffectiveFrom(request.getEffectiveFrom());
        fixedPrice.setEffectiveTo(request.getEffectiveTo());
        fixedPrice.setRemarks(request.getRemarks());
        fixedPrice.setUpdatedAt(LocalDateTime.now());

        BillingFixedPriceConfiguration updated =
                billingFixedPriceRepository.save(fixedPrice);

        return mapToResponse(updated);
    }

    @Override
    public BillingFixedPriceResponseDto get(
            UUID fixedPriceConfigurationId) {

        BillingFixedPriceConfiguration fixedPrice =
                billingFixedPriceRepository.findById(fixedPriceConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Fixed Price Configuration not found."));

        return mapToResponse(fixedPrice);
    }

    @Override
    public List<BillingFixedPriceResponseDto> getAll(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        return billingFixedPriceRepository
                .findAllByBillingConfigurationAndIsActiveTrue(configuration)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void delete(
            UUID fixedPriceConfigurationId) {

        BillingFixedPriceConfiguration fixedPrice =
                billingFixedPriceRepository.findById(fixedPriceConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Fixed Price Configuration not found."));

        BillingConfiguration configuration =
                fixedPrice.getBillingConfiguration();

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        fixedPrice.setIsActive(false);
        fixedPrice.setUpdatedAt(LocalDateTime.now());

        billingFixedPriceRepository.save(fixedPrice);
    }

    private BillingFixedPriceResponseDto mapToResponse(
            BillingFixedPriceConfiguration fixedPrice) {

        return BillingFixedPriceResponseDto.builder()
                .fixedPriceConfigurationId(
                        fixedPrice.getFixedPriceConfigurationId())
                .contractValue(fixedPrice.getContractValue())
                .effectiveFrom(fixedPrice.getEffectiveFrom())
                .effectiveTo(fixedPrice.getEffectiveTo())
                .remarks(fixedPrice.getRemarks())
                .createdAt(fixedPrice.getCreatedAt())
                .updatedAt(fixedPrice.getUpdatedAt())
                .build();
    }


}
