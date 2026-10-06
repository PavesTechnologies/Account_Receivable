package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingAcquisition;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingAcquisitionStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.TriggerMode;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingAcquisitionRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies the Acquisition Console table reports the billing period the user
 * actually selected on Acquire Snapshot (the latest {@link BillingAcquisition}
 * record's own dates), never the Billing Configuration's effectiveFrom/
 * effectiveTo validity window.
 */
@ExtendWith(MockitoExtension.class)
class BillingDataAcquisitionServiceImplTest {

    @Mock
    private BillingConfigurationRepository billingConfigurationRepository;

    @Mock
    private BillingAcquisitionRepository billingAcquisitionRepository;

    @Mock
    private BillingSnapshotRepository billingSnapshotRepository;

    private BillingDataAcquisitionServiceImpl service;

    private BillingConfiguration configuration;
    private UUID billingConfigurationId;

    @BeforeEach
    void setUp() {
        service = new BillingDataAcquisitionServiceImpl(
                billingConfigurationRepository, billingAcquisitionRepository, billingSnapshotRepository);

        billingConfigurationId = UUID.randomUUID();

        ProjectMasterReference project = ProjectMasterReference.builder()
                .pmsProjectId(101L)
                .projectName("Test Project")
                .startDate(LocalDate.of(2026, 8, 5))
                .endDate(LocalDate.of(2027, 1, 8))
                .build();

        Client client = Client.builder()
                .clientId(UUID.randomUUID())
                .clientName("Test Client")
                .build();

        BillingTypeMaster billingType = BillingTypeMaster.builder()
                .billingTypeId(UUID.randomUUID())
                .billingTypeName("Time & Material")
                .build();

        BillingFrequencyMaster billingFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Monthly")
                .build();

        CurrencyMaster currency = CurrencyMaster.builder()
                .currencyId(UUID.randomUUID())
                .currencyCode("USD")
                .build();

        configuration = BillingConfiguration.builder()
                .billingConfigurationId(billingConfigurationId)
                .project(project)
                .client(client)
                .billingType(billingType)
                .billingFrequency(billingFrequency)
                .currency(currency)
                .approvalStatus(ApprovalStatus.APPROVED)
                .billingStatus(BillingConfigurationStatus.ACTIVE)
                // The overall configuration/project validity window - must never
                // leak into the console's Billing Period column.
                .effectiveFrom(LocalDate.of(2026, 8, 5))
                .effectiveTo(LocalDate.of(2027, 1, 8))
                .build();

        when(billingConfigurationRepository.findByApprovalStatusAndBillingStatus(
                ApprovalStatus.APPROVED, BillingConfigurationStatus.ACTIVE))
                .thenReturn(List.of(configuration));
    }

    @Test
    void returnsUserSelectedBillingPeriod_fromLatestAcquisition_notConfigurationValidity() {
        LocalDate selectedStart = LocalDate.of(2026, 9, 1);
        LocalDate selectedEnd = LocalDate.of(2026, 9, 30);

        BillingAcquisition acquisition = BillingAcquisition.builder()
                .id(UUID.randomUUID())
                .billingConfiguration(configuration)
                .projectId(101L)
                .billingPeriodStart(selectedStart)
                .billingPeriodEnd(selectedEnd)
                .snapshotId(UUID.randomUUID())
                .triggerMode(TriggerMode.MANUAL)
                .status(BillingAcquisitionStatus.READY)
                .acquiredAt(LocalDateTime.now())
                .build();

        when(billingAcquisitionRepository.findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(
                billingConfigurationId))
                .thenReturn(Optional.of(acquisition));

        List<BillingDataAcquisitionResponseDto> result = service.getActiveConfigurations();

        assertThat(result).hasSize(1);
        BillingDataAcquisitionResponseDto dto = result.get(0);
        assertThat(dto.getBillingPeriodStart()).isEqualTo(selectedStart);
        assertThat(dto.getBillingPeriodEnd()).isEqualTo(selectedEnd);
        // Sanity check the fixture: the selected period differs from the
        // configuration's own effectiveFrom/effectiveTo validity window.
        assertThat(dto.getBillingPeriodStart()).isNotEqualTo(configuration.getEffectiveFrom());
        assertThat(dto.getBillingPeriodEnd()).isNotEqualTo(configuration.getEffectiveTo());
        assertThat(dto.getStatus()).isEqualTo(BillingAcquisitionStatus.READY.name());
    }

    @Test
    void returnsNullBillingPeriod_whenConfigurationHasNeverBeenAcquired() {
        when(billingAcquisitionRepository.findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(
                billingConfigurationId))
                .thenReturn(Optional.empty());

        List<BillingDataAcquisitionResponseDto> result = service.getActiveConfigurations();

        assertThat(result).hasSize(1);
        BillingDataAcquisitionResponseDto dto = result.get(0);
        assertThat(dto.getBillingPeriodStart()).isNull();
        assertThat(dto.getBillingPeriodEnd()).isNull();
        assertThat(dto.getSnapshotId()).isNull();
        assertThat(dto.getStatus()).isEqualTo(BillingAcquisitionStatus.NOT_ACQUIRED.name());
    }

    // =========================================================
    // Orphaned snapshots (persisted without an acquisition record)
    // =========================================================

    private BillingSnapshot snapshot(LocalDate start, LocalDate end, LocalDateTime createdDate) {
        return BillingSnapshot.builder()
                .id(UUID.randomUUID())
                .snapshotNumber("BS-20260930101500-1A2B3C4D")
                .billingConfigurationId(billingConfigurationId)
                .projectId(101L)
                .billingPeriodStart(start)
                .billingPeriodEnd(end)
                .status(BillingSnapshotStatus.TAX_COMPLETED)
                .createdDate(createdDate)
                .build();
    }

    private BillingAcquisition acquisition(LocalDate start, LocalDate end, LocalDateTime createdAt, String finalInvoiceId) {
        return BillingAcquisition.builder()
                .id(UUID.randomUUID())
                .billingConfiguration(configuration)
                .projectId(101L)
                .billingPeriodStart(start)
                .billingPeriodEnd(end)
                .snapshotId(UUID.randomUUID())
                .triggerMode(TriggerMode.MANUAL)
                .status(BillingAcquisitionStatus.READY)
                .finalInvoiceId(finalInvoiceId)
                .createdAt(createdAt)
                .build();
    }

    // Test 5 - listing an orphaned snapshot
    @Test
    void orphanedSnapshotWithoutAcquisitionRecord_isNotReportedAsNotAcquired() {
        BillingSnapshot orphan = snapshot(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDateTime.now());
        when(billingAcquisitionRepository.findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(
                billingConfigurationId)).thenReturn(Optional.empty());
        when(billingSnapshotRepository.findFirstByBillingConfigurationIdOrderByCreatedDateDesc(billingConfigurationId))
                .thenReturn(Optional.of(orphan));

        BillingDataAcquisitionResponseDto dto = service.getActiveConfigurations().get(0);

        assertThat(dto.getStatus()).isEqualTo(BillingAcquisitionStatus.READY.name());
        assertThat(dto.getBillingPeriodStart()).isEqualTo(orphan.getBillingPeriodStart());
        assertThat(dto.getBillingPeriodEnd()).isEqualTo(orphan.getBillingPeriodEnd());
        assertThat(dto.getSnapshotId()).isEqualTo(orphan.getId());
        assertThat(dto.getLastInvoice()).isNull();
    }

    @Test
    void orphanedSnapshotNewerThanLatestAcquisitionForAnotherPeriod_isReported() {
        LocalDateTime augustAcquiredAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        BillingAcquisition august = acquisition(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), augustAcquiredAt, "INV-0008");
        BillingSnapshot septemberOrphan = snapshot(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), augustAcquiredAt.plusDays(30));
        when(billingAcquisitionRepository.findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(
                billingConfigurationId)).thenReturn(Optional.of(august));
        when(billingSnapshotRepository.findFirstByBillingConfigurationIdOrderByCreatedDateDesc(billingConfigurationId))
                .thenReturn(Optional.of(septemberOrphan));

        BillingDataAcquisitionResponseDto dto = service.getActiveConfigurations().get(0);

        assertThat(dto.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(dto.getSnapshotId()).isEqualTo(septemberOrphan.getId());
        assertThat(dto.getStatus()).isEqualTo(BillingAcquisitionStatus.READY.name());
    }

    @Test
    void snapshotWithItsOwnAcquisitionRecord_reportsTheAcquisitionRecord() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        LocalDateTime acquiredAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        BillingAcquisition recorded = acquisition(start, end, acquiredAt, "INV-0009");
        when(billingAcquisitionRepository.findFirstByBillingConfiguration_BillingConfigurationIdOrderByCreatedAtDesc(
                billingConfigurationId)).thenReturn(Optional.of(recorded));
        when(billingSnapshotRepository.findFirstByBillingConfigurationIdOrderByCreatedDateDesc(billingConfigurationId))
                .thenReturn(Optional.of(snapshot(start, end, acquiredAt.minusSeconds(1))));

        BillingDataAcquisitionResponseDto dto = service.getActiveConfigurations().get(0);

        assertThat(dto.getStatus()).isEqualTo(BillingAcquisitionStatus.READY.name());
        assertThat(dto.getLastInvoice()).isEqualTo("INV-0009");
        assertThat(dto.getSnapshotId()).isEqualTo(recorded.getSnapshotId());
    }
}
