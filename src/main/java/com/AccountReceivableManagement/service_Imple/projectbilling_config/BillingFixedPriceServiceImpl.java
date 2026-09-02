package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingFixedPriceResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingFixedPriceService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class BillingFixedPriceServiceImpl implements BillingFixedPriceService {

    private final BillingFixedPriceRepository billingFixedPriceRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;
//    private final com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingConfigurationChangeTrackingService changeTrackingService;

    @Override
    public BillingFixedPriceResponseDto create(
            UUID billingConfigurationId,
            BillingFixedPriceRequestDto request) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Billing Configuration not found."
                                ));

        validateBillingConfiguration(configuration);

        if (billingFixedPriceRepository
                .existsByBillingConfigurationAndIsActiveTrue(configuration)) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Fixed Price configuration already exists."
            );
        }

        validateRequest(request);

        // Validate effective dates against project duration
        validateEffectiveDatesAgainstProjectDuration(
                configuration,
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        // If contract value source is PMS_BUDGET, sync from current project budget
        if (request.getContractValueSource() == ContractValueSource.PMS_BUDGET) {
            var project = configuration.getProject();
            if (project.getProjectBudget() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Project Budget is not available from PMS for PMS_BUDGET source.");
            }
            // Override pmsProjectBudget with current project budget
            request.setPmsProjectBudget(project.getProjectBudget());
        }

        BillingFixedPriceConfiguration fixedPrice =
                BillingFixedPriceConfiguration.builder()
                        .billingConfiguration(configuration)
                        .contractValue(request.getContractValue())
                        .pmsProjectBudget(request.getPmsProjectBudget())
                        .contractValueSource(request.getContractValueSource())
                        .retentionPercentage(request.getRetentionPercentage())
                        .advanceReceived(request.getAdvanceReceived())
                        .effectiveFrom(request.getEffectiveFrom())
                        .effectiveTo(request.getEffectiveTo())
                        .remarks(request.getRemarks())
                        .isActive(true)
                        .build();

        validateFinancialValues(fixedPrice);

        BillingFixedPriceConfiguration saved =
                billingFixedPriceRepository.save(fixedPrice);

        // Update parent BillingConfiguration with the actual contract value
        BigDecimal finalContractValue = fixedPrice.getContractValue();
        configuration.setContractValue(finalContractValue);
        configuration.setUpdatedAt(LocalDateTime.now());
        billingConfigurationRepository.save(configuration);

        return mapToResponse(saved);
    }

    @Override
    public BillingFixedPriceResponseDto update(
            UUID fixedPriceConfigurationId,
            BillingFixedPriceRequestDto request) {

        BillingFixedPriceConfiguration fixedPrice =
                billingFixedPriceRepository.findById(
                        fixedPriceConfigurationId
                ).orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Fixed Price Configuration not found."
                        ));

        BillingConfiguration configuration =
                fixedPrice.getBillingConfiguration();

        validateBillingConfiguration(configuration);

        validateRequest(request);

//        // Capture previous state for audit before any changes
//        BillingFixedPriceConfiguration previousFixedPrice = cloneFixedPriceConfiguration(fixedPrice);
//        BillingConfiguration previousConfiguration = cloneConfiguration(configuration);

        // Validate effective dates against project duration
        validateEffectiveDatesAgainstProjectDuration(
                configuration,
                request.getEffectiveFrom(),
                request.getEffectiveTo());

        // If contract value source is PMS_BUDGET, sync from current project budget
        if (request.getContractValueSource() == ContractValueSource.PMS_BUDGET) {
            var project = configuration.getProject();
            if (project.getProjectBudget() == null) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Project Budget is not available from PMS for PMS_BUDGET source.");
            }
            // Override pmsProjectBudget with current project budget
            request.setPmsProjectBudget(project.getProjectBudget());
        }

        fixedPrice.setContractValue(request.getContractValue());
        fixedPrice.setPmsProjectBudget(request.getPmsProjectBudget());
        fixedPrice.setContractValueSource(
                request.getContractValueSource()
        );
        fixedPrice.setRetentionPercentage(request.getRetentionPercentage());
        fixedPrice.setAdvanceReceived(request.getAdvanceReceived());
        fixedPrice.setEffectiveFrom(request.getEffectiveFrom());
        fixedPrice.setEffectiveTo(request.getEffectiveTo());
        fixedPrice.setRemarks(request.getRemarks());

        validateFinancialValues(fixedPrice);

        BillingFixedPriceConfiguration updated =
                billingFixedPriceRepository.save(fixedPrice);

        // Update parent BillingConfiguration with the actual contract value
        BigDecimal finalContractValue = updated.getContractValue();
        configuration.setContractValue(finalContractValue);
        
        // Handle approval state transition for the parent configuration
        handleApprovalStateTransition(configuration);
        
        configuration.setUpdatedAt(LocalDateTime.now());
        billingConfigurationRepository.save(configuration);

