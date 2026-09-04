package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.*;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.*;
import com.AccountReceivableManagement.entity_enums.client.RecordStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.*;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BillingConfigurationServiceImpl implements BillingConfigurationService {
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final ClientRepository clientRepository;
    private final ProjectMasterReferenceRepository projectRepository;
    private final BillingTypeMasterRepository billingTypeRepository;
    private final CurrencyMasterRepository currencyRepository;
    private final PaymentTermsMasterRepository paymentTermsRepository;
    private final BillingFrequencyMasterRepository billingFrequencyRepository;
    private final TaxRegionMasterRepository taxRegionRepository;
    private final BillingTMRateCardRepository billingTMRateCardRepository;
    private final BillingFixedPriceRepository billingFixedPriceRepository;
    private final BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;
    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;
    private final BillingScheduleRepository billingScheduleRepository;

    // =========================================================
    // CREATE BILLING CONFIGURATION
    // =========================================================

    @Override
    @Transactional
    public BillingConfigurationResponseDto create(
            BillingConfigurationRequestDto request) {

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Client not found."));

        ProjectMasterReference project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Project not found."));

        BillingTypeMaster billingType =
                billingTypeRepository
                        .findByBillingTypeIdAndIsActiveTrue(
                                request.getBillingTypeId())
                        .orElseThrow(() ->
                                new ValidationException(
                                        "Selected Billing Type is inactive or does not exist."));

        PaymentTermsMaster paymentTerm =
                paymentTermsRepository.findById(
                                request.getPaymentTermId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Payment Term not found."));

        BillingFrequencyMaster billingFrequency =
                billingFrequencyRepository.findById(
                                request.getBillingFrequencyId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Frequency not found."));

        TaxRegionMaster taxRegion =
                taxRegionRepository.findById(
                                request.getTaxRegionId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Tax Region not found."));

        /*
         * Validate project belongs to selected client.
         */
        if (!Objects.equals(
                project.getClientId(),
                client.getClientId())) {

            throw new ValidationException(
                    "Selected project does not belong to the selected client.");
        }

        /*
         * Validate currency from PMS project.
         */
        if (project.getProjectBudgetCurrency() == null
                || project.getProjectBudgetCurrency().isBlank()) {

            throw new ValidationException(
                    "Project Currency is not available from PMS.");
        }

        CurrencyMaster currency =
                currencyRepository
                        .findByCurrencyCodeIgnoreCase(
                                project.getProjectBudgetCurrency())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Currency not found: "
                                                + project.getProjectBudgetCurrency()));

        /*
         * Validate effective dates against project duration.
         */
        validateEffectiveDatesAgainstProjectDuration(
                project,
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        /*
         * Validate general effective dates.
         */
        validateEffectiveDates(
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        /*
         * Time & Material validation.
         */
        if (billingType.getBillingTypeName()
                .trim()
                .equalsIgnoreCase("Timesheet Based")) {

            if (request.getPricingModel() == null) {
                throw new ValidationException(
                        "Pricing Model is required for Time & Material billing.");
            }

            if (request.getPricingModel() == PricingModel.STANDARD) {

                if (request.getHourlyRate() == null
                        || request.getHourlyRate()
                        .compareTo(BigDecimal.ZERO) <= 0) {

                    throw new ValidationException(
                            "Hourly Rate is required for Standard Rate pricing.");
                }
            }
        }

        /*
         * Create configuration.
         */
        BillingConfiguration configuration =
                new BillingConfiguration();

        configuration.setClient(client);
        configuration.setProject(project);
        configuration.setBillingType(billingType);
        configuration.setCurrency(currency);
        configuration.setPaymentTerm(paymentTerm);
        configuration.setBillingFrequency(billingFrequency);
        configuration.setTaxRegion(taxRegion);

        configuration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        configuration.setEffectiveFrom(
                request.getEffectiveFrom());

        configuration.setEffectiveTo(
                request.getEffectiveTo());

        configuration.setPricingModel(
                request.getPricingModel());

        configuration.setInvoiceGenerationType(
                request.getInvoiceGenerationType());

        /*
         * Hourly rate is applicable only for Standard pricing.
         */
        if (request.getPricingModel() == PricingModel.STANDARD) {
            configuration.setHourlyRate(
                    request.getHourlyRate());
        } else {
            configuration.setHourlyRate(null);
        }

        /*
         * New configuration always starts as:
         *
         * Approval = DRAFT
         * Billing  = INACTIVE
         */
        configuration.setApprovalStatus(
                ApprovalStatus.DRAFT);

        configuration.setBillingStatus(
                BillingConfigurationStatus.INACTIVE);

        configuration.setManuallyDeactivated(false);

        configuration.setRejectionReason(null);

        configuration.setContractValue(
                request.getContractValue()
        );

        LocalDateTime now = LocalDateTime.now();

        configuration.setCreatedAt(now);
        configuration.setUpdatedAt(now);

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        return mapToResponse(saved);
    }


    // =========================================================
    // GET APPROVED CONFIGURATION
    // =========================================================

    public BillingConfigurationResponseDto getApprovedByProjectId(
            Long projectId) {

        BillingConfiguration config =
                billingConfigurationRepository
                        .findByProject_PmsProjectIdAndApprovalStatusAndBillingStatus(
                                projectId,
                                ApprovalStatus.APPROVED,
                                BillingConfigurationStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active approved billing configuration found for project"
                                ));

        return mapToResponse(config);
    }


    // =========================================================
    // APPROVE
    // =========================================================

    @Override
    @Transactional
    public BillingConfigurationResponseDto approve(UUID id) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        if (configuration.getApprovalStatus()
                != ApprovalStatus.PENDING_APPROVAL) {

            throw new ValidationException(
                    "Only configurations pending approval can be approved.");
        }

        validateEffectiveDates(configuration);

        /*
         * Approval is successful.
         */
        configuration.setApprovalStatus(
                ApprovalStatus.APPROVED);

        /*
         * A newly approved configuration must not inherit
         * manual deactivation.
         */
        configuration.setManuallyDeactivated(false);

        /*
         * Determine whether it should immediately become ACTIVE
         * or wait until effectiveFrom.
         */
        LocalDate today = LocalDate.now();

        BillingConfigurationStatus calculatedStatus =
                calculateBillingStatus(
                        configuration,
                        today);

        configuration.setBillingStatus(
                calculatedStatus);

        configuration.setRejectionReason(null);

        configuration.setUpdatedAt(
                LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        return mapToResponse(saved);
    }


    // =========================================================
    // REJECT
    // =========================================================

    @Override
    @Transactional
    public BillingConfigurationResponseDto reject(
            UUID id,
            BillingConfigurationRejectRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        if (configuration.getApprovalStatus()
                != ApprovalStatus.PENDING_APPROVAL) {

            throw new ValidationException(
                    "Only configurations pending approval can be rejected.");
        }

        configuration.setApprovalStatus(
                ApprovalStatus.REJECTED);

        configuration.setBillingStatus(
                BillingConfigurationStatus.INACTIVE);

        /*
         * Rejected configurations must never be considered
         * manually deactivated.
         */
        configuration.setManuallyDeactivated(false);

        configuration.setRejectionReason(
                request.getRejectionReason());

        configuration.setUpdatedAt(
                LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        return mapToResponse(saved);
    }


    // =========================================================
    // MAP TO RESPONSE
    // =========================================================

    private BillingConfigurationResponseDto mapToResponse(
            BillingConfiguration configuration) {

        if (configuration == null) {
            return null;
        }

        BillingConfigurationResponseDto.BillingConfigurationResponseDtoBuilder builder = 
                BillingConfigurationResponseDto.builder()
                .billingConfigurationId(configuration.getBillingConfigurationId())

                .clientId(
                        configuration.getClient() != null
                                ? configuration.getClient().getClientId()
                                : null)

                .clientName(
                        configuration.getClient() != null
                                ? configuration.getClient().getClientName()
                                : null)

                .projectId(
                        configuration.getProject() != null
                                ? configuration.getProject().getPmsProjectId()
                                : null)

                .projectName(
                        configuration.getProject() != null
                                ? configuration.getProject().getProjectName()
                                : null)

                .projectBudget(
                        configuration.getProject() != null
                                ? configuration.getProject().getProjectBudget()
                                : null
                )
                .projectBudgetCurrency(
                        configuration.getProject() != null
                                ? configuration.getProject().getProjectBudgetCurrency()
                                : null
                )

                .contractValue(
                        configuration.getContractValue())

                .billingTypeId(
                        configuration.getBillingType() != null
                                ? configuration.getBillingType().getBillingTypeId()
                                : null)

                .billingTypeName(
                        configuration.getBillingType() != null
                                ? configuration.getBillingType().getBillingTypeName()
                                : null)

                .currencyId(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency().getCurrencyId()
                                : null)

                .currencyCode(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency().getCurrencyCode()
                                : null)

                .paymentTermId(
                        configuration.getPaymentTerm() != null
                                ? configuration.getPaymentTerm().getPaymentTermId()
                                : null)

                .paymentTermName(
                        configuration.getPaymentTerm() != null
                                ? configuration.getPaymentTerm().getPaymentTermName()
                                : null)

                .paymentTermCode(
                        configuration.getPaymentTerm() != null
                                ? String.valueOf(
                                configuration.getPaymentTerm().getPaymentDays())
                                : null)

                .billingFrequencyId(
                        configuration.getBillingFrequency() != null
                                ? configuration.getBillingFrequency()
                                .getBillingFrequencyId()
                                : null)

                .billingFrequencyName(
                        configuration.getBillingFrequency() != null
                                ? configuration.getBillingFrequency()
                                .getBillingFrequencyName()
                                : null)

                .taxRegionId(
                        configuration.getTaxRegion() != null
                                ? configuration.getTaxRegion().getTaxRegionId()
                                : null)

                .taxRegionName(
                        configuration.getTaxRegion() != null
                                ? configuration.getTaxRegion().getTaxRegionName()
                                : null)

                .taxRegionCode(
                        configuration.getTaxRegion() != null
                                ? configuration.getTaxRegion().getTaxRegionCode()
                                : null)

                .pricingModel(configuration.getPricingModel())

                .invoiceGenerationType(
                        configuration.getInvoiceGenerationType())

                .expenseBillingEligible(
                        configuration.getExpenseBillingEligible())

                .approvalStatus(
                        configuration.getApprovalStatus())

                .billingStatus(
                        configuration.getBillingStatus())

                .rejectionReason(
                        configuration.getRejectionReason())

                .effectiveFrom(
                        configuration.getEffectiveFrom())

                .effectiveTo(
                        configuration.getEffectiveTo())

                .hourlyRate(
                        configuration.getHourlyRate())

                .createdAt(
                        configuration.getCreatedAt())

                .updatedAt(
                        configuration.getUpdatedAt());

        // Populate billing-specific data based on billing type
        if (configuration.getBillingType() != null) {
            String billingTypeName = configuration.getBillingType().getBillingTypeName();
            
            if ("Fixed Price".equalsIgnoreCase(billingTypeName)) {
                builder.fixedPriceDetails(getFixedPriceDetails(configuration));
            } else if ("Recurring".equalsIgnoreCase(billingTypeName) || "Subscription".equalsIgnoreCase(billingTypeName)) {
                builder.recurringDetails(getRecurringDetails(configuration));
            } else if ("Timesheet Based".equalsIgnoreCase(billingTypeName.trim())) {
                builder.tmRateCards(getTMRateCards(configuration));
            } else if ("Milestone Based".equalsIgnoreCase(billingTypeName)) {
                builder.milestoneSchedules(getMilestoneSchedules(configuration));
            }
        }

//        // Add change tracking information if configuration is PENDING_APPROVAL
//        if (configuration.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
//            com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationAudit audit =
//                    changeTrackingService.getLatestAudit(configuration);
//
//            if (audit != null) {
//                List<com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationChangeDetail> details =
//                        changeTrackingService.getChangeDetails(audit);
//
//                builder.changes(changeTrackingService.mapChangesToDto(details));
//                builder.previousApprovalStatus(audit.getApprovalStatus().name());
//                builder.previousBillingStatus(audit.getBillingStatus().name());
//            }
//        }

        return builder.build();
    }

//    /**
//     * Clones a BillingConfiguration for audit purposes.
//     * Creates a shallow copy of the entity to capture previous state.
//     */
//    private BillingConfiguration cloneConfiguration(BillingConfiguration original) {
//        if (original == null) {
//            return null;
//        }
//
//        BillingConfiguration clone = new BillingConfiguration();
//        clone.setBillingConfigurationId(original.getBillingConfigurationId());
//        clone.setApprovalStatus(original.getApprovalStatus());
//        clone.setBillingStatus(original.getBillingStatus());
//        clone.setContractValue(original.getContractValue());
//        clone.setEffectiveFrom(original.getEffectiveFrom());
//        clone.setEffectiveTo(original.getEffectiveTo());
//        clone.setHourlyRate(original.getHourlyRate());
//        clone.setPricingModel(original.getPricingModel());
//        clone.setInvoiceGenerationType(original.getInvoiceGenerationType());
//        clone.setExpenseBillingEligible(original.getExpenseBillingEligible());
//        clone.setManuallyDeactivated(original.getManuallyDeactivated());
//        clone.setRejectionReason(original.getRejectionReason());
//
//        return clone;
//    }

//    /**
//     * Tracks general configuration field changes.
//     */
//    private void trackGeneralConfigurationChanges(
//            com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationAudit audit,
//            BillingConfiguration previous,
//            BillingConfiguration current) {
//
//        changeTrackingService.recordChange(audit, "contractValue", "Contract Value", "DECIMAL",
//                previous.getContractValue(), current.getContractValue(), "COMMERCIAL");
//        changeTrackingService.recordChange(audit, "effectiveFrom", "Effective From", "DATE",
//                previous.getEffectiveFrom(), current.getEffectiveFrom(), "DATES");
//        changeTrackingService.recordChange(audit, "effectiveTo", "Effective To", "DATE",
//                previous.getEffectiveTo(), current.getEffectiveTo(), "DATES");
//        changeTrackingService.recordChange(audit, "hourlyRate", "Hourly Rate", "DECIMAL",
//                previous.getHourlyRate(), current.getHourlyRate(), "PRICING");
//        changeTrackingService.recordChange(audit, "pricingModel", "Pricing Model", "ENUM",
//                previous.getPricingModel(), current.getPricingModel(), "PRICING");
//        changeTrackingService.recordChange(audit, "invoiceGenerationType", "Invoice Generation Type", "ENUM",
//                previous.getInvoiceGenerationType(), current.getInvoiceGenerationType(), "BILLING");
//        changeTrackingService.recordChange(audit, "expenseBillingEligible", "Expense Billing Eligible", "BOOLEAN",
//                previous.getExpenseBillingEligible(), current.getExpenseBillingEligible(), "BILLING");
//    }

    private BillingFixedPriceResponseDto getFixedPriceDetails(BillingConfiguration configuration) {
        try {
            return billingFixedPriceRepository
                    .findByBillingConfigurationAndIsActiveTrue(configuration)
                    .map(this::mapFixedPriceToResponse)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error fetching fixed price details for configuration: {}", 
                    configuration.getBillingConfigurationId(), e);
            return null;
        }
    }

    private BillingFixedPriceResponseDto mapFixedPriceToResponse(
            com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration fixedPrice) {
        
        java.math.BigDecimal contractValue = fixedPrice.getContractValue();
        java.math.BigDecimal retentionPercentage = fixedPrice.getRetentionPercentage();
        java.math.BigDecimal advanceReceived = fixedPrice.getAdvanceReceived();

        // Calculate retention amount: Contract Value × Retention % / 100
        java.math.BigDecimal retentionAmount = contractValue
                .multiply(retentionPercentage)
                .divide(java.math.BigDecimal.valueOf(100), 2, 
                        java.math.RoundingMode.HALF_UP);

        // Calculate billable amount: Contract Value − Retention Amount
        java.math.BigDecimal billableAmount = contractValue.subtract(retentionAmount);

        // Calculate remaining receivable: Billable Amount − Advance Received
        java.math.BigDecimal remainingReceivable = billableAmount.subtract(advanceReceived);

        return BillingFixedPriceResponseDto.builder()
                .fixedPriceConfigurationId(fixedPrice.getFixedPriceConfigurationId())
                .billingConfigurationId(
                        fixedPrice.getBillingConfiguration() != null
                                ? fixedPrice.getBillingConfiguration().getBillingConfigurationId()
                                : null)
                .contractValue(contractValue)
                .pmsProjectBudget(fixedPrice.getPmsProjectBudget())
                .contractValueSource(fixedPrice.getContractValueSource())
                .retentionPercentage(retentionPercentage)
                .retentionAmount(retentionAmount)
                .billableAmount(billableAmount)
                .advanceReceived(advanceReceived)
                .remainingReceivable(remainingReceivable)
                .effectiveFrom(fixedPrice.getEffectiveFrom())
                .effectiveTo(fixedPrice.getEffectiveTo())
                .remarks(fixedPrice.getRemarks())
                .isActive(fixedPrice.getIsActive())
                .createdAt(fixedPrice.getCreatedAt())
                .updatedAt(fixedPrice.getUpdatedAt())
                .build();
    }

    private RecurringBillingResponseDto getRecurringDetails(BillingConfiguration configuration) {
        try {
            return billingRecurringConfigurationRepository
                    .findByBillingConfigurationAndIsActiveTrue(configuration)
                    .map(this::mapRecurringToResponse)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error fetching recurring details for configuration: {}", 
                    configuration.getBillingConfigurationId(), e);
            return null;
        }
    }

    private RecurringBillingResponseDto mapRecurringToResponse(
            com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration recurring) {
        
        return RecurringBillingResponseDto.builder()
                .recurringConfigurationId(recurring.getRecurringConfigurationId())
                .recurringName(recurring.getRecurringName())
                .contractValue(recurring.getContractValue())
                .contractValueSource(recurring.getContractValueSource())
                .billingFrequencyId(
                        recurring.getBillingFrequency() != null
                                ? recurring.getBillingFrequency().getBillingFrequencyId()
                                : null)
                .billingFrequencyName(
                        recurring.getBillingFrequency() != null
                                ? recurring.getBillingFrequency().getBillingFrequencyName()
                                : null)
                .recurringStartDate(recurring.getRecurringStartDate())
                .recurringEndDate(recurring.getRecurringEndDate())
                .renewalType(recurring.getRenewalType())
                .renewalDurationType(recurring.getRenewalDurationType())
                .renewalDurationValue(recurring.getRenewalDurationValue())
                .renewalDurationUnit(recurring.getRenewalDurationUnit())
                .renewalPricingType(recurring.getRenewalPricingType())
                .renewalContractValue(recurring.getRenewalContractValue())
                .renewalBillingFrequencyId(
                        recurring.getRenewalBillingFrequency() != null
                                ? recurring.getRenewalBillingFrequency().getBillingFrequencyId()
                                : null)
                .renewalBillingFrequencyName(
                        recurring.getRenewalBillingFrequency() != null
                                ? recurring.getRenewalBillingFrequency().getBillingFrequencyName()
                                : null)
                .renewalEffectiveFrom(recurring.getRenewalEffectiveFrom())
                .remarks(recurring.getRemarks())
                .createdAt(recurring.getCreatedAt())
                .updatedAt(recurring.getUpdatedAt())
                .build();
    }

    private java.util.List<BillingTMRateCardResponseDto> getTMRateCards(BillingConfiguration configuration) {
        try {
            return billingTMRateCardRepository
                    .findByBillingConfigurationAndIsActiveTrueOrderByRoleNameAsc(configuration)
                    .stream()
                    .map(this::mapTMRateCardToResponse)
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching T&M rate cards for configuration: {}", 
                    configuration.getBillingConfigurationId(), e);
            return java.util.Collections.emptyList();
        }
    }

    private BillingTMRateCardResponseDto mapTMRateCardToResponse(
            com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard rateCard) {
        
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

    private java.util.List<BillingScheduleResponseDto> getMilestoneSchedules(BillingConfiguration configuration) {
        try {
            return billingScheduleRepository
                    .findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration)
                    .stream()
                    .map(this::mapScheduleToResponse)
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching milestone schedules for configuration: {}", 
                    configuration.getBillingConfigurationId(), e);
            return java.util.Collections.emptyList();
        }
    }

    private BillingScheduleResponseDto mapScheduleToResponse(
            com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule schedule) {
        
        return BillingScheduleResponseDto.builder()
                .billingScheduleId(schedule.getBillingScheduleId())
                .periodNumber(schedule.getPeriodNumber())
                .periodStartDate(schedule.getPeriodStartDate())
                .periodEndDate(schedule.getPeriodEndDate())
                .billingAmount(schedule.getBillingAmount())
                .scheduleType(schedule.getScheduleType())
                .isPartialPeriod(schedule.getIsPartialPeriod())
                .periodStatus(schedule.getPeriodStatus())
                .isInvoiced(schedule.getIsInvoiced())
                .invoiceDate(schedule.getInvoiceDate())
                .remarks(schedule.getRemarks())
                .build();
    }


    // =========================================================
    // GET CLIENTS
    // =========================================================

    @Override
    public List<ClientResponseDto> getClients() {

        return clientRepository
                .findByStatusOrderByClientNameAsc(
                        RecordStatus.ACTIVE)
                .stream()
                .map(client ->
                        ClientResponseDto.builder()
                                .clientId(client.getClientId())
                                .clientName(client.getClientName())
                                .build())
                .toList();
    }


    // =========================================================
    // GET PROJECTS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getProjects(UUID clientId) {

        // Get all projects for the client
        List<ProjectMasterReference> allProjects =
                projectRepository.findByClientIdOrderByProjectNameAsc(clientId);

        // Get projects that already have an APPROVED and ACTIVE
        // billing configuration.
        List<Long> configuredProjectIds =
                billingConfigurationRepository
                        .findByClientClientId(clientId)
                        .stream()
                        .filter(config ->
                                config.getApprovalStatus()
                                        == ApprovalStatus.APPROVED
                                        &&
                                        config.getBillingStatus()
                                                == BillingConfigurationStatus.ACTIVE
                        )
                        .map(config ->
                                config.getProject() != null
                                        ? config.getProject().getPmsProjectId()
                                        : null
                        )
                        .filter(java.util.Objects::nonNull)
                        .toList();

        return allProjects
                .stream()

                // Do not show projects that already have
                // an approved + active billing configuration.
                .filter(project ->
                        !configuredProjectIds.contains(
                                project.getPmsProjectId()
                        )
                )

                .map(project -> {

                    String projectDuration =
                            calculateProjectDuration(
                                    project.getStartDate(),
                                    project.getEndDate()
                            );

                    return ProjectResponseDto.builder()
                            .projectId(
                                    project.getPmsProjectId()
                            )
                            .projectName(
                                    project.getProjectName()
                            )
                            .projectCode(
                                    String.valueOf(
                                            project.getPmsProjectId()
                                    )
                            )
                            .projectDuration(
                                    projectDuration
                            )
                            .projectBudget(
                                    project.getProjectBudget()
                            )
                            .projectBudgetCurrency(
                                    project.getProjectBudgetCurrency()
                            )
                            .build();
                })
                .toList();
    }


    // =========================================================
    // PROJECT DURATION
    // =========================================================

    private String calculateProjectDuration(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            return null;
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd-MMM-yyyy");

        return startDate.format(formatter)
                + " to "
                + endDate.format(formatter);
    }


    // =========================================================
    // EFFECTIVE DATE VALIDATION AGAINST PROJECT DURATION
    // =========================================================

    private void validateEffectiveDatesAgainstProjectDuration(
            ProjectMasterReference project,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        LocalDate projectStart = project.getStartDate();
        LocalDate projectEnd = project.getEndDate();

        // If project dates are not available, skip validation
        if (projectStart == null || projectEnd == null) {
            return;
        }

        // Validate effectiveFrom is not before project start
        if (effectiveFrom != null && effectiveFrom.isBefore(projectStart)) {
            throw new ValidationException(
                    "Effective From date cannot be before Project Start Date (" 
                    + projectStart + ").");
        }

        // Validate effectiveTo is not after project end
        if (effectiveTo != null && effectiveTo.isAfter(projectEnd)) {
            throw new ValidationException(
                    "Effective To date cannot be after Project End Date (" 
                    + projectEnd + ").");
        }

        // Validate effectiveFrom is not after effectiveTo
        if (effectiveFrom != null && effectiveTo != null 
                && effectiveFrom.isAfter(effectiveTo)) {
            throw new ValidationException(
                    "Effective From date cannot be after Effective To date.");
        }
    }


    // =========================================================
    // GET BILLING CONFIGURATION
    // =========================================================

    @Override
    public BillingConfigurationResponseDto getBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration billingConfiguration =
                billingConfigurationRepository
                        .findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found with id: "
                                                + billingConfigurationId));

        return mapToResponse(billingConfiguration);
    }


    // =========================================================
    // GET ALL BILLING CONFIGURATIONS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<BillingConfigurationResponseDto> getAllBillingConfigurations() {

        List<BillingConfiguration> configurations =
                billingConfigurationRepository.findAll();

        return configurations.stream()
                .map(this::mapToResponse)
                .toList();
    }



    // =========================================================
    // UPDATE BILLING CONFIGURATION
    // =========================================================

    @Override
    @Transactional
    public BillingConfigurationResponseDto updateBillingConfiguration(
            UUID billingConfigurationId,
            BillingConfigurationRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(
                                billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));


        Client client =
                clientRepository.findById(request.getClientId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found."));

        ProjectMasterReference project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Project not found."));

        BillingTypeMaster billingType =
                billingTypeRepository
                        .findByBillingTypeIdAndIsActiveTrue(
                                request.getBillingTypeId())
                        .orElseThrow(() ->
                                new ValidationException(
                                        "Selected Billing Type is inactive or does not exist."));

        PaymentTermsMaster paymentTerm =
                paymentTermsRepository.findById(
                                request.getPaymentTermId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Payment Term not found."));

        BillingFrequencyMaster billingFrequency =
                billingFrequencyRepository.findById(
                                request.getBillingFrequencyId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Frequency not found."));

        TaxRegionMaster taxRegion =
                taxRegionRepository.findById(
                                request.getTaxRegionId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Tax Region not found."));

        /*
         * Currency comes from the project/PMS.
         */
        if (project.getProjectBudgetCurrency() == null
                || project.getProjectBudgetCurrency().isBlank()) {

            throw new ValidationException(
                    "Project Currency is not available from PMS.");
        }

        CurrencyMaster currency =
                currencyRepository
                        .findByCurrencyCodeIgnoreCase(
                                project.getProjectBudgetCurrency())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Currency not found: "
                                                + project.getProjectBudgetCurrency()));

        /*
         * Validate dates.
         */
        validateEffectiveDates(
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        validateEffectiveDatesAgainstProjectDuration(
                project,
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        /*
         * Update master-data relationships.
         */
        configuration.setClient(client);
        configuration.setProject(project);
        configuration.setBillingType(billingType);
        configuration.setCurrency(currency);
        configuration.setPaymentTerm(paymentTerm);
        configuration.setBillingFrequency(billingFrequency);
        configuration.setTaxRegion(taxRegion);

        configuration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        configuration.setEffectiveFrom(
                request.getEffectiveFrom());

        configuration.setEffectiveTo(
                request.getEffectiveTo());

        configuration.setPricingModel(
                request.getPricingModel());

        configuration.setInvoiceGenerationType(
                request.getInvoiceGenerationType());

        /*
         * Hourly rate only applies to Standard pricing.
         */
        if (request.getPricingModel() == PricingModel.STANDARD) {
            configuration.setHourlyRate(
                    request.getHourlyRate());
        } else {
            configuration.setHourlyRate(null);
        }

        /*
         * Update contract value from request.
         * Note: For billing types with specific configurations (Fixed Price, Recurring),
         * the contract value will be updated by those services after their specific calculations.
         * This update handles cases where contract value is set directly on the configuration.
         */
        configuration.setContractValue(
                request.getContractValue()
        );

        /*
         * Handle approval state transitions based on current status.
         * This implements the required workflow:
         * - DRAFT → Edit → DRAFT (stay in draft)
         * - PENDING_APPROVAL → Edit → PENDING_APPROVAL (stay pending)
         * - APPROVED + ACTIVE → Edit → PENDING_APPROVAL + INACTIVE (require re-approval)
         * - REJECTED → Edit → DRAFT (allow correction)
         */
        boolean needsStateTransitionSave = handleApprovalStateTransition(configuration);

        if (!needsStateTransitionSave) {
            // Only update timestamp if no state transition occurred
            configuration.setUpdatedAt(LocalDateTime.now());
        }

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);


        return mapToResponse(saved);
    }


    // =========================================================
    // APPROVAL STATE TRANSITION HELPER
    // =========================================================

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
     * @return true if the parent configuration needs to be saved with updated approval state
     */
    private boolean handleApprovalStateTransition(BillingConfiguration configuration) {
        ApprovalStatus currentStatus = configuration.getApprovalStatus();
        BillingConfigurationStatus currentBillingStatus = configuration.getBillingStatus();
        
        boolean needsSave = false;
        
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
                configuration.setUpdatedAt(LocalDateTime.now());
                needsSave = true;
                log.info("Edited APPROVED+ACTIVE configuration {} sent back to PENDING_APPROVAL+INACTIVE",
                        configuration.getBillingConfigurationId());
                break;
                
            case REJECTED:
                // REJECTED → DRAFT (allow correction and resubmission)
                configuration.setApprovalStatus(ApprovalStatus.DRAFT);
                configuration.setBillingStatus(BillingConfigurationStatus.INACTIVE);
                configuration.setRejectionReason(null);
                configuration.setUpdatedAt(LocalDateTime.now());
                needsSave = true;
                log.info("Edited REJECTED configuration {} moved to DRAFT",
                        configuration.getBillingConfigurationId());
                break;
        }
        
        return needsSave;
    }

    // =========================================================
    // DEACTIVATE
    // =========================================================

    @Override
    @Transactional
    public void deactivateBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository
                        .findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        /*
         * Only APPROVED configurations can be manually deactivated.
         */
        if (configuration.getApprovalStatus()
                != ApprovalStatus.APPROVED) {

            throw new ValidationException(
                    "Only approved billing configurations can be deactivated.");
        }

        /*
         * Only currently ACTIVE configurations can be deactivated.
         */
        if (configuration.getBillingStatus()
                != BillingConfigurationStatus.ACTIVE) {

            throw new ValidationException(
                    "Only active billing configurations can be deactivated.");
        }

        /*
         * Manual deactivation.
         *
         * This flag is important because the scheduler must NOT
         * automatically reactivate this configuration later.
         */
        configuration.setBillingStatus(
                BillingConfigurationStatus.INACTIVE);

        configuration.setManuallyDeactivated(true);

        configuration.setUpdatedAt(
                LocalDateTime.now());

        billingConfigurationRepository.save(
                configuration);
    }

    @Transactional
    public void deleteBillingConfiguration(UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing configuration not found."));

        // Only allow deletion of drafts
        if (configuration.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Only draft billing configurations can be deleted.");
        }

        // Delete child configurations first
        billingFixedPriceRepository
                .deleteByBillingConfiguration(configuration);

        billingRecurringConfigurationRepository
                .deleteByBillingConfiguration(configuration);

        billingScheduleRepository
                .deleteByBillingConfiguration(configuration);

        // Finally delete parent
        billingConfigurationRepository.delete(configuration);
    }

    @Override
    @Transactional
    public BillingConfigurationDraftResponseDto createDraft(
            BillingConfigurationDraftRequestDto request) {

        // =========================================================
        // VALIDATE CLIENT
        // =========================================================
        Client client =
                clientRepository.findById(request.getClientId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found."));

        // =========================================================
        // VALIDATE PROJECT
        // =========================================================
        ProjectMasterReference project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Project not found."));

        // =========================================================
        // VALIDATE BILLING TYPE
        // =========================================================
        BillingTypeMaster billingType =
                billingTypeRepository
                        .findByBillingTypeIdAndIsActiveTrue(
                                request.getBillingTypeId())
                        .orElseThrow(() ->
                                new ValidationException(
                                        "Selected Billing Type is inactive or does not exist."));

        // =========================================================
        // VALIDATE PROJECT BELONGS TO CLIENT
        // =========================================================
        if (!project.getClientId().equals(client.getClientId())) {
            throw new ValidationException(
                    "Selected project does not belong to the selected client.");
        }

        // =========================================================
        // CREATE DRAFT
        // =========================================================
        BillingConfiguration configuration =
                new BillingConfiguration();

        configuration.setClient(client);
        configuration.setProject(project);

        // IMPORTANT:
        // Billing Type MUST be set during draft creation.
        configuration.setBillingType(billingType);

        // =========================================================
        // DRAFT STATUS
        // =========================================================
        configuration.setApprovalStatus(
                ApprovalStatus.DRAFT);

        configuration.setBillingStatus(
                BillingConfigurationStatus.INACTIVE);

        configuration.setManuallyDeactivated(false);

        // =========================================================
        // OPTIONAL FIELDS
        // =========================================================

        configuration.setPricingModel(
                request.getPricingModel());

        configuration.setInvoiceGenerationType(
                request.getInvoiceGenerationType());

        configuration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        configuration.setEffectiveFrom(
                request.getEffectiveFrom());

        configuration.setEffectiveTo(
                request.getEffectiveTo());

        configuration.setHourlyRate(
                request.getHourlyRate());

        // =========================================================
        // SAVE
        // =========================================================
        configuration.setCreatedAt(
                LocalDateTime.now());

        configuration.setUpdatedAt(
                LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        return mapToDraftResponse(saved);
    }

    @Override
    @Transactional
    public BillingConfigurationDraftResponseDto saveDraft(
            UUID billingConfigurationId,
            BillingConfigurationDraftRequestDto request) {

        // =========================================================
        // FIND EXISTING CONFIGURATION
        // =========================================================
        BillingConfiguration configuration =
                billingConfigurationRepository.findById(
                                billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        // =========================================================
        // ONLY DRAFT CAN BE EDITED
        // =========================================================
        if (configuration.getApprovalStatus()
                != ApprovalStatus.DRAFT) {

            throw new ValidationException(
                    "Only draft billing configurations can be edited.");
        }

        // =========================================================
        // FIND CLIENT
        // =========================================================
        Client client =
                clientRepository.findById(request.getClientId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found."));

        // =========================================================
        // FIND PROJECT
        // =========================================================
        ProjectMasterReference project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Project not found."));

        // =========================================================
        // FIND BILLING TYPE
        // =========================================================
        BillingTypeMaster billingType =
                billingTypeRepository
                        .findByBillingTypeIdAndIsActiveTrue(
                                request.getBillingTypeId())
                        .orElseThrow(() ->
                                new ValidationException(
                                        "Selected Billing Type is inactive or does not exist."));

        // =========================================================
        // UPDATE CLIENT / PROJECT / BILLING TYPE
        // =========================================================
        configuration.setClient(client);
        configuration.setProject(project);
        configuration.setBillingType(billingType);

        // =========================================================
        // UPDATE OPTIONAL COMMERCIAL FIELDS
        // =========================================================

        if (request.getBillingFrequencyId() != null) {

            BillingFrequencyMaster billingFrequency =
                    billingFrequencyRepository
                            .findById(request.getBillingFrequencyId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Billing Frequency not found."));

            configuration.setBillingFrequency(
                    billingFrequency);
        }

        if (request.getPaymentTermId() != null) {

            PaymentTermsMaster paymentTerm =
                    paymentTermsRepository
                            .findById(request.getPaymentTermId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Payment Term not found."));

            configuration.setPaymentTerm(
                    paymentTerm);
        }

        if (request.getTaxRegionId() != null) {

            TaxRegionMaster taxRegion =
                    taxRegionRepository
                            .findById(request.getTaxRegionId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Tax Region not found."));

            configuration.setTaxRegion(
                    taxRegion);
        }

        // =========================================================
        // CURRENCY
        // =========================================================
        if (request.getCurrencyId() != null) {

            CurrencyMaster currency =
                    currencyRepository
                            .findById(request.getCurrencyId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Currency not found."));

            configuration.setCurrency(currency);

        } else if (request.getCurrency() != null
                && !request.getCurrency().isBlank()) {

            CurrencyMaster currency =
                    currencyRepository
                            .findByCurrencyCodeIgnoreCase(
                                    request.getCurrency())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Currency not found: "
                                                    + request.getCurrency()));

            configuration.setCurrency(currency);
        }

        // =========================================================
        // COMMON FIELDS
        // =========================================================
        configuration.setPricingModel(
                request.getPricingModel());

        configuration.setInvoiceGenerationType(
                request.getInvoiceGenerationType());

        configuration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        configuration.setEffectiveFrom(
                request.getEffectiveFrom());

        configuration.setEffectiveTo(
                request.getEffectiveTo());

        configuration.setHourlyRate(
                request.getHourlyRate());

        // =========================================================
        // DRAFT STATUS
        // =========================================================
        configuration.setApprovalStatus(
                ApprovalStatus.DRAFT);

        configuration.setBillingStatus(
                BillingConfigurationStatus.INACTIVE);

        /*
         * Important:
         * This is a normal draft save.
         * It must never activate the configuration.
         */
        configuration.setManuallyDeactivated(false);

        configuration.setRejectionReason(null);

        configuration.setUpdatedAt(
                LocalDateTime.now());

        // =========================================================
        // SAVE
        // =========================================================
        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        return mapToDraftResponse(saved);
    }

    private BillingConfigurationDraftResponseDto mapToDraftResponse(
            BillingConfiguration configuration) {

        return BillingConfigurationDraftResponseDto.builder()

                .billingConfigurationId(
                        configuration.getBillingConfigurationId())

                .clientId(
                        configuration.getClient() != null
                                ? configuration.getClient().getClientId()
                                : null)

                .projectId(
                        configuration.getProject() != null
                                ? configuration.getProject().getPmsProjectId()
                                : null)

                .billingTypeId(
                        configuration.getBillingType() != null
                                ? configuration.getBillingType()
                                .getBillingTypeId()
                                : null)

                .billingFrequencyId(
                        configuration.getBillingFrequency() != null
                                ? configuration.getBillingFrequency()
                                .getBillingFrequencyId()
                                : null)

                .currencyId(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency()
                                .getCurrencyId()
                                : null)

                .currency(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency()
                                .getCurrencyCode()
                                : null)

                .paymentTermId(
                        configuration.getPaymentTerm() != null
                                ? configuration.getPaymentTerm()
                                .getPaymentTermId()
                                : null)

                .taxRegionId(
                        configuration.getTaxRegion() != null
                                ? configuration.getTaxRegion()
                                .getTaxRegionId()
                                : null)

                .pricingModel(
                        configuration.getPricingModel())

                .invoiceGenerationType(
                        configuration.getInvoiceGenerationType())

                .expenseBillingEligible(
                        configuration.getExpenseBillingEligible())

                .effectiveFrom(
                        configuration.getEffectiveFrom())

                .effectiveTo(
                        configuration.getEffectiveTo())

                .hourlyRate(
                        configuration.getHourlyRate())

                /*
                 * New status model:
                 *
                 * Draft = INACTIVE until submitted/approved.
                 */
                .status(
                        configuration.getBillingStatus())

                /*
                 * Draft should not be active.
                 */
                .isActive(
                        configuration.getBillingStatus()
                                == BillingConfigurationStatus.ACTIVE)

                .build();
    }

    @Override
    @Transactional
    public BillingConfigurationResponseDto submitForApproval(
            UUID id) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        if (configuration.getApprovalStatus()
                != ApprovalStatus.DRAFT) {

            throw new ValidationException(
                    "Only draft configurations can be submitted for approval.");
        }

        validateEffectiveDates(configuration);

        configuration.setApprovalStatus(
                ApprovalStatus.PENDING_APPROVAL);

        /*
         * Pending approval configurations are never billable.
         */
        configuration.setBillingStatus(
                BillingConfigurationStatus.INACTIVE);

        configuration.setManuallyDeactivated(false);

        configuration.setUpdatedAt(
                LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        return mapToResponse(saved);
    }

    private void validateEffectiveDates(
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        if (effectiveFrom == null) {

            throw new ValidationException(
                    "Effective From date is required.");
        }

        if (effectiveTo != null
                && effectiveTo.isBefore(effectiveFrom)) {

            throw new ValidationException(
                    "Effective To date cannot be before Effective From date.");
        }
    }

    private void validateEffectiveDates(
            BillingConfiguration configuration) {

        if (configuration == null) {

            throw new ValidationException(
                    "Billing Configuration is required.");
        }

        validateEffectiveDates(
                configuration.getEffectiveFrom(),
                configuration.getEffectiveTo());
    }

    @Transactional
    public BillingConfigurationResponseDto expire(UUID id) {

        BillingConfiguration config =
                billingConfigurationRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Billing configuration not found"));

        if (config.getBillingStatus()
                != BillingConfigurationStatus.ACTIVE) {

            throw new RuntimeException(
                    "Only active configurations can expire"
            );
        }

        config.setBillingStatus(
                BillingConfigurationStatus.EXPIRED
        );

        config.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(
                billingConfigurationRepository.save(config)
        );
    }

    private BillingConfigurationStatus calculateBillingStatus(
            BillingConfiguration configuration,
            LocalDate today) {

        /*
         * Only APPROVED configurations can become ACTIVE.
         */
        if (configuration.getApprovalStatus()
                != ApprovalStatus.APPROVED) {

            return BillingConfigurationStatus.INACTIVE;
        }

        /*
         * Manually deactivated configurations must stay inactive.
         * The scheduler also uses this method.
         */
        if (Boolean.TRUE.equals(
                configuration.getManuallyDeactivated())) {

            return BillingConfigurationStatus.INACTIVE;
        }

        /*
         * Effective From is mandatory.
         */
        if (configuration.getEffectiveFrom() == null) {

            return BillingConfigurationStatus.INACTIVE;
        }

        /*
         * Billing period has not started yet.
         */
        if (today.isBefore(
                configuration.getEffectiveFrom())) {

            return BillingConfigurationStatus.INACTIVE;
        }

        /*
         * Billing period has ended.
         */
        if (configuration.getEffectiveTo() != null
                && today.isAfter(
                configuration.getEffectiveTo())) {

            return BillingConfigurationStatus.EXPIRED;
        }

        /*
         * Configuration is within its effective period.
         */
        return BillingConfigurationStatus.ACTIVE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingConfigurationResponseDto> getPendingApprovals() {

        return billingConfigurationRepository
                .findByApprovalStatus(ApprovalStatus.PENDING_APPROVAL)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

}
