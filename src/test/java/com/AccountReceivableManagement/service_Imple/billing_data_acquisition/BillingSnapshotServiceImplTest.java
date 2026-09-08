package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.builder.billing_data_acquisition.BillingSnapshotBuilder;
import com.AccountReceivableManagement.dependency.billing_data_acquisition.ProjectMasterDataService;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.mapper.billing_data_acquisition.BillingSnapshotMapper;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.strategy.billing_data_acquisition.BillingAcquisitionStrategy;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Note: {@link #billingSnapshotMapper} is deliberately a REAL instance, not a
 * mock. It's the exact object whose {@code snapshot.getItems()} access threw
 * {@code LazyInitializationException} in production once the Hibernate
 * session that loaded the snapshot had closed (see
 * {@link BillingSnapshotRepository#findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd}
 * for the fix — an {@code @EntityGraph} that eagerly fetches {@code items}).
 * Running the real mapper here exercises that exact code path end to end.
 */
@ExtendWith(MockitoExtension.class)
class BillingSnapshotServiceImplTest {

    @Mock
    private BillingSnapshotRepository billingSnapshotRepository;

    @Mock
    private BillingConfigurationIntegration billingConfigurationIntegration;

    @Mock
    private ProjectMasterDataService projectMasterDataService;

    @Mock
    private CurrencyMasterRepository currencyMasterRepository;

    @Mock
    private ProjectMasterReferenceRepository projectMasterReferenceRepository;

    @Mock
    private TaxRegionMasterRepository taxRegionMasterRepository;

    @Mock
    private BillingAcquisitionStrategy timeAndMaterialStrategy;

    // Real, dependency-free collaborators — using the actual production
    // logic here (not mocks) is what lets these tests prove the acquisition
    // pipeline genuinely attaches/maps source timesheet items, not just
    // that some mock was told to return them.
    private final BillingAcquisitionValidator billingAcquisitionValidator = new BillingAcquisitionValidator();
    private final BillingSnapshotBuilder billingSnapshotBuilder = new BillingSnapshotBuilder();
    private final BillingSnapshotMapper billingSnapshotMapper = new BillingSnapshotMapper();

    private BillingSnapshotServiceImpl service;

    private static final Long PROJECT_ID = 23L;
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 8, 5);
    private static final LocalDate PERIOD_END = LocalDate.of(2027, 1, 8);

    private UUID snapshotId;
    private UUID orphanedConfigurationId;
    private BillingSnapshot existingSnapshot;

    @BeforeEach
    void setUp() {
        when(timeAndMaterialStrategy.getSupportedBillingType()).thenReturn(BillingType.TIME_AND_MATERIAL);

        service = new BillingSnapshotServiceImpl(
                billingSnapshotRepository,
                billingConfigurationIntegration,
                projectMasterDataService,
                billingAcquisitionValidator,
                billingSnapshotBuilder,
                billingSnapshotMapper,
                currencyMasterRepository,
                projectMasterReferenceRepository,
                taxRegionMasterRepository,
                List.of(timeAndMaterialStrategy));

        snapshotId = UUID.randomUUID();
        orphanedConfigurationId = UUID.randomUUID();

        BillingSnapshotItem item = BillingSnapshotItem.builder()
                .billingSnapshotItemId(UUID.randomUUID())
                .itemType(BillingItemType.TIME_ENTRY)
                .itemName("Jane Doe")
                .sourceReferenceId("TMS-001")
                .quantity(BigDecimal.valueOf(40))
                .rate(BigDecimal.valueOf(250))
                .amount(BigDecimal.valueOf(10000))
                .workDate(LocalDate.of(2026, 8, 10))
                .approvalStatus("APPROVED")
                .role("Developer")
                .build();

        existingSnapshot = BillingSnapshot.builder()
                .id(snapshotId)
                .snapshotNumber("BS-20260805120000")
                .billingConfigurationId(orphanedConfigurationId)
                .clientId(UUID.randomUUID())
                .projectId(PROJECT_ID)
                .billingPeriodStart(PERIOD_START)
                .billingPeriodEnd(PERIOD_END)
                .status(BillingSnapshotStatus.READY_FOR_TAX)
                .subtotal(BigDecimal.valueOf(10000))
                .expenseAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(10000))
                .items(List.of(item))
                .build();
    }

    // =========================================================
    // A. getByProjectAndPeriod — historical configuration missing
    // =========================================================

    @Test
    void getByProjectAndPeriod_historicalConfigurationMissing_returnsSnapshotWithEnrichmentOmitted() {
        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(existingSnapshot));

        when(billingConfigurationIntegration.getApprovedBillingConfigurationById(orphanedConfigurationId))
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found with id: " + orphanedConfigurationId));

        // No exception must escape this call, and the real mapper must be
        // able to read snapshot.getItems() without a LazyInitializationException.
        ApiResponse<BillingSnapshotResponseDto> response =
                service.getByProjectAndPeriod(PROJECT_ID, PERIOD_START, PERIOD_END);

        assertThat(response.isSuccess()).isTrue();
        BillingSnapshotResponseDto data = response.getData();
        assertThat(data.getSnapshotId()).isEqualTo(snapshotId);
        assertThat(data.getBillingPeriodStart()).isEqualTo(PERIOD_START);
        assertThat(data.getBillingPeriodEnd()).isEqualTo(PERIOD_END);
        assertThat(data.getStatus()).isEqualTo(BillingSnapshotStatus.READY_FOR_TAX);
        assertThat(data.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        // Items were mapped successfully.
        assertThat(data.getTimesheets()).hasSize(1);
        assertThat(data.getTimesheets().get(0).getEmployee()).isEqualTo("Jane Doe");
        assertThat(data.getTimesheets().get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        // The historical reference itself must never be rewritten.
        assertThat(existingSnapshot.getBillingConfigurationId()).isEqualTo(orphanedConfigurationId);

        // Only the configuration-derived enrichment fields are affected.
        assertThat(data.getBillingTypeName()).isNull();
        assertThat(data.getCurrencyCode()).isNull();
        assertThat(data.getPaymentTermName()).isNull();
        assertThat(data.getBillingFrequencyName()).isNull();
        assertThat(data.getTaxRegionCode()).isNull();

        verify(billingSnapshotRepository, never()).save(any());
    }

    // =========================================================
    // B. createBillingSnapshot — existing snapshot branch, historical
    //    configuration missing
    // =========================================================

    @Test
    void createBillingSnapshot_existingSnapshot_historicalConfigurationMissing_returnsSnapshotSuccessfully() {
        BillingSnapshotCreateRequestDto request = BillingSnapshotCreateRequestDto.builder()
                .projectId(PROJECT_ID)
                .billingPeriodStart(PERIOD_START)
                .billingPeriodEnd(PERIOD_END)
                .build();

        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(existingSnapshot));

        when(billingConfigurationIntegration.getApprovedBillingConfigurationById(orphanedConfigurationId))
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found with id: " + orphanedConfigurationId));

        ApiResponse<BillingSnapshotResponseDto> response = service.createBillingSnapshot(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getSnapshotId()).isEqualTo(snapshotId);
        assertThat(response.getData().getTimesheets()).hasSize(1);
        assertThat(existingSnapshot.getBillingConfigurationId()).isEqualTo(orphanedConfigurationId);

        // A pre-existing snapshot must never be re-acquired/re-persisted —
        // clicking "View" must not create a new BillingSnapshot.
        verify(billingSnapshotRepository, never()).save(any());
    }

    // =========================================================
    // C. Happy path — historical configuration still resolvable
    // =========================================================

    @Test
    void getByProjectAndPeriod_configurationFound_returnsEnrichedSnapshot() {
        UUID activeConfigurationId = UUID.randomUUID();
        existingSnapshot.setBillingConfigurationId(activeConfigurationId);

        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(existingSnapshot));

        BillingConfigurationResponseDto configuration = BillingConfigurationResponseDto.builder()
                .billingConfigurationId(activeConfigurationId)
                .billingTypeName("Timesheet Based")
                .currencyCode("USD")
                .build();

        when(billingConfigurationIntegration.getApprovedBillingConfigurationById(activeConfigurationId))
                .thenReturn(configuration);

        ApiResponse<BillingSnapshotResponseDto> response =
                service.getByProjectAndPeriod(PROJECT_ID, PERIOD_START, PERIOD_END);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getSnapshotId()).isEqualTo(snapshotId);
        assertThat(response.getData().getBillingTypeName()).isEqualTo("Timesheet Based");
        assertThat(response.getData().getCurrencyCode()).isEqualTo("USD");
        assertThat(response.getData().getTimesheets()).hasSize(1);
    }

    @Test
    void getByProjectAndPeriod_noSnapshot_returnsFailureWithoutThrowing() {
        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty());

        ApiResponse<BillingSnapshotResponseDto> response =
                service.getByProjectAndPeriod(PROJECT_ID, PERIOD_START, PERIOD_END);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage())
                .isEqualTo("Billing Snapshot not found for the selected project and billing period.");
    }

    // =========================================================
    // D. Unexpected exceptions must still propagate
    // =========================================================

    @Test
    void getByProjectAndPeriod_unexpectedExceptionFromIntegration_propagates() {
        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(existingSnapshot));

        when(billingConfigurationIntegration.getApprovedBillingConfigurationById(orphanedConfigurationId))
                .thenThrow(new IllegalStateException("Database connection lost"));

        assertThatThrownBy(() -> service.getByProjectAndPeriod(PROJECT_ID, PERIOD_START, PERIOD_END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database connection lost");
    }

    // =========================================================
    // E. First-time acquisition persists the source timesheet items
    // =========================================================

    @Test
    void createBillingSnapshot_newSnapshot_persistsSourceTimesheetItems() {
        BillingSnapshotCreateRequestDto request = BillingSnapshotCreateRequestDto.builder()
                .projectId(PROJECT_ID)
                .billingPeriodStart(PERIOD_START)
                .billingPeriodEnd(PERIOD_END)
                .build();

        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty());

        UUID configurationId = UUID.randomUUID();
        BillingConfigurationResponseDto configuration = BillingConfigurationResponseDto.builder()
                .billingConfigurationId(configurationId)
                .billingType(BillingType.TIME_AND_MATERIAL)
                .billingTypeName("Timesheet Based")
                .currencyId(UUID.randomUUID())
                .currencyCode("USD")
                .taxRegionCode("DOM")
                .approved(true)
                .build();
        when(billingConfigurationIntegration.getApprovedBillingConfiguration(PROJECT_ID))
                .thenReturn(configuration);

        when(projectMasterDataService.getClientIdByProjectId(PROJECT_ID)).thenReturn(UUID.randomUUID());

        ProjectMasterReference project = ProjectMasterReference.builder()
                .pmsProjectId(PROJECT_ID)
                .primaryLocation("Domestic")
                .build();
        when(projectMasterReferenceRepository.findBypmsProjectId(PROJECT_ID))
                .thenReturn(Optional.of(project));

        TaxRegionMaster taxRegion = TaxRegionMaster.builder()
                .taxRegionCode("DOM")
                .build();
        when(taxRegionMasterRepository.findByTaxRegionNameIgnoreCase("Domestic"))
                .thenReturn(Optional.of(taxRegion));

        TimesheetDto timesheet = TimesheetDto.builder()
                .resourceId(1L)
                .resourceName("Jane Doe")
                .sourceReferenceId("TMS-100")
                .workDate(LocalDate.of(2026, 8, 10))
                .hours(BigDecimal.valueOf(8))
                .hourlyRate(BigDecimal.valueOf(165))
                .role("Developer")
                .approvalStatus("APPROVED")
                .approved(true)
                .billable(true)
                .build();
        when(timeAndMaterialStrategy.acquire(configuration, request)).thenReturn(
                BillingAcquisitionResultDto.builder()
                        .billingConfigurationId(configurationId)
                        .timesheets(List.of(timesheet))
                        .build());

        ArgumentCaptor<BillingSnapshot> savedCaptor = ArgumentCaptor.forClass(BillingSnapshot.class);
        when(billingSnapshotRepository.save(savedCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<BillingSnapshotResponseDto> response = service.createBillingSnapshot(request);

        assertThat(response.isSuccess()).isTrue();

        // The entity actually handed to the repository for persistence must
        // already carry its source timesheet as a BillingSnapshotItem —
        // this is the exact object graph Hibernate cascades into
        // billing_snapshot_item on save.
        BillingSnapshot persisted = savedCaptor.getValue();
        assertThat(persisted.getItems()).hasSize(1);
        assertThat(persisted.getItems().get(0).getItemName()).isEqualTo("Jane Doe");
        assertThat(persisted.getItems().get(0).getSourceReferenceId()).isEqualTo("TMS-100");
        assertThat(persisted.getItems().get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1320));
        assertThat(persisted.getItems().get(0).getBillingSnapshot()).isSameAs(persisted);

        // ...and the response the frontend receives reflects the same data.
        assertThat(response.getData().getTimesheets()).hasSize(1);
        assertThat(response.getData().getTimesheets().get(0).getEmployee()).isEqualTo("Jane Doe");
        assertThat(response.getData().getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1320));
    }

    // =========================================================
    // F. Empty items are allowed on retrieval — not an error state
    // =========================================================

    @Test
    void getByProjectAndPeriod_snapshotWithNoItems_returnsEmptyTimesheetsSuccessfully() {
        existingSnapshot.setItems(List.of());

        when(billingSnapshotRepository.findByProjectIdAndBillingPeriodStartAndBillingPeriodEnd(
                PROJECT_ID, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(existingSnapshot));

        when(billingConfigurationIntegration.getApprovedBillingConfigurationById(orphanedConfigurationId))
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException("not found"));

        ApiResponse<BillingSnapshotResponseDto> response =
                service.getByProjectAndPeriod(PROJECT_ID, PERIOD_START, PERIOD_END);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getTimesheets()).isNotNull();
        assertThat(response.getData().getTimesheets()).isEmpty();
    }
}
