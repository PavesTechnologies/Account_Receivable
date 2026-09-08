package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingConfigurationServiceImplTest {

    @Mock private BillingConfigurationRepository billingConfigurationRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ProjectMasterReferenceRepository projectRepository;
    @Mock private BillingTypeMasterRepository billingTypeRepository;
    @Mock private CurrencyMasterRepository currencyRepository;
    @Mock private PaymentTermsMasterRepository paymentTermsRepository;
    @Mock private BillingFrequencyMasterRepository billingFrequencyRepository;
    @Mock private TaxRegionMasterRepository taxRegionRepository;
    @Mock private BillingTMRateCardRepository billingTMRateCardRepository;
    @Mock private BillingFixedPriceRepository billingFixedPriceRepository;
    @Mock private BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;
    @Mock private ProjectMasterReferenceRepository projectMasterReferenceRepository;
    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private BillingSnapshotRepository billingSnapshotRepository;

    private BillingConfigurationServiceImpl service;

    private UUID configurationId;
    private BillingConfiguration draftConfiguration;

    @BeforeEach
    void setUp() {
        service = new BillingConfigurationServiceImpl(
                billingConfigurationRepository,
                clientRepository,
                projectRepository,
                billingTypeRepository,
                currencyRepository,
                paymentTermsRepository,
                billingFrequencyRepository,
                taxRegionRepository,
                billingTMRateCardRepository,
                billingFixedPriceRepository,
                billingRecurringConfigurationRepository,
                projectMasterReferenceRepository,
                billingScheduleRepository,
                billingSnapshotRepository);

        configurationId = UUID.randomUUID();
        draftConfiguration = BillingConfiguration.builder()
                .billingConfigurationId(configurationId)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();
    }

    // =========================================================
    // E. Referenced by a historical BillingSnapshot -> rejected
    // =========================================================

    @Test
    void deleteBillingConfiguration_referencedBySnapshot_rejectsDeletion() {
        when(billingConfigurationRepository.findById(configurationId))
                .thenReturn(Optional.of(draftConfiguration));
        when(billingSnapshotRepository.existsByBillingConfigurationId(configurationId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.deleteBillingConfiguration(configurationId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("referenced by historical Billing Snapshot data");

        verify(billingConfigurationRepository, never()).delete(any());
        verify(billingFixedPriceRepository, never()).deleteByBillingConfiguration(any());
        verify(billingRecurringConfigurationRepository, never()).deleteByBillingConfiguration(any());
        verify(billingScheduleRepository, never()).deleteByBillingConfiguration(any());
    }

    // =========================================================
    // F. Unreferenced draft configuration -> deletion still works
    // =========================================================

    @Test
    void deleteBillingConfiguration_unreferencedDraftConfiguration_stillDeletes() {
        when(billingConfigurationRepository.findById(configurationId))
                .thenReturn(Optional.of(draftConfiguration));
        when(billingSnapshotRepository.existsByBillingConfigurationId(configurationId))
                .thenReturn(false);

        service.deleteBillingConfiguration(configurationId);

        verify(billingFixedPriceRepository, times(1)).deleteByBillingConfiguration(draftConfiguration);
        verify(billingRecurringConfigurationRepository, times(1)).deleteByBillingConfiguration(draftConfiguration);
        verify(billingScheduleRepository, times(1)).deleteByBillingConfiguration(draftConfiguration);
        verify(billingConfigurationRepository, times(1)).delete(draftConfiguration);
    }

    @Test
    void deleteBillingConfiguration_notDraft_rejectsRegardlessOfSnapshotReference() {
        BillingConfiguration approvedConfiguration = BillingConfiguration.builder()
                .billingConfigurationId(configurationId)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();

        when(billingConfigurationRepository.findById(configurationId))
                .thenReturn(Optional.of(approvedConfiguration));

        assertThatThrownBy(() -> service.deleteBillingConfiguration(configurationId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Only draft billing configurations can be deleted.");

        verify(billingSnapshotRepository, never()).existsByBillingConfigurationId(any());
        verify(billingConfigurationRepository, never()).delete(any());
    }

    @Test
    void deleteBillingConfiguration_nonexistentId_throwsResourceNotFoundException() {
        when(billingConfigurationRepository.findById(configurationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBillingConfiguration(configurationId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }
}
