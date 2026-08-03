package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingTMRateCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BillingTMRateCardServiceImpl implements BillingTMRateCardService {


    private final BillingTMRateCardRepository billingTMRateCardRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;

    @Override
    public BillingTMRateCardResponseDto addRateCard(
            UUID billingConfigurationId,
            BillingTMRateCardRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        // Validate Billing Type
        if (!configuration.getBillingType()
                .getBillingTypeName()
                .equalsIgnoreCase("Time & Material")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Rate Cards can only be configured for Time & Material billing.");
        }

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        boolean roleExists =
                billingTMRateCardRepository
                        .existsByBillingConfigurationAndRoleNameIgnoreCaseAndIsActiveTrue(
                                configuration,
                                request.getRoleName());

        if (roleExists) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Role already exists for this Billing Configuration.");
        }

        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        BillingTMRateCard rateCard = BillingTMRateCard.builder()
                .billingConfiguration(configuration)
                .roleName(request.getRoleName())
                .hourlyRate(request.getHourlyRate())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .remarks(request.getRemarks())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        BillingTMRateCard saved =
                billingTMRateCardRepository.save(rateCard);

        return mapToResponse(saved);
    }

    @Override
    public BillingTMRateCardResponseDto updateRateCard(
            UUID rateCardId,
            BillingTMRateCardRequestDto request) {

        BillingTMRateCard rateCard =
                billingTMRateCardRepository.findById(rateCardId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Rate Card not found."));

        BillingConfiguration configuration =
                rateCard.getBillingConfiguration();

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        boolean duplicateRole =
                billingTMRateCardRepository
                        .findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(configuration)
                        .stream()
                        .anyMatch(existing ->
                                !existing.getRateCardId().equals(rateCardId)
                                        && existing.getRoleName()
                                        .equalsIgnoreCase(request.getRoleName()));

        if (duplicateRole) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Role already exists for this Billing Configuration.");
        }

        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        rateCard.setRoleName(request.getRoleName());
        rateCard.setHourlyRate(request.getHourlyRate());
        rateCard.setEffectiveFrom(request.getEffectiveFrom());
        rateCard.setEffectiveTo(request.getEffectiveTo());
        rateCard.setRemarks(request.getRemarks());
        rateCard.setUpdatedAt(LocalDateTime.now());

        BillingTMRateCard updated =
                billingTMRateCardRepository.save(rateCard);

        return mapToResponse(updated);
    }

    @Override
    public BillingTMRateCardResponseDto getRateCard(UUID rateCardId) {

        BillingTMRateCard rateCard = billingTMRateCardRepository.findById(rateCardId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Rate Card not found."));

        return mapToResponse(rateCard);
    }

    @Override
    public List<BillingTMRateCardResponseDto> getAllRateCards(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        return billingTMRateCardRepository
                .findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(configuration)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void deleteRateCard(UUID rateCardId) {

        BillingTMRateCard rateCard =
                billingTMRateCardRepository.findById(rateCardId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Rate Card not found."));

        BillingConfiguration configuration =
                rateCard.getBillingConfiguration();

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        rateCard.setIsActive(false);
        rateCard.setUpdatedAt(LocalDateTime.now());

        billingTMRateCardRepository.save(rateCard);
    }

    private BillingTMRateCardResponseDto mapToResponse(
            BillingTMRateCard rateCard) {

        return BillingTMRateCardResponseDto.builder()
                .rateCardId(rateCard.getRateCardId())
                .roleName(rateCard.getRoleName())
                .hourlyRate(rateCard.getHourlyRate())
                .effectiveFrom(rateCard.getEffectiveFrom())
                .effectiveTo(rateCard.getEffectiveTo())
                .remarks(rateCard.getRemarks())
                .createdAt(rateCard.getCreatedAt())
                .updatedAt(rateCard.getUpdatedAt())
                .build();
    }



}
