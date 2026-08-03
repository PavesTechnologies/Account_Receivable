package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ClientResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ProjectResponseDto;
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

        billingConfiguration.setStatus(BillingConfigurationStatus.DRAFT);
        billingConfiguration.setIsActive(false);

        billingConfiguration.setCreatedAt(LocalDateTime.now());
        billingConfiguration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(billingConfiguration);

        // Response
        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(saved.getBillingConfigurationId())
                .clientId(saved.getClient().getClientId())
                .clientName(saved.getClient().getClientName())
                .projectId(saved.getProject().getPmsProjectId())
                .projectName(saved.getProject().getProjectName())
                .billingTypeId(saved.getBillingType().getBillingTypeId())
                .billingTypeName(saved.getBillingType().getBillingTypeName())
                .currencyId(saved.getCurrency().getCurrencyId())
                .currencyCode(saved.getCurrency().getCurrencyCode())
                .paymentTermId(saved.getPaymentTerm().getPaymentTermId())
                .paymentTermName(saved.getPaymentTerm().getPaymentTermName())
                .billingFrequencyId(saved.getBillingFrequency().getBillingFrequencyId())
                .billingFrequencyName(saved.getBillingFrequency().getBillingFrequencyName())
                .taxRegionId(saved.getTaxRegion().getTaxRegionId())
                .taxRegionName(saved.getTaxRegion().getTaxRegionName())
                .expenseBillingEligible(saved.getExpenseBillingEligible())
                .status(saved.getStatus())
                .effectiveFrom(saved.getEffectiveFrom())
                .effectiveTo(saved.getEffectiveTo())
                .isActive(saved.getIsActive())
                .build();
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

        configuration.setStatus(BillingConfigurationStatus.APPROVED);

        configuration.setIsActive(true);

        configuration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(configuration);

        return mapToResponse(saved);
    }

    private BillingConfigurationResponseDto mapToResponse(BillingConfiguration saved) {

        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(saved.getBillingConfigurationId())

                .clientId(saved.getClient().getClientId())
                .clientName(saved.getClient().getClientName())

                .projectId(saved.getProject().getPmsProjectId())
                .projectName(saved.getProject().getProjectName())

                .billingTypeId(saved.getBillingType().getBillingTypeId())
                .billingTypeName(saved.getBillingType().getBillingTypeName())

                .currencyId(saved.getCurrency().getCurrencyId())
                .currencyCode(saved.getCurrency().getCurrencyCode())

                .paymentTermId(saved.getPaymentTerm().getPaymentTermId())
                .paymentTermName(saved.getPaymentTerm().getPaymentTermName())

                .billingFrequencyId(saved.getBillingFrequency().getBillingFrequencyId())
                .billingFrequencyName(saved.getBillingFrequency().getBillingFrequencyName())

                .taxRegionId(saved.getTaxRegion().getTaxRegionId())
                .taxRegionName(saved.getTaxRegion().getTaxRegionName())

                .expenseBillingEligible(saved.getExpenseBillingEligible())

                .status(saved.getStatus())
                .effectiveFrom(saved.getEffectiveFrom())
                .effectiveTo(saved.getEffectiveTo())
                .isActive(saved.getIsActive())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
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
}
