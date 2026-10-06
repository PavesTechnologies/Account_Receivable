package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.TriggerMode;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the acquisition record's create-or-update semantics on
 * {@code uk_billing_acq_config_period}: repeated calls update one record,
 * a lost concurrent-insert race is retried once in a new transaction, and
 * reconciliation for an existing snapshot never overwrites a recorded status.
 */
@ExtendWith(MockitoExtension.class)
class BillingAcquisitionServiceImplTest {

    @Mock
    private BillingConfigurationRepository billingConfigurationRepository;

    @Mock
    private BillingAcquisitionRepository billingAcquisitionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private BillingAcquisitionServiceImpl service;

    private static final Long PROJECT_ID = 101L;
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 9, 30);

    private UUID billingConfigurationId;
    private BillingConfiguration configuration;

    @BeforeEach
    void setUp() {
        service = new BillingAcquisitionServiceImpl(
                billingConfigurationRepository, billingAcquisitionRepository, transactionManager);

        billingConfigurationId = UUID.randomUUID();
        configuration = BillingConfiguration.builder()
                .billingConfigurationId(billingConfigurationId)
                .project(ProjectMasterReference.builder().pmsProjectId(PROJECT_ID).build())
                .approvalStatus(ApprovalStatus.APPROVED)
                .billingStatus(BillingConfigurationStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private BillingAcquisitionRequestDto request(UUID snapshotId, String status) {
        return BillingAcquisitionRequestDto.builder()
                .billingConfigurationId(billingConfigurationId)
                .billingPeriodStart(PERIOD_START)
                .billingPeriodEnd(PERIOD_END)
                .snapshotId(snapshotId)
                .status(status)
                .build();
    }

    private BillingAcquisition existingAcquisition(UUID snapshotId, BillingAcquisitionStatus status) {
        return BillingAcquisition.builder()
                .id(UUID.randomUUID())
                .billingConfiguration(configuration)
                .projectId(PROJECT_ID)
                .billingPeriodStart(PERIOD_START)
                .billingPeriodEnd(PERIOD_END)
                .snapshotId(snapshotId)
                .triggerMode(TriggerMode.MANUAL)
                .status(status)
                .acquiredAt(LocalDateTime.now())
                .build();
    }

    private void stubFindAcquisition(Optional<BillingAcquisition> first, Optional<BillingAcquisition> then) {
        when(billingAcquisitionRepository
                .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                        billingConfigurationId, PERIOD_START, PERIOD_END))
                .thenReturn(first, then);
    }

    // Test 6 - repeated acquisition updates the existing record
    @Test
    void createManualAcquisition_calledRepeatedly_updatesSingleRecord() {
        when(billingConfigurationRepository.findById(billingConfigurationId)).thenReturn(Optional.of(configuration));

        UUID firstSnapshotId = UUID.randomUUID();
        UUID secondSnapshotId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        ArgumentCaptor<BillingAcquisition> savedCaptor = ArgumentCaptor.forClass(BillingAcquisition.class);
        when(billingAcquisitionRepository.saveAndFlush(savedCaptor.capture())).thenAnswer(invocation -> {
            BillingAcquisition saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(recordId);
            }
            return saved;
        });
        // No record on the first call; the record from the first call on the second.
        when(billingAcquisitionRepository
                .findByBillingConfiguration_BillingConfigurationIdAndBillingPeriodStartAndBillingPeriodEnd(
                        billingConfigurationId, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(savedCaptor.getAllValues().get(0)));

        AcquireDataResponseDto first = service.createManualAcquisition(request(firstSnapshotId, "READY"));
        AcquireDataResponseDto second = service.createManualAcquisition(request(secondSnapshotId, "READY"));

        assertThat(first.getId()).isEqualTo(recordId);
        assertThat(second.getId()).isEqualTo(recordId);
        assertThat(second.getSnapshotId()).isEqualTo(secondSnapshotId);

        List<BillingAcquisition> saves = savedCaptor.getAllValues();
        assertThat(saves).hasSize(2);
        // The second save is the same record updated in place, not a new insert.
        assertThat(saves.get(1)).isSameAs(saves.get(0));
        assertThat(saves.get(1).getStatus()).isEqualTo(BillingAcquisitionStatus.READY);
    }

    // Test 4 (acquisition record) - lost concurrent insert is retried in a new transaction
    @Test
    void createManualAcquisition_concurrentInsertConflict_retriesOnceAndUpdatesWinningRecord() {
        when(billingConfigurationRepository.findById(billingConfigurationId)).thenReturn(Optional.of(configuration));
        UUID snapshotId = UUID.randomUUID();
        BillingAcquisition committedByOtherRequest = existingAcquisition(snapshotId, BillingAcquisitionStatus.READY);
        stubFindAcquisition(Optional.empty(), Optional.of(committedByOtherRequest));
        when(billingAcquisitionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'uk_billing_acq_config_period'"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AcquireDataResponseDto response = service.createManualAcquisition(request(snapshotId, "READY"));

        assertThat(response.getId()).isEqualTo(committedByOtherRequest.getId());
        assertThat(response.getSnapshotId()).isEqualTo(snapshotId);
        verify(billingAcquisitionRepository).saveAndFlush(committedByOtherRequest);
        // First attempt rolled back, retry ran in its own transaction and committed.
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
    }

    @Test
    void createManualAcquisition_integrityViolationOnRetry_propagates() {
        when(billingConfigurationRepository.findById(billingConfigurationId)).thenReturn(Optional.of(configuration));
        stubFindAcquisition(Optional.empty(), Optional.empty());
        when(billingAcquisitionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Column 'project_id' cannot be null"));

        assertThatThrownBy(() -> service.createManualAcquisition(request(UUID.randomUUID(), "READY")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("Column 'project_id' cannot be null");
        verify(billingAcquisitionRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void createManualAcquisition_joinedToCallerTransaction_propagatesConflictWithoutRetry() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(billingConfigurationRepository.findById(billingConfigurationId)).thenReturn(Optional.of(configuration));
        stubFindAcquisition(Optional.empty(), Optional.empty());
        when(billingAcquisitionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'uk_billing_acq_config_period'"));

        // The caller's transaction is already rollback-only - retrying inside it is unsafe.
        assertThatThrownBy(() -> service.createManualAcquisition(
                billingConfigurationId, PERIOD_START, PERIOD_END, UUID.randomUUID(), "READY"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(billingAcquisitionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void createManualAcquisition_missingSnapshotId_stillRejected() {
        when(billingConfigurationRepository.findById(billingConfigurationId)).thenReturn(Optional.of(configuration));

        assertThatThrownBy(() -> service.createManualAcquisition(billingConfigurationId, PERIOD_START, PERIOD_END))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Snapshot acquisition must complete successfully before recording acquisition.");
        verify(billingAcquisitionRepository, never()).saveAndFlush(any());
    }

    // Reconciliation for an existing snapshot (Test 2, acquisition side)
    @Test
    void recordAcquisitionForSnapshot_noRecord_createsReadyRecordForSnapshot() {
        when(billingConfigurationRepository.findById(billingConfigurationId)).thenReturn(Optional.of(configuration));
        stubFindAcquisition(Optional.empty(), Optional.empty());
        when(billingAcquisitionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID snapshotId = UUID.randomUUID();

        AcquireDataResponseDto response = service.recordAcquisitionForSnapshot(
                billingConfigurationId, PERIOD_START, PERIOD_END, snapshotId);

        ArgumentCaptor<BillingAcquisition> savedCaptor = ArgumentCaptor.forClass(BillingAcquisition.class);
        verify(billingAcquisitionRepository).saveAndFlush(savedCaptor.capture());
        BillingAcquisition saved = savedCaptor.getValue();
        assertThat(saved.getSnapshotId()).isEqualTo(snapshotId);
        assertThat(saved.getStatus()).isEqualTo(BillingAcquisitionStatus.READY);
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getBillingPeriodStart()).isEqualTo(PERIOD_START);
        assertThat(saved.getBillingPeriodEnd()).isEqualTo(PERIOD_END);
        assertThat(response.getStatus()).isEqualTo("READY");
    }

    @Test
    void recordAcquisitionForSnapshot_existingRecord_keepsStatusAndRepointsSnapshot() {
        BillingAcquisition existing = existingAcquisition(UUID.randomUUID(), BillingAcquisitionStatus.ALREADY_BILLED);
        stubFindAcquisition(Optional.of(existing), Optional.of(existing));
        when(billingAcquisitionRepository.saveAndFlush(existing)).thenReturn(existing);
        UUID snapshotId = UUID.randomUUID();

        AcquireDataResponseDto response = service.recordAcquisitionForSnapshot(
                billingConfigurationId, PERIOD_START, PERIOD_END, snapshotId);

        assertThat(existing.getSnapshotId()).isEqualTo(snapshotId);
        assertThat(existing.getStatus()).isEqualTo(BillingAcquisitionStatus.ALREADY_BILLED);
        assertThat(response.getStatus()).isEqualTo("ALREADY_BILLED");
        // Reconciling an existing record needs no configuration re-validation.
        verify(billingConfigurationRepository, never()).findById(any());
    }

    @Test
    void recordAcquisitionForSnapshot_recordAlreadyReferencesSnapshot_writesNothing() {
        UUID snapshotId = UUID.randomUUID();
        BillingAcquisition existing = existingAcquisition(snapshotId, BillingAcquisitionStatus.READY);
        stubFindAcquisition(Optional.of(existing), Optional.of(existing));

        AcquireDataResponseDto response = service.recordAcquisitionForSnapshot(
                billingConfigurationId, PERIOD_START, PERIOD_END, snapshotId);

        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(billingAcquisitionRepository, never()).saveAndFlush(any());
    }
}
