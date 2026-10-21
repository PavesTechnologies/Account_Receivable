package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.builder.billing_data_acquisition.BillingSnapshotBuilder;
import com.AccountReceivableManagement.dependency.billing_data_acquisition.ProjectMasterDataService;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.mapper.billing_data_acquisition.BillingSnapshotMapper;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.strategy.billing_data_acquisition.BillingAcquisitionStrategy;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Database-level double-billing guard ({@code uk_billing_snapshot_item_billed_source})
 * against a real (in-memory H2, MySQL mode) database: one TMS timesheet can
 * be claimed by only one Billing Snapshot, whichever period - same or
 * overlapping - and however concurrently the snapshots are acquired. Not
 * wrapped in a test transaction; each save commits, as in production.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing_snapshot_item_claim;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingSnapshotItemClaimPersistenceTest {

    private static final Long PROJECT_ID = 7L;
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);
    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    private static final LocalDate NOV_14 = LocalDate.of(2026, 11, 14);

    @Autowired private BillingSnapshotRepository snapshotRepository;
    @Autowired private BillingSnapshotItemRepository itemRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final BillingSnapshotBuilder builder = new BillingSnapshotBuilder();
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            itemRepository.deleteAll();
            snapshotRepository.deleteAll();
        });
    }

    // ---------------------------------------------------------------- fixtures

    private static TimesheetDto timesheet(String timesheetId, String role, String hours, String rate) {
        return TimesheetDto.builder()
                .sourceReferenceId(timesheetId)
                .resourceName("Resource " + timesheetId)
                .workDate(OCT_15)
                .role(role)
                .hours(new BigDecimal(hours))
                .hourlyRate(new BigDecimal(rate))
                .approvalStatus("APPROVED")
                .approved(true)
                .billable(true)
                .build();
    }

    /** A snapshot of the timesheets, built by the production item mapping. */
    private BillingSnapshot snapshot(LocalDate start, LocalDate end, TimesheetDto... timesheets) {
        BillingSnapshot snapshot = BillingSnapshot.builder()
                .snapshotNumber("BS-" + UUID.randomUUID().toString().substring(0, 20))
                .billingConfigurationId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .projectId(PROJECT_ID)
                .billingTypeId(UUID.randomUUID())
                .currencyId(UUID.randomUUID())
                .currencyCode("INR")
                .billingPeriodStart(start)
                .billingPeriodEnd(end)
                .status(BillingSnapshotStatus.READY_FOR_TAX)
                .createdDate(LocalDateTime.now())
                .build();
        List<BillingSnapshotItem> items = builder.buildItems(List.of(timesheets));
        items.forEach(item -> item.setBillingSnapshot(snapshot));
        snapshot.setItems(new ArrayList<>(items));
        return snapshot;
    }

    private BillingSnapshot save(BillingSnapshot snapshot) {
        return tx.execute(status -> snapshotRepository.saveAndFlush(snapshot));
    }

    // ================================================================ claims

    @Test
    void timesheetItems_claimTheirTmsTimesheet() {
        BillingSnapshot saved = save(snapshot(OCT_1, OCT_31,
                timesheet("TS-1", "Developer", "8", "1500"),
                timesheet("TS-2", "Senior Developer", "5", "2000")));

        List<BillingSnapshotItem> items = itemRepository.findAll();
        assertThat(items).extracting(BillingSnapshotItem::getBilledSourceReferenceId)
                .containsExactlyInAnyOrder("TS-1", "TS-2");
        assertThat(items).extracting(BillingSnapshotItem::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("12000"), new BigDecimal("10000"));
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void sameTimesheet_inASnapshotOfAnOverlappingPeriod_cannotBeCommitted() {
        save(snapshot(OCT_1, OCT_31, timesheet("TS-1", "Developer", "8", "1500")));

        assertThatThrownBy(() -> save(snapshot(OCT_15, NOV_14,
                timesheet("TS-1", "Developer", "8", "1500"),
                timesheet("TS-3", "Developer", "4", "1500"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .containsIgnoringCase("uk_billing_snapshot_item_billed_source"));

        // Nothing of the second snapshot was kept - not even its other, unclaimed timesheet.
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(itemRepository.findAll()).extracting(BillingSnapshotItem::getSourceReferenceId)
                .containsExactly("TS-1");
    }

    @Test
    void alreadyClaimedTimesheet_isReportedByTheValidatorsQuery_whateverThePeriod() {
        save(snapshot(OCT_1, OCT_31, timesheet("TS-1", "Developer", "8", "1500")));

        List<String> claimed = itemRepository.findClaimedSourceReferenceIds(BillingItemType.TIME_ENTRY,
                Set.of("TS-1", "TS-9"), BillingSnapshotStatus.CANCELLED, null);

        assertThat(claimed).containsExactly("TS-1");
    }

    @Test
    void concurrentSnapshotsOfOverlappingPeriods_billTheSharedTimesheetExactlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> october = pool.submit(() -> {
                start.await();
                return save(snapshot(OCT_1, OCT_31, timesheet("TS-9", "Developer", "8", "1500")));
            });
            Future<?> midMonth = pool.submit(() -> {
                start.await();
                return save(snapshot(OCT_15, NOV_14, timesheet("TS-9", "Developer", "8", "1500")));
            });
            start.countDown();

            int committed = 0;
            for (Future<?> attempt : List.of(october, midMonth)) {
                try {
                    attempt.get(30, TimeUnit.SECONDS);
                    committed++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    // The loser fails on the claim (or on the lock the winner holds on it).
                    assertThat(ex.getCause()).isInstanceOf(DataAccessException.class);
                }
            }

            assertThat(committed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(itemRepository.findAll()).extracting(BillingSnapshotItem::getBilledSourceReferenceId)
                .containsExactly("TS-9");
    }

    @Test
    void rebuildingASnapshot_replacesItsOwnClaimsWithoutConflict() {
        BillingSnapshot saved = save(snapshot(OCT_1, OCT_31,
                timesheet("TS-1", "Developer", "8", "1500"),
                timesheet("TS-2", "Senior Developer", "5", "2000")));

        // The real financial-correction rebuild: TMS now reports TS-1 with 9 hours and a new TS-4.
        BillingSnapshotServiceImpl service = serviceAcquiring(saved.getBillingConfigurationId(),
                timesheet("TS-1", "Developer", "9", "1500"),
                timesheet("TS-2", "Senior Developer", "5", "2000"),
                timesheet("TS-4", "Developer", "2", "1500"));

        tx.executeWithoutResult(status -> service.rebuildBillingSnapshot(saved.getId()));

        assertThat(itemRepository.findAll()).extracting(BillingSnapshotItem::getBilledSourceReferenceId)
                .containsExactlyInAnyOrder("TS-1", "TS-2", "TS-4");
        assertThat(snapshotRepository.findById(saved.getId()).orElseThrow().getSubtotal())
                .isEqualByComparingTo("26500");
    }

    @Test
    void rebuildingASnapshot_cannotTakeOverATimesheetBilledByAnotherSnapshot() {
        save(snapshot(OCT_15, NOV_14, timesheet("TS-7", "Developer", "8", "1500")));
        BillingSnapshot saved = save(snapshot(OCT_1, OCT_31, timesheet("TS-1", "Developer", "8", "1500")));
        BillingSnapshotServiceImpl service = serviceAcquiring(saved.getBillingConfigurationId(),
                timesheet("TS-1", "Developer", "8", "1500"),
                timesheet("TS-7", "Developer", "8", "1500"));

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.rebuildBillingSnapshot(saved.getId())))
                .hasMessageContaining("TS-7")
                .hasMessageContaining("already included in another Billing Snapshot");

        assertThat(itemRepository.findAll()).extracting(BillingSnapshotItem::getBilledSourceReferenceId)
                .containsExactlyInAnyOrder("TS-7", "TS-1");
    }

    /**
     * The production service on the real repositories and validator; only
     * the configuration lookup and the TMS acquisition are stubbed.
     */
    private BillingSnapshotServiceImpl serviceAcquiring(UUID configurationId, TimesheetDto... timesheets) {
        BillingConfigurationIntegration configurations = mock(BillingConfigurationIntegration.class);
        when(configurations.getApprovedBillingConfigurationById(configurationId)).thenReturn(
                BillingConfigurationResponseDto.builder()
                        .billingConfigurationId(configurationId)
                        .billingType(BillingType.TIME_AND_MATERIAL)
                        .approved(true)
                        .build());
        BillingAcquisitionStrategy strategy = mock(BillingAcquisitionStrategy.class);
        when(strategy.getSupportedBillingType()).thenReturn(BillingType.TIME_AND_MATERIAL);
        when(strategy.acquire(any(), any())).thenReturn(BillingAcquisitionResultDto.builder()
                .billingConfigurationId(configurationId)
                .timesheets(new ArrayList<>(List.of(timesheets)))
                .build());

        return new BillingSnapshotServiceImpl(snapshotRepository, configurations,
                mock(ProjectMasterDataService.class), new BillingAcquisitionValidator(itemRepository), builder,
                mock(BillingSnapshotMapper.class), mock(CurrencyMasterRepository.class),
                mock(ProjectMasterReferenceRepository.class), mock(TaxRegionMasterRepository.class),
                List.of(strategy), mock(BillingAcquisitionService.class), transactionManager);
    }

    @Test
    void linesWithoutAClaim_neverConflict() {
        BillingSnapshot first = snapshot(OCT_1, OCT_31);
        BillingSnapshot second = snapshot(OCT_15, NOV_14);
        for (BillingSnapshot snapshot : List.of(first, second)) {
            snapshot.getItems().add(BillingSnapshotItem.builder()
                    .billingSnapshot(snapshot)
                    .itemType(BillingItemType.TOOL_CHARGE)
                    .sourceReferenceId("TOOL-1")
                    .amount(new BigDecimal("100"))
                    .build());
        }

        save(first);
        save(second);

        assertThat(itemRepository.count()).isEqualTo(2);
    }
}
