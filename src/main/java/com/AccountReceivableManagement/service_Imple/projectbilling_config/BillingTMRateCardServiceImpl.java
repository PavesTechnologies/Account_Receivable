package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateDiscount;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateDiscountRepository;
import com.AccountReceivableManagement.util.TmRateCalculator;
import com.AccountReceivableManagement.util.TmRateCardRules;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingTMRateCardService;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BillingTMRateCardServiceImpl implements BillingTMRateCardService {

    private static final int ROLE_NAME_MAX_LENGTH = 100;

    private final BillingTMRateCardRepository billingTMRateCardRepository;
    private final BillingTMRateDiscountRepository billingTMRateDiscountRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final RecordActionLockService recordActionLockService;

    @Override
    public BillingTMRateCardResponseDto addRateCard(
            UUID billingConfigurationId,
            BillingTMRateCardRequestDto request) {

        return createRateCard(billingConfigurationId, request);
    }

    @Override
    public BillingTMRateCardResponseDto saveRateCard(
            UUID billingConfigurationId,
            BillingTMRateCardRequestDto request) {

        // Same rules as addRateCard: both create endpoints share one validated path, so neither can bypass the
        // duplicate-role / overlapping-window / pricing-model checks.
        return createRateCard(billingConfigurationId, request);
    }

    private BillingTMRateCardResponseDto createRateCard(
            UUID billingConfigurationId,
            BillingTMRateCardRequestDto request) {

        // The configuration row lock is the first read of the transaction: concurrent creates/updates for this
        // configuration queue here, and the card list read below is a locking read that sees their commits.
        BillingConfiguration configuration = lockConfiguration(billingConfigurationId);

        // Block if another user holds the parent BILLING_CONFIGURATION edit lock
        recordActionLockService.validateNotLockedByAnotherUser(
                LockResourceType.BILLING_CONFIGURATION,
                billingConfigurationId
        );

        validateBillingConfiguration(configuration);

        validateRequest(request);

        // Validate effective dates against project duration
        validateEffectiveDatesAgainstProjectDuration(
                configuration,
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        String role = TmRateCardRules.normalizeRole(request.getRoleName());
        validateRoleName(role);
        validatePricingModel(configuration, role, request.getRatePeriod());
        TmRateCardRules.assertNoRoleConflict(
                billingTMRateCardRepository.findActiveByConfigurationForUpdate(billingConfigurationId),
                null, role, request.getEffectiveFrom(), request.getEffectiveTo());

        BillingTMRateCard rateCard =
                BillingTMRateCard.builder()
                        .billingConfiguration(configuration)
                        .roleName(role)
                        .rate(request.getRate())
                        .ratePeriod(request.getRatePeriod())
                        .effectiveFrom(request.getEffectiveFrom())
                        .effectiveTo(request.getEffectiveTo())
                        .remarks(request.getRemarks())
                        .includeBaseRateInInvoice(
                                request.getIncludeBaseRateInInvoice() == null
                                        || request.getIncludeBaseRateInInvoice())
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        BillingTMRateCard saved =
                billingTMRateCardRepository.save(rateCard);

        touchConfiguration(configuration);

        return mapToResponse(saved);
    }

    @Override
    public BillingTMRateCardResponseDto updateRateCard(
            UUID rateCardId,
            BillingTMRateCardRequestDto request) {

        BillingTMRateCard rateCard = findActiveRateCard(rateCardId);

        // Lock order everywhere is configuration -> cards -> discount periods, so writers cannot deadlock.
        BillingConfiguration configuration = lockOwningConfiguration(rateCard);

        // Block if another user holds the parent BILLING_CONFIGURATION edit lock
        recordActionLockService.validateNotLockedByAnotherUser(
                LockResourceType.BILLING_CONFIGURATION,
                configuration.getBillingConfigurationId()
        );

        validateBillingConfiguration(configuration);

        validateRequest(request);

        // Validate effective dates against project duration
        validateEffectiveDatesAgainstProjectDuration(
                configuration,
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        String role = TmRateCardRules.normalizeRole(request.getRoleName());
        validateRoleName(role);
        validatePricingModel(configuration, role, request.getRatePeriod());
        TmRateCardRules.assertNoRoleConflict(
                billingTMRateCardRepository.findActiveByConfigurationForUpdate(
                        configuration.getBillingConfigurationId()),
                rateCard.getRateCardId(), role, request.getEffectiveFrom(), request.getEffectiveTo());

        // Revalidate the active discount periods against the new base rate and validity window BEFORE anything
        // is mutated, so a rejected edit changes nothing (existing discounts are never altered or deleted here).
        List<BillingTMRateDiscount> discounts =
                billingTMRateDiscountRepository.findActiveByRateCardForUpdate(rateCard.getRateCardId());
        validateDiscountsAgainstNewBaseRate(discounts, request);
        validateDiscountsWithinWindow(discounts, request.getEffectiveFrom(), request.getEffectiveTo());

        rateCard.setRoleName(role);
        rateCard.setRate(request.getRate());
        rateCard.setRatePeriod(request.getRatePeriod());
        rateCard.setEffectiveFrom(request.getEffectiveFrom());
        rateCard.setEffectiveTo(request.getEffectiveTo());
        rateCard.setRemarks(request.getRemarks());
        if (request.getIncludeBaseRateInInvoice() != null) {
            rateCard.setIncludeBaseRateInInvoice(request.getIncludeBaseRateInInvoice());
        }
        rateCard.setUpdatedAt(LocalDateTime.now());

        BillingTMRateCard updated =
                billingTMRateCardRepository.save(rateCard);

        touchConfiguration(configuration);

        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingTMRateCardResponseDto getRateCard(
            UUID rateCardId) {

        BillingTMRateCard rateCard =
                billingTMRateCardRepository.findById(rateCardId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Rate Card not found."));

        return mapToResponse(rateCard);
    }

    @Override
    @Transactional(readOnly = true)
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
    public void deleteRateCard(
            UUID rateCardId) {

        BillingTMRateCard rateCard = findActiveRateCard(rateCardId);

        BillingConfiguration configuration = lockOwningConfiguration(rateCard);

        // Block if another user holds the parent BILLING_CONFIGURATION edit lock
        recordActionLockService.validateNotLockedByAnotherUser(
                LockResourceType.BILLING_CONFIGURATION,
                configuration.getBillingConfigurationId()
        );

        validateBillingConfiguration(configuration);

        // Soft delete, with the card's discount periods: a period must never stay active (and visible through
        // the discount APIs) under a card that no longer applies. Snapshots and invoices keep their own frozen
        // copy of every rate and discount, so history is untouched.
        LocalDateTime now = LocalDateTime.now();
        List<BillingTMRateDiscount> discounts =
                billingTMRateDiscountRepository.findActiveByRateCardForUpdate(rateCard.getRateCardId());
        if (discounts != null && !discounts.isEmpty()) {
            discounts.forEach(discount -> {
                discount.setIsActive(false);
                discount.setUpdatedAt(now);
            });
            billingTMRateDiscountRepository.saveAll(discounts);
        }

        rateCard.setIsActive(false);
        rateCard.setUpdatedAt(now);

        billingTMRateCardRepository.save(rateCard);

        touchConfiguration(configuration);
    }

    /** An already-deleted card is "not found", so a repeated delete or an edit of a deleted card changes nothing. */
    private BillingTMRateCard findActiveRateCard(UUID rateCardId) {
        return billingTMRateCardRepository.findById(rateCardId)
                .filter(card -> !Boolean.FALSE.equals(card.getIsActive()))
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Rate Card not found."));
    }

    private BillingConfiguration lockConfiguration(UUID billingConfigurationId) {
        return billingTMRateCardRepository.lockConfiguration(billingConfigurationId)
                .or(() -> billingConfigurationRepository.findById(billingConfigurationId))
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Billing Configuration not found."));
    }

    private BillingConfiguration lockOwningConfiguration(BillingTMRateCard rateCard) {
        BillingConfiguration owner = rateCard.getBillingConfiguration();
        return billingTMRateCardRepository.lockConfiguration(owner.getBillingConfigurationId()).orElse(owner);
    }

    /**
     * Any change to a rate card is a commercial change to the configuration: an approved configuration goes back
     * to PENDING_APPROVAL + INACTIVE and must be re-approved (the same rule discount periods already follow).
     */
    private void touchConfiguration(BillingConfiguration configuration) {
        handleApprovalStateTransition(configuration);
        configuration.setUpdatedAt(LocalDateTime.now());
        billingConfigurationRepository.save(configuration);
    }

    private void validateRoleName(String role) {
        if (role != null && role.length() > ROLE_NAME_MAX_LENGTH) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Role name must be at most " + ROLE_NAME_MAX_LENGTH + " characters.");
        }
    }

    /**
     * A card the billing engine would reject or silently ignore is refused up front.
     * ROLE_BASED billing resolves cards by role and needs an HOURLY rate for timesheet work; STANDARD billing
     * ignores role-specific cards entirely. A configuration with no pricing model set is not checked.
     */
    private void validatePricingModel(BillingConfiguration configuration, String role, BillingRatePeriod ratePeriod) {
        PricingModel model = configuration.getPricingModel();
        if (model == PricingModel.ROLE_BASED) {
            if (role == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Role name is required for Role-Based pricing.");
            }
            if (ratePeriod != BillingRatePeriod.HOURLY) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Role-Based rate cards must use an HOURLY rate period; timesheet billing has no "
                                + "billing-unit conversion for " + ratePeriod + ".");
            }
        } else if (model == PricingModel.STANDARD && role != null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Role-specific rate cards are only used with Role-Based pricing; this billing configuration "
                            + "uses Standard pricing. Remove the role name or change the pricing model.");
        }
    }

    private void validateDiscountsAgainstNewBaseRate(
            List<BillingTMRateDiscount> discounts,
            BillingTMRateCardRequestDto request) {

        if (discounts == null || discounts.isEmpty()) {
            return;
        }
        // Discounts exist only on HOURLY rate cards (no billing-unit conversion is defined).
        if (request.getRatePeriod() != BillingRatePeriod.HOURLY) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The rate period cannot be changed to " + request.getRatePeriod()
                            + " while the rate card has active discount periods; remove them first.");
        }
        for (BillingTMRateDiscount discount : discounts) {
            try {
                TmRateCalculator.apply(request.getRate(), discount.getDiscountType(), discount.getDiscountValue());
            } catch (GlobalExceptionHandler.ValidationException ex) {
                throw new GlobalExceptionHandler.ValidationException(
                        "The new rate would invalidate the discount period '" + discount.getPeriodName()
                                + "' (" + discount.getEffectiveFrom() + " to " + discount.getEffectiveTo()
                                + "): " + ex.getMessage());
            }
        }
    }

    /**
     * Existing discount periods must stay inside the card's validity window (inclusive; a null bound is
     * open-ended). Periods are never moved, truncated or deleted here: the edit is rejected instead.
     */
    private void validateDiscountsWithinWindow(
            List<BillingTMRateDiscount> discounts,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        if (discounts == null) {
            return;
        }
        for (BillingTMRateDiscount discount : discounts) {
            if (!TmRateCardRules.within(discount.getEffectiveFrom(), discount.getEffectiveTo(),
                    effectiveFrom, effectiveTo)) {
                throw new GlobalExceptionHandler.ValidationException(
                        "The new effective dates would leave the discount period '" + discount.getPeriodName()
                                + "' (" + discount.getEffectiveFrom() + " to " + discount.getEffectiveTo()
                                + ") outside the rate card's validity window ("
                                + (effectiveFrom != null ? effectiveFrom : "no start date") + " to "
                                + (effectiveTo != null ? effectiveTo : "no end date")
                                + "). Adjust or remove the discount period first.");
            }
        }
    }

    private void validateBillingConfiguration(
            BillingConfiguration configuration) {

        if (configuration.getBillingType() == null ||
                !configuration.getBillingType()
                        .getBillingTypeName()
                        .trim()
                        .equalsIgnoreCase("Timesheet Based")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Time & Material Rate Cards can only be created for Time & Material billing.");
        }

        // Removed validation that blocked editing approved configurations.
        // Approved configurations can now be edited, which will trigger
        // the approval state transition to PENDING_APPROVAL + INACTIVE.
    }

    private void validateRequest(
            BillingTMRateCardRequestDto request) {

        if (request.getEffectiveFrom() != null &&
                request.getEffectiveTo() != null &&
                request.getEffectiveFrom()
                        .isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }
    }

    private void validateEffectiveDatesAgainstProjectDuration(
            BillingConfiguration configuration,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        var project = configuration.getProject();
        LocalDate projectStart = project.getStartDate();
        LocalDate projectEnd = project.getEndDate();

        // If project dates are not available, skip validation
        if (projectStart == null || projectEnd == null) {
            return;
        }

        // Validate effectiveFrom is not before project start
        if (effectiveFrom != null && effectiveFrom.isBefore(projectStart)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From date cannot be before Project Start Date ("
                    + projectStart + ").");
        }

        // Validate effectiveTo is not after project end
        if (effectiveTo != null && effectiveTo.isAfter(projectEnd)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective To date cannot be after Project End Date ("
                    + projectEnd + ").");
        }
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
                .includeBaseRateInInvoice(TmRateCalculator.isIncluded(rateCard.getIncludeBaseRateInInvoice()))
                .createdAt(rateCard.getCreatedAt())
                .updatedAt(rateCard.getUpdatedAt())
                .build();
    }

    /**
     * Handles approval state transitions when a billing configuration is edited.
     *
     * Workflow:
     * - DRAFT → Edit → DRAFT (stay in draft)
     * - PENDING_APPROVAL → Edit → PENDING_APPROVAL (stay pending)
     * - APPROVED + ACTIVE → Edit → PENDING_APPROVAL + INACTIVE (require re-approval)
     * - REJECTED → Edit → DRAFT (allow correction)
     *
     * @param configuration The billing configuration being edited
     */
    static void handleApprovalStateTransition(BillingConfiguration configuration) {
        ApprovalStatus currentStatus = configuration.getApprovalStatus();
        if (currentStatus == null) {
            return;
        }

        switch (currentStatus) {
            case DRAFT:
                // Stay in DRAFT - no change needed
                break;

            case PENDING_APPROVAL:
                // Stay in PENDING_APPROVAL - already waiting for approval
                break;

            case APPROVED:
                // APPROVED + ACTIVE → PENDING_APPROVAL + INACTIVE
                // This requires re-approval after editing
                configuration.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
                configuration.setBillingStatus(BillingConfigurationStatus.INACTIVE);
                configuration.setManuallyDeactivated(false);
                configuration.setRejectionReason(null);
                break;

            case REJECTED:
                // REJECTED → DRAFT (allow correction and resubmission)
                configuration.setApprovalStatus(ApprovalStatus.DRAFT);
                configuration.setBillingStatus(BillingConfigurationStatus.INACTIVE);
                configuration.setRejectionReason(null);
                break;
        }
    }
}