//        // Create audit record if transitioning from APPROVED
//        if (previousConfiguration.getApprovalStatus() == ApprovalStatus.APPROVED &&
//            configuration.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
//
//            com.AccountReceivableManagement.entity.projectbilling_config.BillingConfigurationAudit audit =
//                    changeTrackingService.createAuditRecord(configuration, previousConfiguration);
//
//            if (audit != null) {
//                // Track Fixed Price specific changes
//                changeTrackingService.trackFixedPriceChanges(audit, previousFixedPrice, updated);
//            }
//        }

        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingFixedPriceResponseDto get(
            UUID fixedPriceConfigurationId) {

        BillingFixedPriceConfiguration fixedPrice =
                billingFixedPriceRepository.findById(
                        fixedPriceConfigurationId
                ).orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Fixed Price Configuration not found."
                        ));

        return mapToResponse(fixedPrice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillingFixedPriceResponseDto> getAll(
            UUID billingConfigurationId) {

        BillingConfiguration configuration =
                billingConfigurationRepository.findById(
                        billingConfigurationId
                ).orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Billing Configuration not found."
                        ));

        return billingFixedPriceRepository
                .findAllByBillingConfigurationAndIsActiveTrue(configuration)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void delete(UUID fixedPriceConfigurationId) {

        BillingFixedPriceConfiguration fixedPrice =
                billingFixedPriceRepository.findById(
                        fixedPriceConfigurationId
                ).orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException(
                                "Fixed Price Configuration not found."
                        ));

        BillingConfiguration configuration =
                fixedPrice.getBillingConfiguration();

        validateBillingConfiguration(configuration);

        fixedPrice.setIsActive(false);

        billingFixedPriceRepository.save(fixedPrice);
    }

    private void validateBillingConfiguration(
            BillingConfiguration configuration) {

        if (configuration.getBillingType() == null ||
                configuration.getBillingType().getBillingTypeName() == null ||
                !configuration.getBillingType()
                        .getBillingTypeName()
                        .equalsIgnoreCase("Fixed Price")) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Fixed Price configuration can only be created for Fixed Price billing."
            );
        }

        // Removed validation that blocked editing approved configurations.
        // Approved configurations can now be edited, which will trigger
        // the approval state transition to PENDING_APPROVAL + INACTIVE.
    }

    private void validateRequest(
            BillingFixedPriceRequestDto request) {

        if (request.getEffectiveFrom() != null &&
                request.getEffectiveTo() != null &&
                request.getEffectiveFrom()
                        .isAfter(request.getEffectiveTo())) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To."
            );
        }

        if (request.getContractValueSource() ==
                ContractValueSource.PMS_BUDGET) {

            if (request.getPmsProjectBudget() == null) {

                throw new GlobalExceptionHandler.ValidationException(
                        "PMS Project Budget is required when Contract Value Source is PMS Budget."
                );
            }
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

    private void validateFinancialValues(
            BillingFixedPriceConfiguration fixedPrice) {

        BigDecimal contractValue =
                fixedPrice.getContractValue();

        BigDecimal retentionPercentage =
                fixedPrice.getRetentionPercentage();

        BigDecimal advanceReceived =
                fixedPrice.getAdvanceReceived();

        // Validate retention percentage is not null and within valid range
        if (retentionPercentage == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Retention Percentage is required."
            );
        }

        if (retentionPercentage.compareTo(BigDecimal.ZERO) < 0 ||
                retentionPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Retention Percentage must be between 0 and 100."
            );
        }

        // Validate advance received is not null and not negative
        if (advanceReceived == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Advance Received is required."
            );
        }

        if (advanceReceived.compareTo(BigDecimal.ZERO) < 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Advance Received cannot be negative."
            );
        }

        // Calculate retention amount
        BigDecimal retentionAmount =
                contractValue
                        .multiply(retentionPercentage)
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );

        // Calculate billable amount
        BigDecimal billableAmount =
                contractValue.subtract(retentionAmount);

        // Validate advance received does not exceed billable amount
        if (advanceReceived.compareTo(billableAmount) > 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Advance Received cannot exceed the billable amount after retention."
            );
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {

        return value == null
                ? BigDecimal.ZERO
                : value;
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
    private void handleApprovalStateTransition(BillingConfiguration configuration) {
        ApprovalStatus currentStatus = configuration.getApprovalStatus();
        
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

    /**
     * Clones a BillingFixedPriceConfiguration for audit purposes.
     */
    private BillingFixedPriceConfiguration cloneFixedPriceConfiguration(BillingFixedPriceConfiguration original) {
        if (original == null) {
            return null;
        }
        
        BillingFixedPriceConfiguration clone = new BillingFixedPriceConfiguration();
        clone.setFixedPriceConfigurationId(original.getFixedPriceConfigurationId());
        clone.setContractValue(original.getContractValue());
        clone.setPmsProjectBudget(original.getPmsProjectBudget());
        clone.setContractValueSource(original.getContractValueSource());
        clone.setRetentionPercentage(original.getRetentionPercentage());
        clone.setAdvanceReceived(original.getAdvanceReceived());
        clone.setEffectiveFrom(original.getEffectiveFrom());
        clone.setEffectiveTo(original.getEffectiveTo());
        clone.setRemarks(original.getRemarks());
        
        return clone;
    }

    /**
     * Clones a BillingConfiguration for audit purposes.
     */
    private BillingConfiguration cloneConfiguration(BillingConfiguration original) {
        if (original == null) {
            return null;
        }
        
        BillingConfiguration clone = new BillingConfiguration();
        clone.setBillingConfigurationId(original.getBillingConfigurationId());
        clone.setApprovalStatus(original.getApprovalStatus());
        clone.setBillingStatus(original.getBillingStatus());
        clone.setContractValue(original.getContractValue());
        
        return clone;
    }

    private BillingFixedPriceResponseDto mapToResponse(
            BillingFixedPriceConfiguration fixedPrice) {

        BigDecimal contractValue =
                fixedPrice.getContractValue();

        BigDecimal retentionPercentage =
                fixedPrice.getRetentionPercentage();

        BigDecimal advanceReceived =
                fixedPrice.getAdvanceReceived();

        // Calculate retention amount: Contract Value × Retention % / 100
        BigDecimal retentionAmount =
                contractValue
                        .multiply(retentionPercentage)
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );

        // Calculate billable amount: Contract Value − Retention Amount
        BigDecimal billableAmount =
                contractValue.subtract(retentionAmount);

        // Calculate remaining receivable: Billable Amount − Advance Received
        BigDecimal remainingReceivable =
                billableAmount.subtract(advanceReceived);

        return BillingFixedPriceResponseDto.builder()
                .fixedPriceConfigurationId(
                        fixedPrice.getFixedPriceConfigurationId()
                )
                .billingConfigurationId(
                        fixedPrice.getBillingConfiguration()
                                .getBillingConfigurationId()
                )
                .contractValue(contractValue)
                .pmsProjectBudget(
                        fixedPrice.getPmsProjectBudget()
                )
                .contractValueSource(
                        fixedPrice.getContractValueSource()
                )
                .retentionPercentage(
                        retentionPercentage
                )
                .retentionAmount(
                        retentionAmount
                )
                .billableAmount(
                        billableAmount
                )
                .advanceReceived(
                        advanceReceived
                )
                .remainingReceivable(
                        remainingReceivable
                )
                .effectiveFrom(
                        fixedPrice.getEffectiveFrom()
                )
                .effectiveTo(
                        fixedPrice.getEffectiveTo()
                )
                .remarks(
                        fixedPrice.getRemarks()
                )
                .isActive(
                        fixedPrice.getIsActive()
                )
                .createdAt(
                        fixedPrice.getCreatedAt()
                )
                .updatedAt(
                        fixedPrice.getUpdatedAt()
                )
                .build();
    }



}
