package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingSubscriptionRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingSubscriptionResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSubscriptionConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalPricingType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingSubscriptionConfigurationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BillingSubscriptionServiceImpl implements BillingSubscriptionService {

    private final BillingSubscriptionConfigurationRepository
            billingSubscriptionRepository;

    private final BillingConfigurationRepository
            billingConfigurationRepository;

    private final BillingFrequencyMasterRepository
            billingFrequencyRepository;

    @Override
    public BillingSubscriptionResponseDto create(
            UUID billingConfigurationId,
            BillingSubscriptionRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        if (!configuration.getBillingType().getBillingTypeName()
                .equalsIgnoreCase("Subscription")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Selected Billing Configuration is not Subscription.");
        }

        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        if (billingSubscriptionRepository
                .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Subscription Configuration already exists.");
        }

        validateSubscription(request);

        BillingSubscriptionConfiguration subscription =
                BillingSubscriptionConfiguration.builder()
                        .billingConfiguration(configuration)
                        .subscriptionName(request.getSubscriptionName())
                        .contractValue(request.getContractValue())
                        .subscriptionStartDate(request.getSubscriptionStartDate())
                        .subscriptionEndDate(request.getSubscriptionEndDate())
                        .renewalType(request.getRenewalType())
                        .renewalDurationType(request.getRenewalDurationType())
                        .renewalDurationValue(request.getRenewalDurationValue())
                        .renewalDurationUnit(request.getRenewalDurationUnit())
                        .renewalPricingType(request.getRenewalPricingType())
                        .renewalContractValue(request.getRenewalContractValue())
                        .renewalBillingFrequency(
                                getRenewalFrequency(
                                        request.getRenewalBillingFrequencyId()))
                        .renewalEffectiveFrom(
                                request.getRenewalEffectiveFrom())
                        .remarks(request.getRemarks())
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        BillingSubscriptionConfiguration saved =
                billingSubscriptionRepository.save(subscription);

        return mapToResponse(saved);
    }

    @Override
    public BillingSubscriptionResponseDto update(
            UUID subscriptionConfigurationId,
            BillingSubscriptionRequestDto request) {

        BillingSubscriptionConfiguration subscription =
                billingSubscriptionRepository.findById(subscriptionConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Subscription Configuration not found."));

        if (subscription.getBillingConfiguration().getStatus()
                == BillingConfigurationStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        validateSubscription(request);

        subscription.setSubscriptionName(
                request.getSubscriptionName());

        subscription.setContractValue(
                request.getContractValue());

        subscription.setSubscriptionStartDate(
                request.getSubscriptionStartDate());

        subscription.setSubscriptionEndDate(
                request.getSubscriptionEndDate());

        subscription.setRenewalType(
                request.getRenewalType());

        subscription.setRenewalDurationType(
                request.getRenewalDurationType());

        subscription.setRenewalDurationValue(
                request.getRenewalDurationValue());

        subscription.setRenewalDurationUnit(
                request.getRenewalDurationUnit());

        subscription.setRenewalPricingType(
                request.getRenewalPricingType());

        subscription.setRenewalContractValue(
                request.getRenewalContractValue());

        subscription.setRenewalBillingFrequency(
                getRenewalFrequency(
                        request.getRenewalBillingFrequencyId()));

        subscription.setRenewalEffectiveFrom(
                request.getRenewalEffectiveFrom());

        subscription.setRemarks(
                request.getRemarks());

        subscription.setUpdatedAt(LocalDateTime.now());

        BillingSubscriptionConfiguration updated =
                billingSubscriptionRepository.save(subscription);

        return mapToResponse(updated);
    }

    private void validateSubscription(
            BillingSubscriptionRequestDto request) {

        if (request.getSubscriptionStartDate()
                .isAfter(request.getSubscriptionEndDate())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Subscription Start Date cannot be after End Date.");
        }


        if (request.getRenewalType() == RenewalType.MANUAL) {

            return;
        }

        if (request.getRenewalDurationType() == null) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Renewal Duration Type is required.");
        }

        if (request.getRenewalPricingType() == null) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Renewal Pricing Type is required.");
        }

        if (request.getRenewalBillingFrequencyId() == null) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Renewal Billing Frequency is required.");
        }

        if (request.getRenewalEffectiveFrom() == null) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Renewal Effective From is required.");
        }

        LocalDate minimumRenewalDate =
                request.getSubscriptionEndDate().plusDays(1);

        if (request.getRenewalEffectiveFrom()
                .isBefore(minimumRenewalDate)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Renewal Start Date must be after Subscription End Date.");
        }


        if (request.getRenewalDurationType()
                == RenewalDurationType.CUSTOM) {

            if (request.getRenewalDurationValue() == null
                    || request.getRenewalDurationValue() <= 0) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Duration Value is required.");
            }

            if (request.getRenewalDurationUnit() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Duration Unit is required.");
            }
        }

        if (request.getRenewalPricingType()
                == RenewalPricingType.REVISED_PRICE) {

            if (request.getRenewalContractValue() == null
                    || request.getRenewalContractValue()
                    .compareTo(BigDecimal.ZERO) <= 0) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Contract Value is required.");
            }
        }
        if (request.getRenewalDurationType()
                == RenewalDurationType.SAME_DURATION) {

            request.setRenewalDurationValue(null);
            request.setRenewalDurationUnit(null);
        }

        if (request.getRenewalPricingType()
                == RenewalPricingType.SAME_PRICE) {

            request.setRenewalContractValue(null);
        }
    }

    private BillingFrequencyMaster getRenewalFrequency(UUID id) {

        if (id == null) {
            return null;
        }

        BillingFrequencyMaster frequency =
                billingFrequencyRepository.findById(id)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Renewal Billing Frequency not found."));

        if (!Boolean.TRUE.equals(frequency.getIsActive())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Selected Renewal Billing Frequency is inactive.");
        }

        return frequency;
    }

    @Override
    public BillingSubscriptionResponseDto get(
            UUID subscriptionConfigurationId) {

        BillingSubscriptionConfiguration subscription =
                billingSubscriptionRepository.findById(subscriptionConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Subscription Configuration not found."));

        return mapToResponse(subscription);
    }

    @Override
    public BillingSubscriptionResponseDto getByBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        BillingSubscriptionConfiguration subscription =
                billingSubscriptionRepository
                        .findByBillingConfigurationAndIsActiveTrue(configuration)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Subscription Configuration not found."));

        return mapToResponse(subscription);
    }

    @Override
    public void delete(
            UUID subscriptionConfigurationId) {

        BillingSubscriptionConfiguration subscription =
                billingSubscriptionRepository.findById(subscriptionConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Subscription Configuration not found."));

        if (subscription.getBillingConfiguration().getStatus()
                == BillingConfigurationStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        subscription.setIsActive(false);
        subscription.setUpdatedAt(LocalDateTime.now());

        billingSubscriptionRepository.save(subscription);
    }

    private BillingSubscriptionResponseDto mapToResponse(
            BillingSubscriptionConfiguration subscription) {

        return BillingSubscriptionResponseDto.builder()
                .subscriptionConfigurationId(
                        subscription.getSubscriptionConfigurationId())
                .subscriptionName(
                        subscription.getSubscriptionName())
                .contractValue(
                        subscription.getContractValue())
                .subscriptionStartDate(
                        subscription.getSubscriptionStartDate())
                .subscriptionEndDate(
                        subscription.getSubscriptionEndDate())
                .renewalType(
                        subscription.getRenewalType())
                .renewalDurationType(
                        subscription.getRenewalDurationType())
                .renewalDurationValue(
                        subscription.getRenewalDurationValue())
                .renewalDurationUnit(
                        subscription.getRenewalDurationUnit())
                .renewalPricingType(
                        subscription.getRenewalPricingType())
                .renewalContractValue(
                        subscription.getRenewalContractValue())
                .renewalBillingFrequencyId(
                        subscription.getRenewalBillingFrequency() != null
                                ? subscription.getRenewalBillingFrequency().getBillingFrequencyId()
                                : null)
                .renewalBillingFrequencyName(
                        subscription.getRenewalBillingFrequency() != null
                                ? subscription.getRenewalBillingFrequency().getBillingFrequencyName()
                                : null)
                .renewalEffectiveFrom(
                        subscription.getRenewalEffectiveFrom())
                .remarks(
                        subscription.getRemarks())
                .createdAt(
                        subscription.getCreatedAt())
                .updatedAt(
                        subscription.getUpdatedAt())
                .build();
    }
}
