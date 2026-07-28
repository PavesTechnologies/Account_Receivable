package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class BillingConfigurationServiceImpl implements BillingConfigurationService {
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final ClientRepository clientRepository;
    private final ProjectMasterReferenceRepository projectRepository;

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

        billingConfiguration.setEffectiveFrom(request.getEffectiveFrom());
        billingConfiguration.setEffectiveTo(request.getEffectiveTo());

        billingConfiguration.setBillingType(request.getBillingType());
        billingConfiguration.setCurrencyCode(request.getCurrencyCode());
        billingConfiguration.setPaymentTermCode(request.getPaymentTermCode());
        billingConfiguration.setBillingFrequency(request.getBillingFrequency());
        billingConfiguration.setTaxRegionCode(request.getTaxRegionCode());
        billingConfiguration.setHourlyRate(request.getHourlyRate());
        billingConfiguration.setContractValue(request.getContractValue());
        billingConfiguration.setExpenseBillingEligible(request.getExpenseBillingEligible());

        billingConfiguration.setStatus(BillingConfigurationStatus.DRAFT);
        billingConfiguration.setIsActive(false);

        billingConfiguration.setCreatedAt(LocalDateTime.now());
        billingConfiguration.setUpdatedAt(LocalDateTime.now());

        BillingConfiguration saved =
                billingConfigurationRepository.save(billingConfiguration);

        // Response
        return toResponseDto(saved);
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

        return toResponseDto(billingConfiguration);
    }

    private BillingConfigurationResponseDto toResponseDto(BillingConfiguration billingConfiguration) {
        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(billingConfiguration.getBillingConfigurationId())
                .clientId(billingConfiguration.getClient().getClientId())
                .projectId(billingConfiguration.getProject().getPmsProjectId())
                .status(billingConfiguration.getStatus())
                .effectiveFrom(billingConfiguration.getEffectiveFrom())
                .effectiveTo(billingConfiguration.getEffectiveTo())
                .active(billingConfiguration.getIsActive())
                .billingType(billingConfiguration.getBillingType())
                .currencyCode(billingConfiguration.getCurrencyCode())
                .paymentTermCode(billingConfiguration.getPaymentTermCode())
                .billingFrequency(billingConfiguration.getBillingFrequency())
                .taxRegionCode(billingConfiguration.getTaxRegionCode())
                .hourlyRate(billingConfiguration.getHourlyRate())
                .contractValue(billingConfiguration.getContractValue())
                .expenseBillingEligible(billingConfiguration.getExpenseBillingEligible())
                .build();
    }
}
