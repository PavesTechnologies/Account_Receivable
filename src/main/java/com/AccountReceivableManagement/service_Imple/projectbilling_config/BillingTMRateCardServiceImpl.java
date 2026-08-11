package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
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

        // 1. Rate cards are allowed only for Timesheet Based billing
        if (!configuration.getBillingType()
                .getBillingTypeName()
                .equalsIgnoreCase("Timesheet Based")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Rate Cards can only be configured for Timesheet Based billing.");
        }

        // 2. Approved configuration cannot be modified
        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        // 3. Pricing model must be configured
        if (configuration.getPricingModel() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Pricing Model must be configured before adding Rate Cards.");
        }

        // 4. Validate Standard Rate
        if (configuration.getPricingModel() == PricingModel.STANDARD) {

            // Standard Rate supports only one active rate
            if (billingTMRateCardRepository
                    .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Only one rate can be configured for Standard Rate pricing.");
            }

            // Standard Rate must not have a role
            if (request.getRoleName() != null
                    && !request.getRoleName().trim().isEmpty()) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Role Name should not be provided for Standard Rate pricing.");
            }
        }

        // 5. Validate Role-Based Rate
        if (configuration.getPricingModel() == PricingModel.ROLE_BASED) {

            // Role is mandatory
            if (request.getRoleName() == null
                    || request.getRoleName().trim().isEmpty()) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Role Name is required for Role-Based pricing.");
            }

            // Role must be unique
            boolean roleExists =
                    billingTMRateCardRepository
                            .existsByBillingConfigurationAndRoleNameIgnoreCaseAndIsActiveTrue(
                                    configuration,
                                    request.getRoleName().trim());

            if (roleExists) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Role already exists for this Billing Configuration.");
            }
        }

        // 6. Validate effective dates
        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        // 7. Create Rate Card
        BillingTMRateCard rateCard = BillingTMRateCard.builder()
                .billingConfiguration(configuration)

                // Standard = no role
                // Role Based = selected role
                .roleName(
                        configuration.getPricingModel() == PricingModel.STANDARD
                                ? null
                                : request.getRoleName().trim()
                )

                .rate(request.getRate())
                .ratePeriod(request.getRatePeriod())
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

        // 1. Find existing rate card
        BillingTMRateCard rateCard =
                billingTMRateCardRepository.findById(rateCardId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Rate Card not found."));

        BillingConfiguration configuration =
                rateCard.getBillingConfiguration();

        // 2. Approved configuration cannot be modified
        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        // 3. Validate billing type
        if (!configuration.getBillingType()
                .getBillingTypeName()
                .equalsIgnoreCase("Timesheet Based")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Rate Cards can only be configured for Timesheet Based billing.");
        }

        // 4. Pricing model must be configured
        if (configuration.getPricingModel() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Pricing Model must be configured before updating Rate Cards.");
        }

        // 5. Validate Standard Rate
        if (configuration.getPricingModel() == PricingModel.STANDARD) {

            // Standard pricing must not have a role
            if (request.getRoleName() != null
                    && !request.getRoleName().trim().isEmpty()) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Role Name should not be provided for Standard Rate pricing.");
            }

            // Only one active rate card is allowed for Standard pricing
            boolean anotherActiveRateExists =
                    billingTMRateCardRepository
                            .findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(
                                    configuration)
                            .stream()
                            .anyMatch(existing ->
                                    !existing.getRateCardId().equals(rateCardId));

            if (anotherActiveRateExists) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Only one rate can be configured for Standard Rate pricing.");
            }
        }

        // 6. Validate Role-Based Rate
        if (configuration.getPricingModel() == PricingModel.ROLE_BASED) {

            // Role is mandatory
            if (request.getRoleName() == null
                    || request.getRoleName().trim().isEmpty()) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Role Name is required for Role-Based pricing.");
            }

            // Check duplicate role excluding the current rate card
            boolean duplicateRole =
                    billingTMRateCardRepository
                            .findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(
                                    configuration)
                            .stream()
                            .anyMatch(existing ->
                                    !existing.getRateCardId().equals(rateCardId)
                                            && existing.getRoleName() != null
                                            && existing.getRoleName()
                                            .equalsIgnoreCase(
                                                    request.getRoleName().trim()));

            if (duplicateRole) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Role already exists for this Billing Configuration.");
            }
        }

        // 7. Validate effective dates
        if (request.getEffectiveFrom() != null
                && request.getEffectiveTo() != null
                && request.getEffectiveFrom()
                .isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        // 8. Update rate card
        rateCard.setRoleName(
                configuration.getPricingModel() == PricingModel.STANDARD
                        ? null
                        : request.getRoleName().trim()
        );

        rateCard.setRate(request.getRate());
        rateCard.setRatePeriod(request.getRatePeriod());
        rateCard.setEffectiveFrom(request.getEffectiveFrom());
        rateCard.setEffectiveTo(request.getEffectiveTo());
        rateCard.setRemarks(request.getRemarks());
        rateCard.setUpdatedAt(LocalDateTime.now());

        // 9. Save
        BillingTMRateCard updated =
                billingTMRateCardRepository.save(rateCard);

        // 10. Return response
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
                .rate(rateCard.getRate())
                .ratePeriod(rateCard.getRatePeriod())
                .effectiveFrom(rateCard.getEffectiveFrom())
                .effectiveTo(rateCard.getEffectiveTo())
                .remarks(rateCard.getRemarks())
                .createdAt(rateCard.getCreatedAt())
                .updatedAt(rateCard.getUpdatedAt())
                .build();
    }



}
