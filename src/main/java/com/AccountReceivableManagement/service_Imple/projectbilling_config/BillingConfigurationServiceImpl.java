package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.*;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.*;
import com.AccountReceivableManagement.entity_enums.client.RecordStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.*;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    @Override
    public BillingConfigurationResponseDto create(BillingConfigurationRequestDto request) {

        // Validate Client
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Client not found."));

        // Validate Project
        ProjectMasterReference project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Project not found."));

        // Validate Billing Type
        BillingTypeMaster billingType = billingTypeRepository
                .findByBillingTypeIdAndIsActiveTrue(request.getBillingTypeId())
                .orElseThrow(() ->
                        new ValidationException(
                                "Selected Billing Type is inactive or does not exist."));

// Validate Currency
        CurrencyMaster currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Currency not found."));

// Validate Payment Term
        PaymentTermsMaster paymentTerm = paymentTermsRepository.findById(request.getPaymentTermId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment Term not found."));

        if (!Boolean.TRUE.equals(paymentTerm.getIsActive())) {
            throw new ValidationException(
                    "Selected Payment Term is inactive.");
        }

// Validate Billing Frequency
        BillingFrequencyMaster billingFrequency = billingFrequencyRepository.findById(request.getBillingFrequencyId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Billing Frequency not found."));

// Validate Tax Region
        TaxRegionMaster taxRegion = taxRegionRepository.findById(request.getTaxRegionId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tax Region not found."));

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
            throw new GlobalExceptionHandler.DuplicateBillingConfigurationException(
                    "An active billing configuration already exists for this project.");
        }

        // Create Billing Configuration
        BillingConfiguration billingConfiguration = new BillingConfiguration();

        billingConfiguration.setClient(client);
        billingConfiguration.setProject(project);
        billingConfiguration.setBillingType(billingType);

        billingConfiguration.setCurrency(currency);

        billingConfiguration.setPaymentTerm(paymentTerm);

        billingConfiguration.setBillingFrequency(billingFrequency);

        billingConfiguration.setTaxRegion(taxRegion);

        billingConfiguration.setExpenseBillingEligible(
                request.getExpenseBillingEligible());

        billingConfiguration.setEffectiveFrom(request.getEffectiveFrom());
        billingConfiguration.setEffectiveTo(request.getEffectiveTo());

        billingConfiguration.setHourlyRate(request.getHourlyRate());
        billingConfiguration.setContractValue(request.getContractValue());

        billingConfiguration.setStatus(BillingConfigurationStatus.DRAFT);
        billingConfiguration.setIsActive(false);

        billingConfiguration.setCreatedAt(LocalDateTime.now());
        billingConfiguration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(billingConfiguration);

        // Response
        return mapToResponse(saved);
    }

    @Override
    public BillingConfigurationResponseDto getApprovedByProjectId(Long projectId) {
        BillingConfiguration billingConfiguration =
                billingConfigurationRepository
                        .findByProject_PmsProjectIdAndStatusAndIsActive(
                                projectId,
                                BillingConfigurationStatus.APPROVED,
                                true)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No approved billing configuration found for project " + projectId + "."));

        return mapToResponse(billingConfiguration);
    }

    @Override
    public BillingConfigurationResponseDto approve(UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        BillingTypeMaster billingType = configuration.getBillingType();

        if (!Boolean.TRUE.equals(billingType.getIsActive())) {
            throw new ValidationException(
                    "Selected Billing Type is inactive and cannot be approved.");
        }

        PaymentTermsMaster paymentTerm = configuration.getPaymentTerm();

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

//        TaxRegionMaster taxRegion =
//                configuration.getTaxRegion();
//
//        if (!Boolean.TRUE.equals(taxRegion.getIsActive())) {
//
//            throw new ValidationException(
//                    "Selected Tax Region is inactive and cannot be approved.");
//        }

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
            throw new GlobalExceptionHandler.DuplicateBillingConfigurationException(
                    "An active billing configuration already exists for this project.");
        }

        if (billingType.getBillingTypeName().equalsIgnoreCase("Time & Material")) {

            if (!billingTMRateCardRepository.existsByBillingConfigurationAndIsActiveTrue(configuration)) {

                throw new ValidationException(
                        "At least one Time & Material Rate Card must be configured before approval.");
            }
        }

        if (billingType.getBillingTypeName().equalsIgnoreCase("Fixed Price")) {

            if (!billingFixedPriceRepository
                    .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

                throw new ValidationException(
                        "Fixed Price configuration must be completed before approval.");
            }
        }

        if (billingType.getBillingTypeName()
                .equalsIgnoreCase("Subscription")) {

            if (!billingSubscriptionConfigurationRepository
                    .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

                throw new ValidationException(
                        "Subscription configuration must be completed before approval.");
            }
        }

        configuration.setStatus(BillingConfigurationStatus.APPROVED);

        configuration.setIsActive(true);

        configuration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(configuration);

        return mapToResponse(saved);
    }

    @Override
    public BillingConfigurationResponseDto reject(
            UUID billingConfigurationId,
            BillingConfigurationRejectRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        if (configuration.getStatus() != BillingConfigurationStatus.DRAFT) {
            throw new ValidationException(
                    "Only Draft Billing Configurations can be rejected.");
        }

        configuration.setStatus(BillingConfigurationStatus.REJECTED);

        configuration.setIsActive(false);

        configuration.setRejectionReason(request.getRejectionReason());

        configuration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(configuration);

        return mapToResponse(saved);
    }

    private BillingConfigurationResponseDto mapToResponse(BillingConfiguration configuration) {

        BillingTypeMaster billingType = configuration.getBillingType();
        CurrencyMaster currency = configuration.getCurrency();
        PaymentTermsMaster paymentTerm = configuration.getPaymentTerm();
        BillingFrequencyMaster billingFrequency = configuration.getBillingFrequency();
        TaxRegionMaster taxRegion = configuration.getTaxRegion();

        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(configuration.getBillingConfigurationId())

                .clientId(configuration.getClient().getClientId())
                .clientName(configuration.getClient().getClientName())

                .projectId(configuration.getProject().getPmsProjectId())
                .projectName(configuration.getProject().getProjectName())

                .projectBudget(
                        configuration.getProject().getProjectBudget())

                .projectBudgetCurrency(
                        configuration.getProject().getProjectBudgetCurrency())

                .billingTypeId(billingType.getBillingTypeId())
                .billingTypeName(billingType.getBillingTypeName())

                .currencyId(currency.getCurrencyId())
                .currencyCode(currency.getCurrencyCode())

                .paymentTermId(paymentTerm.getPaymentTermId())
                .paymentTermName(paymentTerm.getPaymentTermName())

                .billingFrequencyId(billingFrequency.getBillingFrequencyId())
                .billingFrequencyName(billingFrequency.getBillingFrequencyName())

                .taxRegionId(taxRegion.getTaxRegionId())
                .taxRegionName(taxRegion.getTaxRegionName())
                .taxRegionCode(taxRegion.getTaxRegionCode())

                .expenseBillingEligible(configuration.getExpenseBillingEligible())

                .status(configuration.getStatus())
                .effectiveFrom(configuration.getEffectiveFrom())
                .effectiveTo(configuration.getEffectiveTo())
                .isActive(configuration.getIsActive())
                .rejectionReason(configuration.getRejectionReason())

                .hourlyRate(configuration.getHourlyRate())
                .contractValue(configuration.getContractValue())

                .createdAt(configuration.getCreatedAt())
                .updatedAt(configuration.getUpdatedAt())
                .build();
    }

    @Override
    public List<ClientResponseDto> getClients() {

        return clientRepository
                .findByStatusOrderByClientNameAsc(RecordStatus.ACTIVE)
                .stream()
                .map(client -> ClientResponseDto.builder()
                        .clientId(client.getClientId())
                        .clientName(client.getClientName())
                        .build())
                .toList();
    }

    @Override
    public List<ProjectResponseDto> getProjects(UUID clientId) {

        return projectRepository.findByClientIdOrderByProjectNameAsc(clientId)
                .stream()
                .map(project -> ProjectResponseDto.builder()
                        .projectId(project.getPmsProjectId())
                        .projectName(project.getProjectName())
                        .build())
                .toList();
    }

    @Override
    public BillingConfigurationResponseDto getBillingConfiguration(UUID billingConfigurationId) {

        BillingConfiguration billingConfiguration = billingConfigurationRepository.findById(billingConfigurationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Billing Configuration not found with id: " + billingConfigurationId));

        return mapToResponse(billingConfiguration);
    }

    @Override
    public List<BillingConfigurationResponseDto> getAllBillingConfigurations() {
        return billingConfigurationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public BillingConfigurationResponseDto updateBillingConfiguration(
            UUID billingConfigurationId,
            BillingConfigurationRequestDto request) {

        BillingConfiguration configuration = billingConfigurationRepository.findById(billingConfigurationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Billing Configuration not found with id: " + billingConfigurationId));

//        if (configuration.getStatus() == BillingConfigurationStatus.APPROVED) {
//            throw new ValidationException(
//                    "Cannot update an approved billing configuration.");
//        }
        /*
         * Inactive approved configurations cannot be modified.
         */
        if (!Boolean.TRUE.equals(configuration.getIsActive())
                && configuration.getStatus() == BillingConfigurationStatus.APPROVED) {

            throw new ValidationException(
                    "Inactive Billing Configuration cannot be modified.");
        }

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found."));

        ProjectMasterReference project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found."));

        BillingTypeMaster billingType = billingTypeRepository
                .findByBillingTypeIdAndIsActiveTrue(request.getBillingTypeId())
                .orElseThrow(() -> new ValidationException(
                        "Selected Billing Type is inactive or does not exist."));

        CurrencyMaster currency = currencyRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found."));

        PaymentTermsMaster paymentTerm = paymentTermsRepository.findById(request.getPaymentTermId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment Term not found."));

        if (!Boolean.TRUE.equals(paymentTerm.getIsActive())) {
            throw new ValidationException(
                    "Selected Payment Term is inactive.");
        }

        BillingFrequencyMaster billingFrequency = billingFrequencyRepository.findById(request.getBillingFrequencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing Frequency not found."));

        TaxRegionMaster taxRegion = taxRegionRepository.findById(request.getTaxRegionId())
                .orElseThrow(() -> new ResourceNotFoundException("Tax Region not found."));

        if (!project.getClientId().equals(client.getClientId())) {
            throw new ValidationException(
                    "Selected project does not belong to the selected client.");
        }
        boolean commercialChange =
                !configuration.getBillingType().getBillingTypeId().equals(request.getBillingTypeId())
                        || !configuration.getPaymentTerm().getPaymentTermId().equals(request.getPaymentTermId())
                        || !configuration.getBillingFrequency().getBillingFrequencyId().equals(request.getBillingFrequencyId())
                        || !configuration.getTaxRegion().getTaxRegionId().equals(request.getTaxRegionId())
                        || !configuration.getExpenseBillingEligible().equals(request.getExpenseBillingEligible())
                        || !Objects.equals(configuration.getEffectiveFrom(), request.getEffectiveFrom())
                        || !Objects.equals(configuration.getEffectiveTo(), request.getEffectiveTo())
                        || !Objects.equals(configuration.getHourlyRate(), request.getHourlyRate())
                        || !Objects.equals(configuration.getContractValue(), request.getContractValue());

        configuration.setClient(client);
        configuration.setProject(project);
        configuration.setBillingType(billingType);
        configuration.setCurrency(currency);
        configuration.setPaymentTerm(paymentTerm);
        configuration.setBillingFrequency(billingFrequency);
        configuration.setTaxRegion(taxRegion);
        configuration.setExpenseBillingEligible(request.getExpenseBillingEligible());
        configuration.setEffectiveFrom(request.getEffectiveFrom());
        configuration.setEffectiveTo(request.getEffectiveTo());
        configuration.setHourlyRate(request.getHourlyRate());
        configuration.setContractValue(request.getContractValue());
        configuration.setUpdatedAt(LocalDateTime.now());

        /*
         * If an approved configuration is modified,
         * it must go through the approval workflow again and rejection case also handled
         */
        if ((configuration.getStatus() == BillingConfigurationStatus.APPROVED
                || configuration.getStatus() == BillingConfigurationStatus.REJECTED)
                && commercialChange) {

            configuration.setStatus(BillingConfigurationStatus.DRAFT);
            configuration.setIsActive(false);

            // Optional: clear previous rejection reason
            configuration.setRejectionReason(null);
        }

        configuration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved = billingConfigurationRepository.save(configuration);

        return mapToResponse(saved);
    }

    @Override
    public void deactivateBillingConfiguration(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found."));

        /*
         * Already inactive
         */
        if (!Boolean.TRUE.equals(configuration.getIsActive())) {

            throw new ValidationException(
                    "Billing Configuration is already inactive.");
        }

        /*
         * Soft deactivate
         */
//        configuration.setStatus(BillingConfigurationStatus.INACTIVE);

        configuration.setIsActive(false);

        configuration.setUpdatedAt(LocalDateTime.now());

        billingConfigurationRepository.save(configuration);
    }
}
