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
                .projectId(saved.getProject().getPmsProjectId())
                .status(saved.getStatus())
                .effectiveFrom(saved.getEffectiveFrom())
                .effectiveTo(saved.getEffectiveTo())
                .active(saved.getIsActive())
                .build();
    }
}
