package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.TriggerMode;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow recording service for Billing Data Acquisition.
 *
 * Records the acquisition outcome determined by the snapshot processing engine.
 * Stores:
 *   - billingConfiguration & projectId
 *   - billingPeriodStart & billingPeriodEnd
 *   - snapshotId (reference to the created BillingSnapshot)
 *   - acquisitionStatus (e.g. READY | PARTIALLY_READY)
 *   - acquiredAt timestamp
 *   - triggerMode (MANUAL)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BillingAcquisitionServiceImpl implements BillingAcquisitionService {

    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingAcquisitionRepository billingAcquisitionRepository;

    @Override
    public AcquireDataResponseDto createManualAcquisition(BillingAcquisitionRequestDto requestDto) {
        return createManualAcquisition(
                requestDto.getBillingConfigurationId(),
                requestDto.getBillingPeriodStart(),
                requestDto.getBillingPeriodEnd(),
                requestDto.getSnapshotId(),
                requestDto.getStatus()
        );
    }

    public AcquireDataResponseDto createManualAcquisition(
            UUID billingConfigurationId,
            LocalDate startDate,
            LocalDate endDate,
            UUID snapshotId,
            String statusStr
    ) {
        // 1. Validate that the BillingConfiguration exists
        BillingConfiguration config = billingConfigurationRepository.findById(billingConfigurationId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing Configuration not found with ID: " + billingConfigurationId));

        // 2. Validate that it is ACTIVE (is_active = true)
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new ValidationException("Billing Configuration is not active for project ID: " + config.getProject().getPmsProjectId());
        }

        // 3. Validate snapshotId is provided (Snapshot acquisition must complete successfully first)
        if (snapshotId == null) {
            throw new ValidationException("Snapshot acquisition must complete successfully before recording acquisition.");
        }

        // 3. Resolve status enum determined by snapshot engine (default READY)
        BillingAcquisitionStatus status = BillingAcquisitionStatus.READY;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = BillingAcquisitionStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException ex) {
                status = BillingAcquisitionStatus.READY;
            }
        }

        // 4. Idempotently create or update the BillingAcquisition record for (billingConfigurationId, startDate, endDate)
        Optional<BillingAcquisition> existingAcquisition = billingAcquisitionRepository
                .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                        billingConfigurationId, startDate, endDate
                );

        BillingAcquisition acquisition;
        if (existingAcquisition.isPresent()) {
            acquisition = existingAcquisition.get();
            if (snapshotId != null) {
                acquisition.setSnapshotId(snapshotId);
            }
            acquisition.setStatus(status);
            acquisition.setTriggerMode(TriggerMode.MANUAL);
            acquisition.setAcquiredAt(LocalDateTime.now());
        } else {
            acquisition = BillingAcquisition.builder()
                    .billingConfiguration(config)
                    .projectId(config.getProject().getPmsProjectId())
                    .billingPeriodStart(startDate)
                    .billingPeriodEnd(endDate)
                    .snapshotId(snapshotId)
                    .triggerMode(TriggerMode.MANUAL)
                    .status(status)
                    .acquiredAt(LocalDateTime.now())
                    .build();
        }

        BillingAcquisition saved = billingAcquisitionRepository.save(acquisition);

        return AcquireDataResponseDto.builder()
                .id(saved.getId())
                .projectId(saved.getProjectId())
                .snapshotId(saved.getSnapshotId())
                .status(saved.getStatus().name())
                .build();
    }

    @Override
    public AcquireDataResponseDto createManualAcquisition(UUID billingConfigurationId, LocalDate startDate, LocalDate endDate) {
        return createManualAcquisition(billingConfigurationId, startDate, endDate, null, "READY");
    }
}
