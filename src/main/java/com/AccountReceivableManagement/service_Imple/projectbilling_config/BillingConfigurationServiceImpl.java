package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.*;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.*;
import com.AccountReceivableManagement.entity_enums.client.RecordStatus;
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
    private final BillingSubscriptionConfigurationRepository billingSubscriptionConfigurationRepository;
    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;
    private final BillingTypeMasterRepository billingTypeMasterRepository;
    private final BillingFrequencyMasterRepository billingFrequencyMasterRepository;

    // =========================================================
    // CREATE BILLING CONFIGURATION
    // =========================================================

    @Override
    public BillingConfigurationResponseDto create(
            BillingConfigurationRequestDto request) {

        // Validate Client
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Client not found."));

        // Validate Project
        ProjectMasterReference project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Project not found."));

        // Validate Billing Type
        BillingTypeMaster billingType =
                billingTypeRepository
                        .findByBillingTypeIdAndIsActiveTrue(
                                request.getBillingTypeId())
                        .orElseThrow(() ->
                                new ValidationException(
                                        "Selected Billing Type is inactive or does not exist."));

        // Validate Payment Term
        PaymentTermsMaster paymentTerm =
                paymentTermsRepository.findById(
                                request.getPaymentTermId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Payment Term not found."));

        if (!Boolean.TRUE.equals(paymentTerm.getIsActive())) {
            throw new ValidationException(
                    "Selected Payment Term is inactive.");
        }

        // Validate Billing Frequency
        BillingFrequencyMaster billingFrequency =
                billingFrequencyRepository.findById(
                                request.getBillingFrequencyId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Frequency not found."));

        // Validate Tax Region
        TaxRegionMaster taxRegion =
                taxRegionRepository.findById(
                                request.getTaxRegionId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Tax Region not found."));

        // Validate Invoice Generation Type
        if (request.getInvoiceGenerationType() == null) {
            throw new ValidationException(
                    "Invoice Generation Type is required.");
        }

        // Validate Project belongs to Client
        if (!project.getClientId().equals(client.getClientId())) {
            throw new ValidationException(
                    "Selected project does not belong to the selected client.");
        }

        // Check duplicate active configuration
        boolean configurationExists =
                billingConfigurationRepository
                        .existsByProject_PmsProjectIdAndStatusAndIsActive(
                                request.getProjectId(),
                                BillingConfigurationStatus.APPROVED,
                                true);

        if (configurationExists) {
            throw new GlobalExceptionHandler
                    .DuplicateBillingConfigurationException(
                    "An active billing configuration already exists for this project.");
        }

        // Create Billing Configuration
        BillingConfiguration billingConfiguration =
                new BillingConfiguration();

        billingConfiguration.setClient(client);
        billingConfiguration.setProject(project);
        billingConfiguration.setBillingType(billingType);

        // Currency comes from PMS project
        if (project.getProjectBudgetCurrency() == null ||
                project.getProjectBudgetCurrency().isBlank()) {

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

        billingConfiguration.setCurrency(currency);

        billingConfiguration.setPaymentTerm(paymentTerm);
        billingConfiguration.setBillingFrequency(billingFrequency);
        billingConfiguration.setTaxRegion(taxRegion);

        billingConfiguration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        billingConfiguration.setEffectiveFrom(
                request.getEffectiveFrom());

        billingConfiguration.setEffectiveTo(
                request.getEffectiveTo());

        // Time & Material validation
        if (billingType.getBillingTypeName()
                .equalsIgnoreCase("Time & Material")) {

            if (request.getPricingModel() == null) {
                throw new ValidationException(
                        "Pricing Model is required for Time & Material billing.");
            }

            if (request.getPricingModel() == PricingModel.STANDARD) {

                if (request.getHourlyRate() == null
                        || request.getHourlyRate()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0) {

                    throw new ValidationException(
                            "Hourly Rate is required for Standard Rate pricing.");
                }
            }
        }

        billingConfiguration.setPricingModel(
                request.getPricingModel());

        if (request.getPricingModel() == PricingModel.STANDARD) {
            billingConfiguration.setHourlyRate(
                    request.getHourlyRate());
        } else {
            billingConfiguration.setHourlyRate(null);
        }

        // IMPORTANT:
        // contractValue is no longer stored in BillingConfiguration.
        // It is handled by BillingFixedPriceConfiguration.

        billingConfiguration.setInvoiceGenerationType(
                request.getInvoiceGenerationType());

        billingConfiguration.setStatus(
                BillingConfigurationStatus.DRAFT);

        billingConfiguration.setIsActive(false);

        billingConfiguration.setCreatedAt(
                LocalDateTime.now());

        billingConfiguration.setUpdatedAt(
                LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        billingConfiguration);

        return mapToResponse(saved);
    }


    // =========================================================
    // GET APPROVED CONFIGURATION
    // =========================================================

    @Override
    public BillingConfigurationResponseDto getApprovedByProjectId(
            Long projectId) {

        BillingConfiguration billingConfiguration =
                billingConfigurationRepository
                        .findByProject_PmsProjectIdAndStatusAndIsActive(
                                projectId,
                                BillingConfigurationStatus.APPROVED,
                                true)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No approved billing configuration found for project "
                                                + projectId + "."));

        return mapToResponse(billingConfiguration);
    }


    // =========================================================
    // APPROVE
    // =========================================================

    @Override
    public BillingConfigurationResponseDto approve(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(
                                billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        BillingTypeMaster billingType =
                configuration.getBillingType();

        if (!Boolean.TRUE.equals(billingType.getIsActive())) {
            throw new ValidationException(
                    "Selected Billing Type is inactive and cannot be approved.");
        }

        PaymentTermsMaster paymentTerm =
                configuration.getPaymentTerm();

        if (!Boolean.TRUE.equals(paymentTerm.getIsActive())) {
            throw new ValidationException(
                    "Selected Payment Term is inactive and cannot be approved.");
        }

        BillingFrequencyMaster billingFrequency =
                configuration.getBillingFrequency();

        if (!Boolean.TRUE.equals(billingFrequency.getIsActive())) {
            throw new ValidationException(
                    "Selected Billing Frequency is inactive and cannot be approved.");
        }

        if (configuration.getInvoiceGenerationType() == null) {
            throw new ValidationException(
                    "Invoice Generation Type is required before approval.");
        }

        ProjectMasterReference project =
                configuration.getProject();

        if (project.getProjectBudget() == null) {
            throw new ValidationException(
                    "Project Budget is not available from PMS.");
        }

        if (project.getProjectBudgetCurrency() == null
                || project.getProjectBudgetCurrency().isBlank()) {

            throw new ValidationException(
                    "Project Currency is not available from PMS.");
        }

        boolean configurationExists =
                billingConfigurationRepository
                        .existsByProject_PmsProjectIdAndStatusAndIsActive(
                                configuration.getProject().getPmsProjectId(),
                                BillingConfigurationStatus.APPROVED,
                                true);

        if (configurationExists) {
            throw new GlobalExceptionHandler
                    .DuplicateBillingConfigurationException(
                    "An active billing configuration already exists for this project.");
        }

        // Time & Material validation
        if (billingType.getBillingTypeName()
                .equalsIgnoreCase("Time & Material")) {

            if (configuration.getPricingModel() == null) {
                throw new ValidationException(
                        "Pricing Model is required for Time & Material billing.");
            }

            if (configuration.getPricingModel()
                    == PricingModel.STANDARD) {

                if (configuration.getHourlyRate() == null
                        || configuration.getHourlyRate()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0) {

                    throw new ValidationException(
                            "Standard hourly rate must be configured before approval.");
                }
            }

            if (configuration.getPricingModel()
                    == PricingModel.ROLE_BASED) {

                if (!billingTMRateCardRepository
                        .existsByBillingConfigurationAndIsActiveTrue(
                                configuration)) {

                    throw new ValidationException(
                            "At least one Time & Material Rate Card must be configured before approval.");
                }
            }
        }

        // Fixed Price validation
        if (billingType.getBillingTypeName()
                .equalsIgnoreCase("Fixed Price")) {

            if (!billingFixedPriceRepository
                    .existsByBillingConfigurationAndIsActiveTrue(
                            configuration)) {

                throw new ValidationException(
                        "Fixed Price configuration must be completed before approval.");
            }
        }

        // Subscription validation
        if (billingType.getBillingTypeName()
                .equalsIgnoreCase("Subscription")) {

            if (!billingSubscriptionConfigurationRepository
                    .existsByBillingConfigurationAndIsActiveTrue(
                            configuration)) {

                throw new ValidationException(
                        "Subscription configuration must be completed before approval.");
            }
        }

        configuration.setStatus(
                BillingConfigurationStatus.APPROVED);

        configuration.setIsActive(true);

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
    public BillingConfigurationResponseDto reject(
            UUID billingConfigurationId,
            BillingConfigurationRejectRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(
                                billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        if (configuration.getStatus()
                != BillingConfigurationStatus.DRAFT) {

            throw new ValidationException(
                    "Only Draft Billing Configurations can be rejected.");
        }

        configuration.setStatus(
                BillingConfigurationStatus.REJECTED);

        configuration.setIsActive(false);

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

        BillingTypeMaster billingType =
                configuration.getBillingType();

        PaymentTermsMaster paymentTerm =
                configuration.getPaymentTerm();

        BillingFrequencyMaster billingFrequency =
                configuration.getBillingFrequency();

        TaxRegionMaster taxRegion =
                configuration.getTaxRegion();

        return BillingConfigurationResponseDto.builder()

                .billingConfigurationId(
                        configuration.getBillingConfigurationId())

                // CLIENT
                .clientId(
                        configuration.getClient() != null
                                ? configuration.getClient().getClientId()
                                : null)

                .clientName(
                        configuration.getClient() != null
                                ? configuration.getClient().getClientName()
                                : null)

                // PROJECT
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
                                : null)

                .projectBudgetCurrency(
                        configuration.getProject() != null
                                ? configuration.getProject().getProjectBudgetCurrency()
                                : null)

                // BILLING TYPE
                .billingTypeId(
                        billingType != null
                                ? billingType.getBillingTypeId()
                                : null)

                .billingTypeName(
                        billingType != null
                                ? billingType.getBillingTypeName()
                                : null)

                // CURRENCY
                .currencyId(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency().getCurrencyId()
                                : null)

                .currencyCode(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency().getCurrencyCode()
                                : null)

                .currency(
                        configuration.getCurrency() != null
                                ? configuration.getCurrency().getCurrencyCode()
                                : null)

                // PAYMENT TERM
                .paymentTermId(
                        paymentTerm != null
                                ? paymentTerm.getPaymentTermId()
                                : null)

                .paymentTermCode(
                        paymentTerm != null
                                ? paymentTerm.getPaymentTermName()
                                : null)

                .paymentTermName(
                        paymentTerm != null
                                ? paymentTerm.getPaymentTermName()
                                : null)

                // BILLING FREQUENCY
                .billingFrequencyId(
                        billingFrequency != null
                                ? billingFrequency.getBillingFrequencyId()
                                : null)

                .billingFrequencyName(
                        billingFrequency != null
                                ? billingFrequency.getBillingFrequencyName()
                                : null)

                // TAX REGION
                .taxRegionId(
                        taxRegion != null
                                ? taxRegion.getTaxRegionId()
                                : null)

                .taxRegionName(
                        taxRegion != null
                                ? taxRegion.getTaxRegionName()
                                : null)

                .taxRegionCode(
                        taxRegion != null
                                ? taxRegion.getTaxRegionCode()
                                : null)

                // OTHER CONFIGURATION DATA
                .expenseBillingEligible(
                        configuration.getExpenseBillingEligible())

                .status(
                        configuration.getStatus())

                .effectiveFrom(
                        configuration.getEffectiveFrom())

                .effectiveTo(
                        configuration.getEffectiveTo())

                .isActive(
                        configuration.getIsActive())

                .rejectionReason(
                        configuration.getRejectionReason())

                .pricingModel(
                        configuration.getPricingModel())

                .hourlyRate(
                        configuration.getHourlyRate())

                .invoiceGenerationType(
                        configuration.getInvoiceGenerationType())

                .generationMode(
                        configuration.getGenerationMode())

                .createdAt(
                        configuration.getCreatedAt())

                .updatedAt(
                        configuration.getUpdatedAt())

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
    public List<ProjectResponseDto> getProjects(
            UUID clientId) {

        return projectRepository
                .findByClientIdOrderByProjectNameAsc(clientId)
                .stream()
                .map(project -> {

                    String projectDuration =
                            calculateProjectDuration(
                                    project.getStartDate(),
                                    project.getEndDate());

                    return ProjectResponseDto.builder()
                            .projectId(
                                    project.getPmsProjectId())
                            .projectName(
                                    project.getProjectName())
                            .projectCode(
                                    String.valueOf(
                                            project.getPmsProjectId()))
                            .projectDuration(
                                    projectDuration)
                            .projectBudget(
                                    project.getProjectBudget())
                            .projectBudgetCurrency(
                                    project.getProjectBudgetCurrency())
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
    // ACTIVATE
    // =========================================================

    @Override
    public BillingConfigurationResponseDto activate(
            UUID billingConfigurationId) {

        return approve(billingConfigurationId);
    }


    // =========================================================
    // UPDATE BILLING CONFIGURATION
    // =========================================================

    @Override
    public BillingConfigurationResponseDto updateBillingConfiguration(
            UUID billingConfigurationId,
            BillingConfigurationRequestDto request) {

        // =========================================================
        // FIND EXISTING BILLING CONFIGURATION
        // =========================================================

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found with id: "
                                                + billingConfigurationId));

        /*
         * Inactive approved configurations cannot be modified.
         */
        if (!Boolean.TRUE.equals(configuration.getIsActive())
                && configuration.getStatus()
                == BillingConfigurationStatus.APPROVED) {

            throw new ValidationException(
                    "Inactive Billing Configuration cannot be modified.");
        }

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
        // VALIDATE PAYMENT TERM - OPTIONAL DURING DRAFT
        // =========================================================

        PaymentTermsMaster paymentTerm = null;

        if (request.getPaymentTermId() != null) {

            paymentTerm =
                    paymentTermsRepository.findById(
                                    request.getPaymentTermId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Payment Term not found."));

            if (!Boolean.TRUE.equals(paymentTerm.getIsActive())) {
                throw new ValidationException(
                        "Selected Payment Term is inactive.");
            }
        }

        // =========================================================
        // VALIDATE BILLING FREQUENCY - OPTIONAL DURING DRAFT
        // =========================================================

        BillingFrequencyMaster billingFrequency = null;

        if (request.getBillingFrequencyId() != null) {

            billingFrequency =
                    billingFrequencyRepository.findById(
                                    request.getBillingFrequencyId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Billing Frequency not found."));
        }

        // =========================================================
        // VALIDATE TAX REGION - OPTIONAL DURING DRAFT
        // =========================================================

        TaxRegionMaster taxRegion = null;

        if (request.getTaxRegionId() != null) {

            taxRegion =
                    taxRegionRepository.findById(
                                    request.getTaxRegionId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Tax Region not found."));
        }

        // =========================================================
        // VALIDATE PROJECT BELONGS TO CLIENT
        // =========================================================

        if (!project.getClientId().equals(client.getClientId())) {

            throw new ValidationException(
                    "Selected project does not belong to the selected client.");
        }

        // =========================================================
        // CHECK COMMERCIAL CHANGES
        // =========================================================

        boolean billingTypeChanged =
                configuration.getBillingType() == null
                        || !configuration.getBillingType()
                        .getBillingTypeId()
                        .equals(request.getBillingTypeId());

        boolean paymentTermChanged =
                !Objects.equals(
                        configuration.getPaymentTerm() != null
                                ? configuration.getPaymentTerm().getPaymentTermId()
                                : null,
                        request.getPaymentTermId());

        boolean billingFrequencyChanged =
                !Objects.equals(
                        configuration.getBillingFrequency() != null
                                ? configuration.getBillingFrequency()
                                .getBillingFrequencyId()
                                : null,
                        request.getBillingFrequencyId());

        boolean taxRegionChanged =
                !Objects.equals(
                        configuration.getTaxRegion() != null
                                ? configuration.getTaxRegion().getTaxRegionId()
                                : null,
                        request.getTaxRegionId());

        boolean commercialChange =
                billingTypeChanged
                        || paymentTermChanged
                        || billingFrequencyChanged
                        || taxRegionChanged

                        || !Objects.equals(
                        configuration.getExpenseBillingEligible(),
                        request.getExpenseBillingEligible())

                        || !Objects.equals(
                        configuration.getEffectiveFrom(),
                        request.getEffectiveFrom())

                        || !Objects.equals(
                        configuration.getEffectiveTo(),
                        request.getEffectiveTo())

                        || !Objects.equals(
                        configuration.getPricingModel(),
                        request.getPricingModel())

                        || !Objects.equals(
                        configuration.getHourlyRate(),
                        request.getHourlyRate())

                        || !Objects.equals(
                        configuration.getInvoiceGenerationType(),
                        request.getInvoiceGenerationType());

        // =========================================================
        // UPDATE CLIENT / PROJECT / BILLING TYPE
        // =========================================================

        configuration.setClient(client);
        configuration.setProject(project);
        configuration.setBillingType(billingType);

        // =========================================================
        // CURRENCY COMES FROM PMS PROJECT
        // =========================================================

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

        configuration.setCurrency(currency);

        // =========================================================
        // UPDATE OPTIONAL COMMERCIAL FIELDS
        // =========================================================

        /*
         * Only replace these relationships when the request actually
         * contains them.
         *
         * This is important because the initial draft is created
         * before Payment Term / Billing Frequency / Tax Region are selected.
         */

        if (paymentTerm != null) {
            configuration.setPaymentTerm(paymentTerm);
        }

        if (billingFrequency != null) {
            configuration.setBillingFrequency(billingFrequency);
        }

        if (taxRegion != null) {
            configuration.setTaxRegion(taxRegion);
        }

        // =========================================================
        // UPDATE COMMON FIELDS
        // =========================================================

        configuration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        configuration.setEffectiveFrom(
                request.getEffectiveFrom());

        configuration.setEffectiveTo(
                request.getEffectiveTo());

        configuration.setPricingModel(
                request.getPricingModel());

        // =========================================================
        // TIME & MATERIAL
        // =========================================================

        if (billingType.getBillingTypeName()
                .equalsIgnoreCase("Time & Material")) {

            if (request.getPricingModel() == null) {

                throw new ValidationException(
                        "Pricing Model is required for Time & Material billing.");
            }

            if (request.getPricingModel() == PricingModel.STANDARD) {

                if (request.getHourlyRate() == null
                        || request.getHourlyRate()
                        .compareTo(java.math.BigDecimal.ZERO) <= 0) {

                    throw new ValidationException(
                            "Hourly Rate is required for Standard Rate pricing.");
                }

                configuration.setHourlyRate(
                        request.getHourlyRate());

            } else {

                configuration.setHourlyRate(null);
            }

        } else {

            configuration.setHourlyRate(null);
        }

        // =========================================================
        // INVOICE GENERATION TYPE
        // =========================================================

        if (request.getInvoiceGenerationType() != null) {

            configuration.setInvoiceGenerationType(
                    request.getInvoiceGenerationType());
        }

        // =========================================================
        // STATUS HANDLING
        // =========================================================

        /*
         * If an approved/rejected configuration is commercially
         * modified, send it back to Draft.
         */
        if ((configuration.getStatus()
                == BillingConfigurationStatus.APPROVED
                || configuration.getStatus()
                == BillingConfigurationStatus.REJECTED)
                && commercialChange) {

            configuration.setStatus(
                    BillingConfigurationStatus.ACTIVE);

            configuration.setIsActive(false);

            configuration.setRejectionReason(null);
        }

        // =========================================================
        // FINALIZATION - FINAL "CREATE BILLING SETUP"
        // =========================================================

        /*
         * When finalize=true, this is the final "Create Billing Setup" operation.
         * The configuration becomes APPROVED with isActive=true.
         * When finalize=false or null, this is a normal save/update and remains DRAFT.
         */
        if (Boolean.TRUE.equals(request.getFinalize())) {

            // Validate that all required fields are present before finalizing
            if (paymentTerm == null) {
                throw new ValidationException(
                        "Payment Term is required before finalizing the billing configuration.");
            }

            if (billingFrequency == null) {
                throw new ValidationException(
                        "Billing Frequency is required before finalizing the billing configuration.");
            }

            if (taxRegion == null) {
                throw new ValidationException(
                        "Tax Region is required before finalizing the billing configuration.");
            }

            if (request.getInvoiceGenerationType() == null) {
                throw new ValidationException(
                        "Invoice Generation Type is required before finalizing the billing configuration.");
            }

            // Time & Material specific validation
            if (billingType.getBillingTypeName()
                    .equalsIgnoreCase("Time & Material")) {

                if (request.getPricingModel() == null) {
                    throw new ValidationException(
                            "Pricing Model is required for Time & Material billing before finalization.");
                }

                if (request.getPricingModel() == PricingModel.STANDARD) {
                    if (request.getHourlyRate() == null
                            || request.getHourlyRate()
                            .compareTo(java.math.BigDecimal.ZERO) <= 0) {
                        throw new ValidationException(
                                "Hourly Rate is required for Standard Rate pricing before finalization.");
                    }
                }
            }

            // Set final status
            configuration.setStatus(BillingConfigurationStatus.APPROVED);
            configuration.setIsActive(true);
            configuration.setRejectionReason(null);

        } else {
            // Normal save/update - ensure DRAFT status
            if (configuration.getStatus() != BillingConfigurationStatus.APPROVED
                    && configuration.getStatus() != BillingConfigurationStatus.REJECTED
                    && configuration.getStatus() != BillingConfigurationStatus.ARCHIVED) {
                configuration.setStatus(BillingConfigurationStatus.DRAFT);
                configuration.setIsActive(false);
            }
        }

        // =========================================================
        // UPDATE TIMESTAMP
        // =========================================================

        configuration.setUpdatedAt(
                LocalDateTime.now());

        // =========================================================
        // SAVE
        // =========================================================

        BillingConfiguration saved =
                billingConfigurationRepository.save(
                        configuration);

        // =========================================================
        // RETURN RESPONSE
        // =========================================================

        return mapToResponse(saved);
    }


    // =========================================================
    // DEACTIVATE
    // =========================================================

    @Override
    public void deactivateBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository
                        .findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        /*
         * Already inactive
         */
        if (!Boolean.TRUE.equals(
                configuration.getIsActive())) {

            throw new ValidationException(
                    "Billing Configuration is already inactive.");
        }

        /*
         * Soft deactivate
         */
        configuration.setIsActive(false);

        configuration.setUpdatedAt(
                LocalDateTime.now());

        billingConfigurationRepository.save(
                configuration);
    }

    @Override
    @Transactional
    public void deleteBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(
                                billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."
                                ));

        // Only Draft configurations can be deleted
        if (configuration.getStatus()
                != BillingConfigurationStatus.DRAFT) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Only Draft Billing Configurations can be deleted."
            );
        }

        // Soft delete parent configuration
        configuration.setIsActive(false);
        configuration.setStatus(
                BillingConfigurationStatus.ARCHIVED
        );
        configuration.setUpdatedAt(LocalDateTime.now());

        billingConfigurationRepository.save(configuration);

        // Deactivate Fixed Price configuration if present
        billingFixedPriceRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .ifPresent(fixedPrice -> {

                    fixedPrice.setIsActive(false);
                    fixedPrice.setUpdatedAt(LocalDateTime.now());

                    billingFixedPriceRepository.save(fixedPrice);
                });
    }

    @Override
    @Transactional
    public BillingConfigurationDraftResponseDto createDraft(
            BillingConfigurationDraftRequestDto request) {

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Client not found."));

        ProjectMasterReference project =
                projectMasterReferenceRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Project not found."));

        BillingTypeMaster billingType =
                billingTypeMasterRepository.findById(request.getBillingTypeId())
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Type not found."));

        BillingConfiguration draft =
                BillingConfiguration.builder()
                        .client(client)
                        .project(project)
                        .billingType(billingType)
                        .status(BillingConfigurationStatus.DRAFT)
                        .isActive(false)
                        .expenseBillingEligible(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        BillingConfiguration saved =
                billingConfigurationRepository.save(draft);

        return BillingConfigurationDraftResponseDto.builder()
                .billingConfigurationId(saved.getBillingConfigurationId())
                .clientId(saved.getClient().getClientId())
                .projectId(saved.getProject().getPmsProjectId())
                .billingTypeId(saved.getBillingType().getBillingTypeId())
                .status(saved.getStatus())
                .isActive(saved.getIsActive())
                .build();
    }

}
