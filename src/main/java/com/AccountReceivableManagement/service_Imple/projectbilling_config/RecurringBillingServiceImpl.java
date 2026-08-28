package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.RecurringBillingResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.*;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingPeriodCalculatorService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.RecurringBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RecurringBillingServiceImpl implements RecurringBillingService {

    private final BillingRecurringConfigurationRepository
            billingRecurringRepository;

    private final BillingConfigurationRepository
            billingConfigurationRepository;

    private final BillingFrequencyMasterRepository
            billingFrequencyRepository;

    private final BillingScheduleRepository
            billingScheduleRepository;

    private final BillingPeriodCalculatorService
            billingPeriodCalculatorService;


    @Override
    @Transactional
    public RecurringBillingResponseDto create(
            UUID billingConfigurationId,
            RecurringBillingRequestDto request) {

        /*
         * 1. Get Billing Configuration
         */
        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        /*
         * 2. Billing Configuration must be in DRAFT
         *
         * Recurring configuration is created while
         * the parent Billing Configuration is being configured.
         */
        if (configuration.getApprovalStatus() != ApprovalStatus.DRAFT) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring billing configuration can only be created while the billing configuration is in Draft.");
        }

        /*
         * 3. Validate Billing Type
         */
        if (configuration.getBillingType() == null ||
                configuration.getBillingType().getBillingTypeName() == null ||
                configuration.getBillingType().getBillingTypeName().isBlank()) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Type is required for recurring billing.");
        }

        String billingTypeName =
                configuration.getBillingType()
                        .getBillingTypeName()
                        .trim();

        if (!billingTypeName.equalsIgnoreCase("Subscription") &&
                !billingTypeName.equalsIgnoreCase("Recurring")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Selected Billing Configuration does not support recurring billing.");
        }

        /*
         * 4. Prevent duplicate active recurring configuration
         */
        if (billingRecurringRepository
                .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring configuration already exists.");
        }

        /*
         * 5. Validate recurring request
         */
        validateRecurringBillingRequest(request);

        /*
         * 6. Get Billing Frequency
         */
        BillingFrequencyMaster billingFrequency =
                getBillingFrequency(
                        request.getBillingFrequencyId());

        /*
         * 7. Validate recurring dates against project duration
         */
        validateEffectiveDatesAgainstProjectDuration(
                configuration,
                request.getRecurringStartDate(),
                request.getRecurringEndDate());

        /*
         * 8. Determine Contract Value
         *
         * PMS_BUDGET:
         *     Contract value comes from Project Budget.
         *
         * MANUAL:
         *     Contract value comes from user input.
         */
        BigDecimal contractValue =
                request.getContractValue();

        ContractValueSource contractValueSource =
                request.getContractValueSource();

        if (contractValueSource == null) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Contract value source is required.");
        }

        if (contractValueSource == ContractValueSource.PMS_BUDGET) {

            /*
             * Project must exist.
             */
            if (configuration.getProject() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Project is required when using PMS_BUDGET as contract value source.");
            }

            BigDecimal projectBudget =
                    configuration.getProject()
                            .getProjectBudget();

            /*
             * Project budget must be valid.
             */
            if (projectBudget == null ||
                    projectBudget.compareTo(BigDecimal.ZERO) <= 0) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Project budget must be available when using PMS_BUDGET as contract value source.");
            }

            /*
             * Actual contract value becomes the project budget.
             */
            contractValue = projectBudget;

        } else if (contractValueSource == ContractValueSource.MANUAL) {

            /*
             * Manual contract value must be supplied.
             */
            if (contractValue == null ||
                    contractValue.compareTo(BigDecimal.ZERO) <= 0) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Contract value must be provided when using MANUAL as contract value source.");
            }

        } else {

            throw new GlobalExceptionHandler.ValidationException(
                    "Unsupported contract value source.");
        }

        /*
         * 9. Store the ACTUAL contract value in Billing Configuration.
         *
         * IMPORTANT:
         *
         * Project Budget is stored in:
         *     project_master_reference.project_budget
         *
         * Actual Billing Contract Value is stored in:
         *     billing_configuration.contract_value
         *
         * This allows the approval/review screen to display
         * the actual contracted amount rather than the project budget.
         */
        configuration.setContractValue(contractValue);
        configuration.setUpdatedAt(LocalDateTime.now());

        billingConfigurationRepository.save(configuration);

        /*
         * 10. Create Recurring Configuration
         */
        BillingRecurringConfiguration recurring =
                BillingRecurringConfiguration.builder()
                        .billingConfiguration(configuration)

                        .recurringName(
                                request.getRecurringName())

                        .contractValue(
                                contractValue)

                        .contractValueSource(
                                contractValueSource)

                        .billingFrequency(
                                billingFrequency)

                        .recurringStartDate(
                                request.getRecurringStartDate())

                        .recurringEndDate(
                                request.getRecurringEndDate())

                        .renewalType(
                                request.getRenewalType())

                        .renewalDurationType(
                                request.getRenewalDurationType())

                        .renewalDurationValue(
                                request.getRenewalDurationValue())

                        .renewalDurationUnit(
                                request.getRenewalDurationUnit())

                        .renewalPricingType(
                                request.getRenewalPricingType())

                        .renewalContractValue(
                                request.getRenewalContractValue())

                        .renewalBillingFrequency(
                                getRenewalFrequency(
                                        request.getRenewalBillingFrequencyId()))

                        .renewalEffectiveFrom(
                                request.getRenewalEffectiveFrom())

                        .remarks(
                                request.getRemarks())

                        .isActive(true)

                        .createdAt(
                                LocalDateTime.now())

                        .updatedAt(
                                LocalDateTime.now())

                        .build();

        /*
         * 11. Save Recurring Configuration
         */
        BillingRecurringConfiguration saved =
                billingRecurringRepository.save(recurring);

        /*
         * 12. Generate Billing Schedule
         */
        generateBillingSchedule(
                configuration,
                saved);

        /*
         * 13. Return response
         */
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public RecurringBillingResponseDto update(
            UUID recurringConfigurationId,
            RecurringBillingRequestDto request) {

        BillingRecurringConfiguration recurring =
                billingRecurringRepository.findById(recurringConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Recurring configuration not found."));

        BillingConfiguration configuration =
                recurring.getBillingConfiguration();

        if (configuration == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration is not associated with this recurring configuration.");
        }

        /*
         * Do NOT require the Billing Configuration to be APPROVED/ACTIVE here.
         *
         * Recurring configuration can be edited while the overall
         * Billing Configuration is still in DRAFT/PENDING_APPROVAL.
         *
         * Approval/activation should be validated when the billing
         * configuration is submitted/approved/activated.
         */

        // Validate recurring request
        validateRecurringBillingRequest(request);

        BillingFrequencyMaster billingFrequency =
                getBillingFrequency(request.getBillingFrequencyId());

        if (billingFrequency == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency is required.");
        }

        // Validate effective dates
        validateEffectiveDatesAgainstProjectDuration(
                configuration,
                request.getRecurringStartDate(),
                request.getRecurringEndDate());

        // ---------------------------------------------------------
        // CONTRACT VALUE
        // ---------------------------------------------------------

        BigDecimal contractValue = request.getContractValue();

        ContractValueSource contractValueSource =
                request.getContractValueSource();

        if (contractValueSource == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Contract value source is required.");
        }

        if (contractValueSource == ContractValueSource.PMS_BUDGET) {

            if (configuration.getProject() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Project is not associated with the Billing Configuration.");
            }

            BigDecimal projectBudget =
                    configuration.getProject().getProjectBudget();

            if (projectBudget == null ||
                    projectBudget.compareTo(BigDecimal.ZERO) <= 0) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Project budget must be available when using PMS_BUDGET as contract value source.");
            }

            contractValue = projectBudget;

        } else if (contractValueSource == ContractValueSource.MANUAL) {

            if (contractValue == null ||
                    contractValue.compareTo(BigDecimal.ZERO) <= 0) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Contract value must be provided when using MANUAL as contract value source.");
            }

        } else {

            throw new GlobalExceptionHandler.ValidationException(
                    "Unsupported contract value source.");
        }

        // Store the actual recurring contract value in the parent billing configuration.
        configuration.setContractValue(contractValue);

        configuration.setUpdatedAt(LocalDateTime.now());

        billingConfigurationRepository.save(configuration);

        // ---------------------------------------------------------
        // UPDATE RECURRING CONFIGURATION
        // ---------------------------------------------------------

        recurring.setRecurringName(request.getRecurringName());

        recurring.setContractValue(contractValue);

        recurring.setContractValueSource(contractValueSource);

        recurring.setBillingFrequency(billingFrequency);

        recurring.setRecurringStartDate(
                request.getRecurringStartDate());

        recurring.setRecurringEndDate(
                request.getRecurringEndDate());

        recurring.setRenewalType(
                request.getRenewalType());

        recurring.setRenewalDurationType(
                request.getRenewalDurationType());

        recurring.setRenewalDurationValue(
                request.getRenewalDurationValue());

        recurring.setRenewalDurationUnit(
                request.getRenewalDurationUnit());

        recurring.setRenewalPricingType(
                request.getRenewalPricingType());

        recurring.setRenewalContractValue(
                request.getRenewalContractValue());

        recurring.setRenewalBillingFrequency(
                getRenewalFrequency(
                        request.getRenewalBillingFrequencyId()));

        recurring.setRenewalEffectiveFrom(
                request.getRenewalEffectiveFrom());

        recurring.setRemarks(
                request.getRemarks());

        recurring.setUpdatedAt(LocalDateTime.now());

        BillingRecurringConfiguration saved =
                billingRecurringRepository.save(recurring);

        // ---------------------------------------------------------
        // INVALIDATE OLD SCHEDULES
        // ---------------------------------------------------------

        List<BillingSchedule> existingSchedules =
                billingScheduleRepository
                        .findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
                                saved);

        if (existingSchedules != null && !existingSchedules.isEmpty()) {

            LocalDateTime now = LocalDateTime.now();

            for (BillingSchedule schedule : existingSchedules) {

                /*
                 * Don't modify an already invoiced schedule.
                 * It represents historical billing data.
                 */
                if (Boolean.TRUE.equals(schedule.getIsInvoiced())) {
                    continue;
                }

                schedule.setIsActive(false);
                schedule.setUpdatedAt(now);
            }

            billingScheduleRepository.saveAll(existingSchedules);
        }

        // ---------------------------------------------------------
        // GENERATE NEW SCHEDULE
        // ---------------------------------------------------------

        generateBillingSchedule(
                configuration,
                saved);

        return mapToResponse(saved);
    }

    private void validateRecurringBillingRequest(
            RecurringBillingRequestDto request) {

        // Validate basic date constraints
        if (request.getRecurringStartDate()
                .isAfter(request.getRecurringEndDate())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring Start Date cannot be after End Date.");
        }

        // Conditional validation for recurring name
        // Only required if it's a subscription-based recurring (has renewalType AUTO)
        if (request.getRenewalType() == RenewalType.AUTO && 
            (request.getRecurringName() == null || request.getRecurringName().trim().isEmpty())) {
            
            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring Name is required for subscription-based recurring billing with AUTO renewal.");
        }

        // Conditional validation for renewal fields
        // Only validate renewal fields if renewalType is AUTO
        if (request.getRenewalType() == RenewalType.AUTO) {
            
            if (request.getRenewalDurationType() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Duration Type is required for AUTO renewal.");
            }

            if (request.getRenewalPricingType() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Pricing Type is required for AUTO renewal.");
            }

            if (request.getRenewalBillingFrequencyId() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Billing Frequency is required for AUTO renewal.");
            }

            if (request.getRenewalEffectiveFrom() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Effective From is required for AUTO renewal.");
            }

            LocalDate minimumRenewalDate =
                    request.getRecurringEndDate().plusDays(1);

            if (request.getRenewalEffectiveFrom()
                    .isBefore(minimumRenewalDate)) {

                throw new GlobalExceptionHandler.ValidationException(
                        "Renewal Start Date must be after Recurring End Date.");
            }

            if (request.getRenewalDurationType()
                    == RenewalDurationType.CUSTOM) {

                if (request.getRenewalDurationValue() == null
                        || request.getRenewalDurationValue() <= 0) {

                    throw new GlobalExceptionHandler.ValidationException(
                            "Renewal Duration Value is required for CUSTOM duration type.");
                }

                if (request.getRenewalDurationUnit() == null) {

                    throw new GlobalExceptionHandler.ValidationException(
                            "Renewal Duration Unit is required for CUSTOM duration type.");
                }
            }

            if (request.getRenewalPricingType()
                    == RenewalPricingType.REVISED_PRICE) {

                if (request.getRenewalContractValue() == null
                        || request.getRenewalContractValue()
                        .compareTo(BigDecimal.ZERO) <= 0) {

                    throw new GlobalExceptionHandler.ValidationException(
                            "Renewal Contract Value is required for REVISED_PRICE pricing type.");
                }
            }

            // Clean up null values for certain combinations
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
        // For MANUAL renewal or null renewalType, renewal fields are optional
        // and should not cause validation failures
    }

    private void validateEffectiveDatesAgainstProjectDuration(
            BillingConfiguration configuration,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        var project = configuration.getProject();

        LocalDate projectStart = project.getStartDate();
        LocalDate projectEnd = project.getEndDate();

        if (effectiveFrom != null &&
                effectiveTo != null &&
                effectiveFrom.isAfter(effectiveTo)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From date cannot be after Effective To date.");
        }

        if (projectStart == null || projectEnd == null) {
            return;
        }

        if (effectiveFrom != null &&
                effectiveFrom.isBefore(projectStart)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From date cannot be before Project Start Date ("
                            + projectStart + ").");
        }

        if (effectiveTo != null &&
                effectiveTo.isAfter(projectEnd)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective To date cannot be after Project End Date ("
                            + projectEnd + ").");
        }
    }

    private BillingFrequencyMaster getBillingFrequency(UUID id) {
        if (id == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency is required for recurring billing.");
        }

        BillingFrequencyMaster frequency =
                billingFrequencyRepository.findById(id)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Frequency not found."));

        if (!Boolean.TRUE.equals(frequency.getIsActive())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Selected Billing Frequency is inactive.");
        }

        if (frequency.getDurationValue() == null || frequency.getDurationValue() <= 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency duration value must be positive.");
        }

        if (frequency.getDurationUnit() == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency duration unit is required.");
        }

        return frequency;
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
    public RecurringBillingResponseDto get(
            UUID recurringConfigurationId) {

        BillingRecurringConfiguration recurring =
                billingRecurringRepository.findById(recurringConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Recurring configuration not found."));

        return mapToResponse(recurring);
    }

    @Override
    public List<RecurringBillingResponseDto> getByBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."));

        return billingRecurringRepository
                .findAllByBillingConfigurationAndIsActiveTrue(configuration)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void delete(UUID recurringConfigurationId) {

        BillingRecurringConfiguration recurring =
                billingRecurringRepository.findById(recurringConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Recurring configuration not found."));

        BillingConfiguration configuration =
                recurring.getBillingConfiguration();

        if (configuration != null &&
                configuration.getApprovalStatus() == ApprovalStatus.APPROVED) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Approved Billing Configuration cannot be modified.");
        }

        recurring.setIsActive(false);
        recurring.setUpdatedAt(LocalDateTime.now());

        billingRecurringRepository.save(recurring);
    }

    private RecurringBillingResponseDto mapToResponse(
            BillingRecurringConfiguration recurring) {

        return RecurringBillingResponseDto.builder()
                .recurringConfigurationId(
                        recurring.getRecurringConfigurationId())
                .recurringName(
                        recurring.getRecurringName())
                .contractValue(
                        recurring.getContractValue())
                .contractValueSource(
                        recurring.getContractValueSource())
                .recurringStartDate(
                        recurring.getRecurringStartDate())
                .recurringEndDate(
                        recurring.getRecurringEndDate())
                .renewalType(
                        recurring.getRenewalType())
                .renewalDurationType(
                        recurring.getRenewalDurationType())
                .renewalDurationValue(
                        recurring.getRenewalDurationValue())
                .renewalDurationUnit(
                        recurring.getRenewalDurationUnit())
                .renewalPricingType(
                        recurring.getRenewalPricingType())
                .renewalContractValue(
                        recurring.getRenewalContractValue())
                .renewalBillingFrequencyId(
                        recurring.getRenewalBillingFrequency() != null
                                ? recurring.getRenewalBillingFrequency().getBillingFrequencyId()
                                : null)
                .renewalBillingFrequencyName(
                        recurring.getRenewalBillingFrequency() != null
                                ? recurring.getRenewalBillingFrequency().getBillingFrequencyName()
                                : null)
                .renewalEffectiveFrom(
                        recurring.getRenewalEffectiveFrom())
                .remarks(
                        recurring.getRemarks())
                .createdAt(
                        recurring.getCreatedAt())
                .updatedAt(
                        recurring.getUpdatedAt())
                .build();
    }

    private void generateBillingSchedule(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring) {

        LocalDate startDate = recurring.getRecurringStartDate();

        if (startDate == null) {
            startDate = configuration.getEffectiveFrom();
        }

        if (startDate == null) {
            startDate = configuration.getProject().getStartDate();
        }

        LocalDate endDate = recurring.getRecurringEndDate();

        if (endDate == null) {
            endDate = configuration.getEffectiveTo();
        }

        if (endDate == null) {
            endDate = configuration.getProject().getEndDate();
        }

        if (startDate == null || endDate == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing schedule cannot be generated because billing start or end date is missing.");
        }

        if (startDate.isAfter(endDate)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring billing start date cannot be after the end date.");
        }

        BillingFrequencyMaster frequency =
                recurring.getBillingFrequency();

        if (frequency == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency is required for recurring billing.");
        }

        List<BillingPeriodDto> periods =
                billingPeriodCalculatorService.calculatePeriodsWithAmount(
                        startDate,
                        endDate,
                        frequency.getDurationValue(),
                        frequency.getDurationUnit().toString(),
                        recurring.getContractValue()
                );

        for (BillingPeriodDto periodDto : periods) {

            BillingSchedule schedule =
                    BillingSchedule.builder()
                            .billingConfiguration(configuration)
                            .recurringConfiguration(recurring)
                            .periodNumber(periodDto.getPeriodNumber())
                            .periodStartDate(periodDto.getPeriodStartDate())
                            .periodEndDate(periodDto.getPeriodEndDate())
                            .billingAmount(periodDto.getBillingAmount())
                            .scheduleType(BillingScheduleType.PRIMARY)
                            .isPartialPeriod(periodDto.getIsPartialPeriod())
                            .periodStatus(BillingPeriodStatus.PENDING)
                            .isInvoiced(false)
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

            billingScheduleRepository.save(schedule);
        }

        log.info(
                "Generated {} billing periods for recurring configuration {}",
                periods.size(),
                recurring.getRecurringConfigurationId()
        );
    }

    private void regenerateBillingSchedule(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring) {

        // Delete existing schedules
        billingScheduleRepository.deleteByRecurringConfiguration(recurring);

        // Generate new schedules
        generateBillingSchedule(configuration, recurring);

        log.info("Regenerated billing schedule for recurring configuration {}",
                recurring.getRecurringConfigurationId());
    }

    @Override
    public List<BillingPeriodDto> getBillingSchedule(UUID recurringConfigurationId) {

        BillingRecurringConfiguration recurringConfiguration =
                billingRecurringRepository.findById(recurringConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Recurring billing configuration not found."));

        return billingScheduleRepository
                .findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
                        recurringConfiguration)
                .stream()
                .map(schedule -> BillingPeriodDto.builder()
                        .periodNumber(schedule.getPeriodNumber())
                        .periodStartDate(schedule.getPeriodStartDate())
                        .periodEndDate(schedule.getPeriodEndDate())
                        .billingAmount(schedule.getBillingAmount())
                        .isPartialPeriod(schedule.getIsPartialPeriod())
                        .build())
                .collect(Collectors.toList());
    }
}
