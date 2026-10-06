package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.TriggerMode;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ResourceNotFoundException;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
 *
 * Transactions are programmatic (see {@link #executeWithDuplicateRetry}) so a
 * concurrent insert that loses the {@code uk_billing_acq_config_period} race
 * can be retried in a fresh transaction - retrying inside the transaction the
 * failed insert already marked rollback-only would not be safe. When called
 * from inside a caller's transaction (snapshot creation), the work joins that
 * transaction and a duplicate-key failure propagates to the caller instead.
 */
@Slf4j
@Service
public class BillingAcquisitionServiceImpl implements BillingAcquisitionService {

    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingAcquisitionRepository billingAcquisitionRepository;
    private final TransactionTemplate transactionTemplate;

    public BillingAcquisitionServiceImpl(BillingConfigurationRepository billingConfigurationRepository,
            BillingAcquisitionRepository billingAcquisitionRepository,
            PlatformTransactionManager transactionManager) {
        this.billingConfigurationRepository = billingConfigurationRepository;
        this.billingAcquisitionRepository = billingAcquisitionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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

    @Override
    public AcquireDataResponseDto createManualAcquisition(
            UUID billingConfigurationId,
            LocalDate startDate,
            LocalDate endDate,
            UUID snapshotId,
            String statusStr
    ) {
        return executeWithDuplicateRetry(() ->
                upsertAcquisition(billingConfigurationId, startDate, endDate, snapshotId, statusStr));
    }

    @Override
    public AcquireDataResponseDto createManualAcquisition(UUID billingConfigurationId, LocalDate startDate, LocalDate endDate) {
        return createManualAcquisition(billingConfigurationId, startDate, endDate, null, "READY");
    }

    @Override
    public AcquireDataResponseDto recordAcquisitionForSnapshot(
            UUID billingConfigurationId,
            LocalDate startDate,
            LocalDate endDate,
            UUID snapshotId
    ) {
        return executeWithDuplicateRetry(() -> {
            Optional<BillingAcquisition> existingAcquisition = findAcquisition(billingConfigurationId, startDate, endDate);

            if (existingAcquisition.isEmpty()) {
                return upsertAcquisition(billingConfigurationId, startDate, endDate, snapshotId,
                        BillingAcquisitionStatus.READY.name());
            }

            // Keep the recorded status - only the snapshot reference is reconciled.
            BillingAcquisition acquisition = existingAcquisition.get();
            if (snapshotId != null && !snapshotId.equals(acquisition.getSnapshotId())) {
                acquisition.setSnapshotId(snapshotId);
                acquisition = billingAcquisitionRepository.saveAndFlush(acquisition);
            }
            return toResponse(acquisition);
        });
    }

    /**
     * Runs {@code work} in a transaction (joining the caller's, if any). A
     * standalone attempt that loses a concurrent insert race on
     * {@code uk_billing_acq_config_period} is retried once in a new
     * transaction, where the now-committed row is found and updated. A
     * second failure, or any failure while joined to a caller's transaction,
     * propagates unchanged.
     */
    private AcquireDataResponseDto executeWithDuplicateRetry(Supplier<AcquireDataResponseDto> work) {
        boolean joinsCallerTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        try {
            return transactionTemplate.execute(status -> work.get());
        } catch (DataIntegrityViolationException ex) {
            if (joinsCallerTransaction) {
                throw ex;
            }
            log.warn("[BillingAcquisitionConcurrentInsert] Acquisition insert conflicted with a concurrent request; "
                    + "retrying once. Reason: {}", ex.getMostSpecificCause().getMessage());
            return transactionTemplate.execute(status -> work.get());
        }
    }

    private AcquireDataResponseDto upsertAcquisition(
            UUID billingConfigurationId,
            LocalDate startDate,
            LocalDate endDate,
            UUID snapshotId,
            String statusStr
    ) {

        // 1. Validate Billing Configuration exists
        BillingConfiguration config =
                billingConfigurationRepository.findById(billingConfigurationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Billing Configuration not found with ID: "
                                                + billingConfigurationId));

        // 2. Billing Configuration must be approved
        if (config.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ValidationException(
                    "Billing Configuration must be approved before data acquisition.");
        }

        // 3. Billing Configuration must currently be active
        if (config.getBillingStatus() != BillingConfigurationStatus.ACTIVE) {
            throw new ValidationException(
                    "Billing Configuration is not active for project ID: "
                            + config.getProject().getPmsProjectId());
        }

        // 4. Validate billing period
        if (startDate == null) {
            throw new ValidationException(
                    "Billing Period Start Date is required.");
        }

        if (endDate == null) {
            throw new ValidationException(
                    "Billing Period End Date is required.");
        }

        if (startDate.isAfter(endDate)) {
            throw new ValidationException(
                    "Billing Period Start Date cannot be after Billing Period End Date.");
        }

        // 5. Validate snapshot
        if (snapshotId == null) {
            throw new ValidationException(
                    "Snapshot acquisition must complete successfully before recording acquisition.");
        }

        // 6. Resolve acquisition status
        BillingAcquisitionStatus status =
                BillingAcquisitionStatus.READY;

        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = BillingAcquisitionStatus.valueOf(
                        statusStr.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException(
                        "Invalid Billing Acquisition status: "
                                + statusStr);
            }
        }

        // 7. Find existing acquisition for this billing period
        Optional<BillingAcquisition> existingAcquisition =
                findAcquisition(billingConfigurationId, startDate, endDate);

        BillingAcquisition acquisition;

        if (existingAcquisition.isPresent()) {

            acquisition = existingAcquisition.get();

            acquisition.setSnapshotId(snapshotId);
            acquisition.setStatus(status);
            acquisition.setTriggerMode(TriggerMode.MANUAL);
            acquisition.setAcquiredAt(LocalDateTime.now());

        } else {

            acquisition = BillingAcquisition.builder()
                    .billingConfiguration(config)
                    .projectId(
                            config.getProject().getPmsProjectId())
                    .billingPeriodStart(startDate)
                    .billingPeriodEnd(endDate)
                    .snapshotId(snapshotId)
                    .triggerMode(TriggerMode.MANUAL)
                    .status(status)
                    .acquiredAt(LocalDateTime.now())
                    .build();
        }

        // 8. Save acquisition - flushed so a unique-key conflict surfaces here,
        // inside the transaction callback, rather than at commit.
        BillingAcquisition saved =
                billingAcquisitionRepository.saveAndFlush(acquisition);

        // 9. Return response
        return toResponse(saved);
    }

    private Optional<BillingAcquisition> findAcquisition(UUID billingConfigurationId, LocalDate startDate,
            LocalDate endDate) {
        return billingAcquisitionRepository
                .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                        billingConfigurationId,
                        startDate,
                        endDate
                );
    }

    private AcquireDataResponseDto toResponse(BillingAcquisition acquisition) {
        return AcquireDataResponseDto.builder()
                .id(acquisition.getId())
                .projectId(acquisition.getProjectId())
                .snapshotId(acquisition.getSnapshotId())
                .status(
                        acquisition.getStatus() != null
                                ? acquisition.getStatus().name()
                                : null)
                .build();
    }
}
