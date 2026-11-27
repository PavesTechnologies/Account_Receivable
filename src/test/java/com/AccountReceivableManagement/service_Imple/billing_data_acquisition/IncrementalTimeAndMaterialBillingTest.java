package com.AccountReceivableManagement.service_Imple.billing_data_acquisition;

import com.AccountReceivableManagement.builder.billing_data_acquisition.BillingSnapshotBuilder;
import com.AccountReceivableManagement.dependency.billing_data_acquisition.ProjectMasterDataService;
import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodAcquisitionDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodDiscoveryResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodReadiness;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.IncrementalBatchRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.NextBillingPeriodResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.BillingConfigurationIntegration;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.integration.billing_data_acquisition.XmsExpenseIntegrationImpl;
import com.AccountReceivableManagement.integration.currency.ExpenseCurrencyConverter;
import com.AccountReceivableManagement.integration.expense_management.XmsBillingPayloadDto;
import com.AccountReceivableManagement.integration.expense_management.XmsExpenseClient;
import com.AccountReceivableManagement.integration.expense_management.XmsHandedOffExpenseDto;
import com.AccountReceivableManagement.integration.expense_management.XmsProperties;
import com.AccountReceivableManagement.mapper.billing_data_acquisition.BillingSnapshotMapper;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotItemRepository;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_interface.billing_data_acquisition.BillingAcquisitionService;
import com.AccountReceivableManagement.strategy.billing_data_acquisition.TimeAndMaterialBillingStrategy;
import com.AccountReceivableManagement.validator.billing_data_acquisition.BillingAcquisitionValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Incremental, period-wise Time &amp; Material billing (acceptance scenarios SC01-SC07) against a real
 * (in-memory H2, MySQL mode) database. The production snapshot service, period discovery service,
 * Time &amp; Material strategy, XMS expense integration, validator, builder, mapper and repositories run as
 * in production; only the TMS / XMS HTTP clients and master-data lookups are replaced by in-memory fakes.
 * Not wrapped in a test transaction: each acquisition commits, as in production.
 *
 * <p>Configuration modelled on "Website Redesign": project 23, weekly, schedule 2026-08-05..2026-12-31,
 * today 2026-11-25. Weekly periods run Wednesday..Tuesday from Effective From:
 * Oct 21-27 ("October week 4"), Nov 4-10 ("November week 1"), Nov 11-17, Nov 18-24.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incremental_tm_billing;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncrementalTimeAndMaterialBillingTest {

    private static final Long PROJECT_ID = 23L;
    private static final UUID CONFIG_ID = UUID.fromString("671d1634-5f5d-4ee1-906a-85b959808b38");
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CURRENCY_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    private static final LocalDate OCT_W4_START = LocalDate.of(2026, 10, 21);
    private static final LocalDate OCT_W4_END = LocalDate.of(2026, 10, 27);
    private static final LocalDate NOV_W1_START = LocalDate.of(2026, 11, 4);
    private static final LocalDate NOV_W1_END = LocalDate.of(2026, 11, 10);
    private static final LocalDate NOV_W2_START = LocalDate.of(2026, 11, 11);
    private static final LocalDate NOV_W2_END = LocalDate.of(2026, 11, 17);
    private static final LocalDate NOV_W3_START = LocalDate.of(2026, 11, 18);
    private static final LocalDate NOV_W3_END = LocalDate.of(2026, 11, 24);

    @Autowired private BillingSnapshotRepository snapshotRepository;
    @Autowired private BillingSnapshotItemRepository itemRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    /** In-memory TMS: approved billable timesheets (TS id, work date, hours). */
    private final List<String[]> tms = new CopyOnWriteArrayList<>();
    /** In-memory XMS handed-off queue. */
    private final List<XmsHandedOffExpenseDto> xmsQueue = new CopyOnWriteArrayList<>();
    private final AtomicInteger xmsCalls = new AtomicInteger();
    private final AtomicInteger acquisitionRecords = new AtomicInteger();

    private BillingConfiguration configurationEntity;
    private BillingConfigurationResponseDto configuration;
    private BillingAcquisitionService acquisitionRecorder;
    private InvoiceRepository invoiceRepository;
    private final List<Invoice> invoices = new CopyOnWriteArrayList<>();

    private BillingSnapshotServiceImpl snapshotService;
    private BillingPeriodAcquisitionServiceImpl periodService;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        configureWeekly(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 12, 31), true, new BigDecimal("1500"));
        wire(TODAY);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            itemRepository.deleteAll();
            snapshotRepository.deleteAll();
        });
    }

    // ================================================================ harness

    private void configureWeekly(LocalDate from, LocalDate to, boolean expenses, BigDecimal hourlyRate) {
        configure(BillingFrequencyMaster.builder().billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Weekly").durationUnit(RenewalDurationUnit.DAYS).durationValue(7).build(),
                from, to, expenses, hourlyRate);
    }

    private void configure(BillingFrequencyMaster frequency, LocalDate from, LocalDate to, boolean expenses,
                           BigDecimal hourlyRate) {
        ProjectMasterReference project = ProjectMasterReference.builder()
                .pmsProjectId(PROJECT_ID).projectName("Website Redesign").clientId(CLIENT_ID)
                .primaryLocation("Karnataka").endDate(LocalDate.of(2027, 3, 31)).build();
        configurationEntity = BillingConfiguration.builder()
                .billingConfigurationId(CONFIG_ID)
                .project(project)
                .approvalStatus(ApprovalStatus.APPROVED)
                .billingType(BillingTypeMaster.builder().billingTypeId(UUID.randomUUID())
                        .billingTypeName("Time & Material").build())
                .billingFrequency(frequency)
                .effectiveFrom(from)
                .effectiveTo(to)
                .build();
        configuration = BillingConfigurationResponseDto.builder()
                .billingConfigurationId(CONFIG_ID)
                .projectId(PROJECT_ID)
                .billingType(BillingType.TIME_AND_MATERIAL)
                .billingTypeId(UUID.randomUUID())
                .billingTypeName("Time & Material")
                .billingFrequencyName(frequency.getBillingFrequencyName())
                .currencyId(CURRENCY_ID)
                .currencyCode("INR")
                .taxRegionCode("KA")
                .hourlyRate(hourlyRate)
                .expenseEligible(expenses)
                .approved(true)
                .build();
    }

    private void wire(LocalDate today) {
        BillingConfigurationRepository configurationRepository = mock(BillingConfigurationRepository.class);
        when(configurationRepository.findById(CONFIG_ID)).thenAnswer(inv -> Optional.of(configurationEntity));
        BillingOccurrenceServiceImpl occurrenceService = new BillingOccurrenceServiceImpl(null, null, null, null,
                null, null, configurationRepository, null, null, null, null, null);

        BillingConfigurationIntegration configurations = mock(BillingConfigurationIntegration.class);
        when(configurations.getApprovedBillingConfigurationById(CONFIG_ID)).thenAnswer(inv -> configuration);

        TimesheetIntegration timesheetIntegration = (projectId, start, end) -> tms.stream()
                .filter(row -> {
                    LocalDate workDate = LocalDate.parse(row[1]);
                    return !workDate.isBefore(start) && !workDate.isAfter(end);
                })
                .map(row -> TimesheetDto.builder()
                        .sourceReferenceId(row[0]).resourceName("Resource " + row[0])
                        .workDate(LocalDate.parse(row[1])).hours(new BigDecimal(row[2]))
                        .approvalStatus("APPROVED").approved(true).billable(true).build())
                .collect(Collectors.toCollection(ArrayList::new));

        XmsProperties xmsProperties = new XmsProperties();
        xmsProperties.setEnabled(true);
        XmsExpenseClient xmsClient = new XmsExpenseClient() {
            @Override
            public List<XmsHandedOffExpenseDto> fetchHandedOffExpenses(Long pmsProjectId) {
                xmsCalls.incrementAndGet();
                return new ArrayList<>(xmsQueue);
            }

            @Override
            public XmsBillingPayloadDto getBillingPayload(String lineItemId) {
                XmsHandedOffExpenseDto record = xmsQueue.stream()
                        .filter(r -> r.getLineItemId().equals(lineItemId)).findFirst().orElseThrow();
                XmsBillingPayloadDto payload = new XmsBillingPayloadDto();
                payload.setLineItemId(lineItemId);
                payload.setExpenseDate(record.getExpenseDate());
                payload.setCostBasis(record.getCostBasis());
                payload.setCurrency("INR");
                payload.setCategory("Travel");
                return payload;
            }
        };
        ProjectMasterReferenceRepository projects = mock(ProjectMasterReferenceRepository.class);
        when(projects.findBypmsProjectId(PROJECT_ID)).thenAnswer(inv -> Optional.of(configurationEntity.getProject()));
        XmsExpenseIntegrationImpl expenseIntegration = new XmsExpenseIntegrationImpl(xmsClient, xmsProperties,
                projects, itemRepository, new ExpenseCurrencyConverter(null, null));

        TimeAndMaterialBillingStrategy strategy = new TimeAndMaterialBillingStrategy(timesheetIntegration,
                mock(BillingTMRateCardRepository.class));
        ReflectionTestUtils.setField(strategy, "billingSnapshotItemRepository", itemRepository);
        ReflectionTestUtils.setField(strategy, "expenseIntegration", expenseIntegration);
        ReflectionTestUtils.setField(strategy, "xmsProperties", xmsProperties);

        ProjectMasterDataService projectMasterData = mock(ProjectMasterDataService.class);
        when(projectMasterData.getClientIdByProjectId(PROJECT_ID)).thenReturn(CLIENT_ID);
        CurrencyMasterRepository currencies = mock(CurrencyMasterRepository.class);
        when(currencies.findById(CURRENCY_ID)).thenReturn(Optional.of(CurrencyMaster.builder()
                .currencyId(CURRENCY_ID).currencyCode("INR").build()));
        TaxRegionMasterRepository taxRegions = mock(TaxRegionMasterRepository.class);
        when(taxRegions.findByTaxRegionNameIgnoreCase("Karnataka"))
                .thenReturn(Optional.of(TaxRegionMaster.builder().taxRegionCode("KA").build()));

        acquisitionRecorder = mock(BillingAcquisitionService.class);
        when(acquisitionRecorder.createManualAcquisition(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            acquisitionRecords.incrementAndGet();
            return AcquireDataResponseDto.builder().id(UUID.randomUUID()).projectId(PROJECT_ID)
                    .snapshotId(inv.getArgument(3)).status("READY").build();
        });

        invoiceRepository = mock(InvoiceRepository.class);
        when(invoiceRepository.findAllByBillingSnapshotIdIn(any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return invoices.stream().filter(invoice -> ids.contains(invoice.getBillingSnapshotId())).toList();
        });

        snapshotService = new BillingSnapshotServiceImpl(snapshotRepository, configurations, projectMasterData,
                new BillingAcquisitionValidator(itemRepository), new BillingSnapshotBuilder(),
                new BillingSnapshotMapper(), currencies, projects, taxRegions, List.of(strategy),
                acquisitionRecorder, transactionManager, invoiceRepository);
        ReflectionTestUtils.setField(snapshotService, "billingOccurrenceService", occurrenceService);
        periodService =new BillingPeriodAcquisitionServiceImpl(occurrenceService, configurations, strategy,
                new BillingAcquisitionValidator(itemRepository), snapshotRepository, invoiceRepository,
                snapshotService, Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private void timesheet(String id, LocalDate workDate, String hours) {
        tms.add(new String[]{id, workDate.toString(), hours});
    }

    private void expense(String lineItemId, LocalDate expenseDate, String amount) {
        XmsHandedOffExpenseDto record = new XmsHandedOffExpenseDto();
        record.setSyncId("SYNC-" + lineItemId);
        record.setLineItemId(lineItemId);
        record.setPmsProjectId(PROJECT_ID);
        record.setExpenseDate(expenseDate);
        record.setCostBasis(new BigDecimal(amount));
        record.setBaseCurrencyCode("INR");
        record.setHandedOffAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        xmsQueue.add(record);
    }

    private ApiResponse<BillingSnapshotResponseDto> acquire(LocalDate start, LocalDate end, String key) {
        return periodService.acquireBatch(CONFIG_ID, IncrementalBatchRequestDto.builder()
                .billingPeriodStart(start).billingPeriodEnd(end).idempotencyKey(key).build());
    }

    private BillingSnapshotResponseDto acquired(LocalDate start, LocalDate end) {
        ApiResponse<BillingSnapshotResponseDto> response = acquire(start, end, UUID.randomUUID().toString());
        assertThat(response.isSuccess()).as(response.getMessage()).isTrue();
        return response.getData();
    }

    /** What an issued invoice does to its snapshot: INVOICED, plus the invoice row. */
    private void invoice(UUID snapshotId, String invoiceNumber) {
        tx.executeWithoutResult(status -> {
            BillingSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
            snapshot.setStatus(BillingSnapshotStatus.INVOICED);
            snapshotRepository.save(snapshot);
        });
        invoices.add(Invoice.builder().invoiceId(UUID.randomUUID()).invoiceNumber(invoiceNumber)
                .billingSnapshotId(snapshotId).status(InvoiceStatus.APPROVED).build());
    }

    private Map<String, BillingPeriodAcquisitionDto> periodsByStart() {
        return periodService.discoverPeriods(CONFIG_ID, false).getPeriods().stream()
                .collect(Collectors.toMap(p -> p.getBillingPeriodStart().toString(), Function.identity()));
    }

    /** Frozen state of one snapshot: id, status, totals and its claimed sources. */
    private String fingerprint(UUID snapshotId) {
        return tx.execute(status -> {
            BillingSnapshot s = snapshotRepository.findById(snapshotId).orElseThrow();
            return s.getId() + "|" + s.getStatus() + "|" + s.getBatchSequence() + "|" + s.getSubtotal() + "|"
                    + s.getExpenseAmount() + "|" + s.getTotalAmount() + "|" + s.getUpdatedDate() + "|"
                    + s.getItems().stream().map(i -> i.getItemType() + ":" + i.getBilledSourceReferenceId() + ":"
                    + i.getAmount()).sorted().toList();
        });
    }

    private List<String> claimsOf(UUID snapshotId) {
        return itemRepository.findAll().stream()
                .filter(item -> item.getBillingSnapshot().getId().equals(snapshotId))
                .map(BillingSnapshotItem::getBilledSourceReferenceId).sorted().toList();
    }

    private List<String> allClaims() {
        return itemRepository.findAll().stream().map(BillingSnapshotItem::getBilledSourceReferenceId)
                .filter(java.util.Objects::nonNull).sorted().toList();
    }

    // ================================================================ SC01

    @Test
    void sc01_initialWeeklyAcquisition_createsOneBatchClaimingEachTimesheetOnce() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        timesheet("TS-2", LocalDate.of(2026, 11, 6), "4");

        BillingPeriodAcquisitionDto before = periodsByStart().get(NOV_W1_START.toString());
        assertThat(before.getBillingPeriodEnd()).isEqualTo(NOV_W1_END);
        assertThat(before.getNewTimesheetCount()).isEqualTo(2);
        assertThat(before.getNewBillableHours()).isEqualByComparingTo("12");
        assertThat(before.getEstimatedTimesheetAmount()).isEqualByComparingTo("18000");
        assertThat(before.getReadiness()).isEqualTo(BillingPeriodReadiness.READY);
        assertThat(before.getRateValidationPassed()).isTrue();
        assertThat(before.isPreviouslyAcquired()).isFalse();

        BillingSnapshotResponseDto batch = acquired(NOV_W1_START, NOV_W1_END);

        assertThat(batch.getBatchSequence()).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(BillingSnapshotStatus.READY_FOR_TAX);
        assertThat(batch.getTotalAmount()).isEqualByComparingTo("18000");
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(acquisitionRecords.get()).isEqualTo(1);
        assertThat(allClaims()).containsExactly("TS-1", "TS-2");

        BillingPeriodAcquisitionDto after = periodsByStart().get(NOV_W1_START.toString());
        assertThat(after.getReadiness()).isEqualTo(BillingPeriodReadiness.NO_NEW_ITEMS);
        assertThat(after.isPreviouslyAcquired()).isTrue();
        assertThat(after.getBatches()).singleElement()
                .satisfies(b -> assertThat(b.getSnapshotId()).isEqualTo(batch.getSnapshotId()));
    }

    // ================================================================ SC02

    @Test
    void sc02_newTimesheetsInAnAlreadyInvoicedWeek_becomeASeparateBatch_earlierOneUnchanged() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        timesheet("TS-2", LocalDate.of(2026, 11, 5), "8");
        BillingSnapshotResponseDto first = acquired(NOV_W1_START, NOV_W1_END);
        invoice(first.getSnapshotId(), "INV-0001");
        String firstBefore = fingerprint(first.getSnapshotId());

        timesheet("TS-3", LocalDate.of(2026, 11, 6), "2");
        timesheet("TS-4", LocalDate.of(2026, 11, 9), "3");
        timesheet("TS-5", LocalDate.of(2026, 11, 10), "5");

        BillingPeriodAcquisitionDto refreshed = periodsByStart().get(NOV_W1_START.toString());
        assertThat(refreshed.isHasNewItems()).isTrue();
        assertThat(refreshed.getNewTimesheetCount()).isEqualTo(3);
        assertThat(refreshed.getNewBillableHours()).isEqualByComparingTo("10");
        assertThat(refreshed.getReadiness()).isEqualTo(BillingPeriodReadiness.READY);
        assertThat(refreshed.getBatches()).singleElement().satisfies(b -> {
            assertThat(b.getInvoiceNumber()).isEqualTo("INV-0001");
            assertThat(b.getSnapshotStatus()).isEqualTo(BillingSnapshotStatus.INVOICED);
        });

        BillingSnapshotResponseDto second = acquired(NOV_W1_START, NOV_W1_END);

        assertThat(second.getSnapshotId()).isNotEqualTo(first.getSnapshotId());
        assertThat(second.getBatchSequence()).isEqualTo(2);
        assertThat(second.getExcludedAlreadyBilledTimesheetCount()).isEqualTo(2);
        assertThat(second.getTotalAmount()).isEqualByComparingTo("15000");
        assertThat(claimsOf(second.getSnapshotId())).containsExactly("TS-3", "TS-4", "TS-5");
        assertThat(fingerprint(first.getSnapshotId())).isEqualTo(firstBefore);
        assertThat(allClaims()).containsExactly("TS-1", "TS-2", "TS-3", "TS-4", "TS-5");
        assertThat(periodsByStart().get(NOV_W1_START.toString()).getBatches()).hasSize(2);
    }

    // ================================================================ SC03

    @Test
    void sc03_lateSubmissionForAnEarlierPeriod_isDiscoveredAndBilledSeparately() {
        timesheet("TS-N1", LocalDate.of(2026, 11, 4), "8");
        timesheet("TS-N2", LocalDate.of(2026, 11, 12), "8");
        timesheet("TS-N3", LocalDate.of(2026, 11, 19), "8");
        List<UUID> november = new ArrayList<>();
        int n = 1;
        for (LocalDate[] week : new LocalDate[][]{{NOV_W1_START, NOV_W1_END}, {NOV_W2_START, NOV_W2_END},
                {NOV_W3_START, NOV_W3_END}}) {
            UUID id = acquired(week[0], week[1]).getSnapshotId();
            invoice(id, "INV-NOV-" + n++);
            november.add(id);
        }
        List<String> novemberBefore = november.stream().map(this::fingerprint).toList();

        timesheet("TS-O1", LocalDate.of(2026, 10, 22), "6");
        timesheet("TS-O2", LocalDate.of(2026, 10, 27), "2");

        BillingPeriodDiscoveryResponseDto withNewItems = periodService.discoverPeriods(CONFIG_ID, true);
        assertThat(withNewItems.getPeriods()).singleElement().satisfies(p -> {
            assertThat(p.getBillingPeriodStart()).isEqualTo(OCT_W4_START);
            assertThat(p.getBillingPeriodEnd()).isEqualTo(OCT_W4_END);
            assertThat(p.getNewTimesheetCount()).isEqualTo(2);
            assertThat(p.isPeriodEnded()).isTrue();
            assertThat(p.getReadiness()).isEqualTo(BillingPeriodReadiness.READY);
        });

        BillingSnapshotResponseDto october = acquired(OCT_W4_START, OCT_W4_END);

        assertThat(october.getBillingPeriodStart()).isEqualTo(OCT_W4_START);
        assertThat(claimsOf(october.getSnapshotId())).containsExactly("TS-O1", "TS-O2");
        assertThat(november.stream().map(this::fingerprint).toList()).isEqualTo(novemberBefore);
        assertThat(periodService.discoverPeriods(CONFIG_ID, true).getPeriods()).isEmpty();
    }

    // ================================================================ SC04

    @Test
    void sc04_newExpenseInABilledWeek_isAcquiredAlone_previouslyBilledExpenseExcluded() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        expense("EXP-1", LocalDate.of(2026, 11, 5), "2500");
        BillingSnapshotResponseDto first = acquired(NOV_W1_START, NOV_W1_END);
        assertThat(first.getExpenseAmount()).isEqualByComparingTo("2500");
        invoice(first.getSnapshotId(), "INV-0001");
        String firstBefore = fingerprint(first.getSnapshotId());

        expense("EXP-2", LocalDate.of(2026, 11, 8), "700");

        BillingPeriodAcquisitionDto refreshed = periodsByStart().get(NOV_W1_START.toString());
        assertThat(refreshed.getNewTimesheetCount()).isZero();
        assertThat(refreshed.getNewExpenseCount()).isEqualTo(1);
        assertThat(refreshed.getNewExpenseAmount()).isEqualByComparingTo("700");
        assertThat(refreshed.getReadiness()).isEqualTo(BillingPeriodReadiness.READY);

        BillingSnapshotResponseDto second = acquired(NOV_W1_START, NOV_W1_END);

        assertThat(second.getTimesheets()).isEmpty();
        assertThat(second.getExpenses()).singleElement()
                .satisfies(e -> assertThat(e.getLineItemId()).isEqualTo("EXP-2"));
        assertThat(second.getTotalAmount()).isEqualByComparingTo("700");
        assertThat(fingerprint(first.getSnapshotId())).isEqualTo(firstBefore);
        assertThat(itemRepository.findAll().stream()
                .filter(i -> i.getItemType() == BillingItemType.EXPENSE)
                .map(BillingSnapshotItem::getBilledSourceReferenceId).sorted().toList())
                .containsExactly("EXP-1", "EXP-2");
    }

    @Test
    void expenseClaim_isUniqueInTheDatabase_evenIfApplicationChecksWereBypassed() {
        expense("EXP-1", LocalDate.of(2026, 11, 5), "2500");
        BillingSnapshotResponseDto first = acquired(NOV_W1_START, NOV_W1_END);

        BillingSnapshot copy = tx.execute(status -> {
            BillingSnapshot original = snapshotRepository.findById(first.getSnapshotId()).orElseThrow();
            BillingSnapshot duplicate = BillingSnapshot.builder()
                    .snapshotNumber("BS-DUP").billingConfigurationId(CONFIG_ID).clientId(CLIENT_ID)
                    .projectId(PROJECT_ID).billingTypeId(UUID.randomUUID()).currencyId(CURRENCY_ID)
                    .currencyCode("INR").billingPeriodStart(NOV_W1_START).billingPeriodEnd(NOV_W1_END)
                    .batchSequence(2).status(BillingSnapshotStatus.READY_FOR_TAX).build();
            BillingSnapshotItem item = BillingSnapshotItem.builder().billingSnapshot(duplicate)
                    .itemType(BillingItemType.EXPENSE).sourceReferenceId("EXP-1")
                    .billedSourceReferenceId(original.getItems().get(0).getBilledSourceReferenceId())
                    .amount(new BigDecimal("2500")).build();
            duplicate.getItems().add(item);
            return duplicate;
        });

        assertThatThrownBy(() -> tx.execute(status -> snapshotRepository.saveAndFlush(copy)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshotRepository.count()).isEqualTo(1);
    }

    // ================================================================ SC05

    @Test
    void sc05_expenseBillingDisabled_expensesNeverReadNorBilled_timesheetsAcquired() {
        configureWeekly(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 12, 31), false, new BigDecimal("1500"));
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        expense("EXP-1", LocalDate.of(2026, 11, 5), "2500");

        BillingPeriodAcquisitionDto period = periodsByStart().get(NOV_W1_START.toString());
        assertThat(period.getNewExpenseCount()).isZero();
        assertThat(period.getNewTimesheetCount()).isEqualTo(1);

        BillingSnapshotResponseDto batch = acquired(NOV_W1_START, NOV_W1_END);

        assertThat(batch.getExpenses()).isEmpty();
        assertThat(batch.getTimesheets()).hasSize(1);
        assertThat(xmsCalls.get()).isZero();
    }

    // ================================================================ SC06

    @Test
    void sc06_repeatedRequestWithTheSameKey_returnsTheSameBatch_nothingClaimedTwice() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");

        ApiResponse<BillingSnapshotResponseDto> first = acquire(NOV_W1_START, NOV_W1_END, "click-42");
        ApiResponse<BillingSnapshotResponseDto> replay = acquire(NOV_W1_START, NOV_W1_END, "click-42");

        assertThat(first.isSuccess()).isTrue();
        assertThat(replay.isSuccess()).isTrue();
        assertThat(replay.getData().getSnapshotId()).isEqualTo(first.getData().getSnapshotId());
        assertThat(replay.getData().getExistingSnapshot()).isTrue();
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(acquisitionRecords.get()).isEqualTo(1);
        assertThat(allClaims()).containsExactly("TS-1");
    }

    @Test
    void sc06_repeatedRequestWithoutNewRecords_createsNothing() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        acquired(NOV_W1_START, NOV_W1_END);

        ApiResponse<BillingSnapshotResponseDto> again = acquire(NOV_W1_START, NOV_W1_END, null);

        assertThat(again.isSuccess()).isFalse();
        assertThat(again.getMessage()).contains("No new approved, billable timesheets")
                .contains("1 approved timesheet(s) are already billed");
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(allClaims()).containsExactly("TS-1");
    }

    @Test
    void sc06_sameKeyForAnotherPeriod_isRejected() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        timesheet("TS-2", LocalDate.of(2026, 11, 12), "8");
        acquire(NOV_W1_START, NOV_W1_END, "click-1");

        ApiResponse<BillingSnapshotResponseDto> misuse = acquire(NOV_W2_START, NOV_W2_END, "click-1");

        assertThat(misuse.isSuccess()).isFalse();
        assertThat(misuse.getMessage()).contains("already used for billing period");
        assertThat(snapshotRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentAcquisitionsOfOnePeriod_billEachTimesheetExactlyOnce() throws Exception {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        timesheet("TS-2", LocalDate.of(2026, 11, 5), "8");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ApiResponse<BillingSnapshotResponseDto>>> attempts = new ArrayList<>();
            for (String key : List.of("user-a", "user-b")) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    return acquire(NOV_W1_START, NOV_W1_END, key);
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<ApiResponse<BillingSnapshotResponseDto>> attempt : attempts) {
                try {
                    if (attempt.get(60, TimeUnit.SECONDS).isSuccess()) {
                        succeeded++;
                    }
                } catch (java.util.concurrent.ExecutionException ex) {
                    // A lock/transient failure of the loser is acceptable; double billing is not.
                    assertThat(ex.getCause()).isInstanceOf(org.springframework.dao.DataAccessException.class);
                }
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(allClaims()).containsExactly("TS-1", "TS-2");
    }

    // ================================================================ SC07 - grouping

    @Test
    void sc07_monthlyFrequency_groupsByConfiguredMonthlyBoundaries_byWorkDate() {
        configure(BillingFrequencyMaster.builder().billingFrequencyId(UUID.randomUUID())
                        .billingFrequencyName("Monthly").durationUnit(RenewalDurationUnit.MONTHS).durationValue(1).build(),
                LocalDate.of(2026, 8, 5), LocalDate.of(2026, 12, 31), false, new BigDecimal("1000"));
        timesheet("TS-A", LocalDate.of(2026, 9, 4), "1");   // last day of period Aug 5 - Sep 4
        timesheet("TS-B", LocalDate.of(2026, 9, 5), "2");   // first day of period Sep 5 - Oct 4
        timesheet("TS-C", LocalDate.of(2026, 10, 4), "3");

        Map<String, BillingPeriodAcquisitionDto> periods = periodsByStart();

        assertThat(periods.get("2026-08-05").getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(periods.get("2026-08-05").getNewTimesheetCount()).isEqualTo(1);
        assertThat(periods.get("2026-09-05").getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(periods.get("2026-09-05").getNewTimesheetCount()).isEqualTo(2);
        assertThat(periods.get("2026-09-05").getNewBillableHours()).isEqualByComparingTo("5");
        // Nov 5 - Dec 4 has started (today Nov 25); Dec 5 - Dec 31 has not, so it is not offered yet.
        assertThat(periods).containsKey("2026-11-05").doesNotContainKey("2026-12-05");

        BillingSnapshotResponseDto september = acquired(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 4));
        assertThat(claimsOf(september.getSnapshotId())).containsExactly("TS-B", "TS-C");
    }

    @Test
    void sc07_weeklyFrequency_partialLastPeriod_endsOnTheScheduleEnd() {
        wire(LocalDate.of(2027, 1, 10));
        timesheet("TS-Z", LocalDate.of(2026, 12, 31), "4");
        timesheet("TS-OUT", LocalDate.of(2027, 1, 2), "4"); // after the schedule - never billed

        BillingPeriodDiscoveryResponseDto discovery = periodService.discoverPeriods(CONFIG_ID, true);

        assertThat(discovery.getScheduleEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(discovery.getPeriods()).singleElement().satisfies(p -> {
            assertThat(p.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 12, 30));
            assertThat(p.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(p.isPartialPeriod()).isTrue();
            assertThat(p.getNewTimesheetCount()).isEqualTo(1);
        });
    }

    @Test
    void periodBoundariesMustBeOneOfTheConfiguredPeriods() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");

        assertThatThrownBy(() -> acquire(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("not one of this configuration's billing periods");
        assertThatThrownBy(() -> acquire(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 8), null))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        assertThat(snapshotRepository.count()).isZero();
    }

    // ================================================================ validation and integrity

    @Test
    void missingRate_blocksThePeriod_andAcquisitionCreatesNothing() {
        configureWeekly(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 12, 31), false, null);
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");

        BillingPeriodAcquisitionDto period = periodsByStart().get(NOV_W1_START.toString());
        assertThat(period.getReadiness()).isEqualTo(BillingPeriodReadiness.BLOCKED);
        assertThat(period.getRateValidationPassed()).isFalse();
        assertThat(period.getBlockingReasons()).anyMatch(reason -> reason.contains("No active hourly rate"));

        ApiResponse<BillingSnapshotResponseDto> response = acquire(NOV_W1_START, NOV_W1_END, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("No active hourly rate");
        assertThat(snapshotRepository.count()).isZero();
        assertThat(allClaims()).isEmpty();
    }

    @Test
    void failureWhileRecordingTheBatch_rollsBackSnapshotAndClaims() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        when(acquisitionRecorder.createManualAcquisition(any(), any(), any(), any(), any()))
                .thenThrow(new GlobalExceptionHandler.ValidationException("Billing Configuration is not active"));

        assertThatThrownBy(() -> acquire(NOV_W1_START, NOV_W1_END, "k"))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class);

        assertThat(snapshotRepository.count()).isZero();
        assertThat(allClaims()).isEmpty();
        // Nothing was claimed, so a retry after the problem is fixed acquires the record.
        assertThat(periodsByStart().get(NOV_W1_START.toString()).getNewTimesheetCount()).isEqualTo(1);
    }

    @Test
    void cancelledBatch_releasesItsRecords_whichTheNextBatchAcquires() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        BillingSnapshotResponseDto first = acquired(NOV_W1_START, NOV_W1_END);
        tx.executeWithoutResult(status -> snapshotService.cancelBillingSnapshot(first.getSnapshotId()));

        BillingPeriodAcquisitionDto period = periodsByStart().get(NOV_W1_START.toString());
        assertThat(period.getNewTimesheetCount()).isEqualTo(1);
        assertThat(period.isPreviouslyAcquired()).isFalse();

        BillingSnapshotResponseDto second = acquired(NOV_W1_START, NOV_W1_END);
        assertThat(second.getBatchSequence()).isEqualTo(2);
        assertThat(claimsOf(second.getSnapshotId())).containsExactly("TS-1");
    }

    @Test
    void legacySnapshotEndpoint_stillReusesTheFirstBatch_andNeverAcquiresAgain() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        BillingSnapshotResponseDto first = acquired(NOV_W1_START, NOV_W1_END);
        timesheet("TS-2", LocalDate.of(2026, 11, 5), "8");
        acquired(NOV_W1_START, NOV_W1_END);

        ApiResponse<BillingSnapshotResponseDto> legacy = snapshotService.createBillingSnapshot(
                BillingSnapshotCreateRequestDto.builder().projectId(PROJECT_ID).billingConfigurationId(CONFIG_ID)
                        .billingPeriodStart(NOV_W1_START).billingPeriodEnd(NOV_W1_END).build());

        assertThat(legacy.isSuccess()).isTrue();
        assertThat(legacy.getData().getExistingSnapshot()).isTrue();
        assertThat(legacy.getData().getSnapshotId()).isEqualTo(first.getSnapshotId());
        assertThat(snapshotRepository.count()).isEqualTo(2);
        assertThat(snapshotService.getByProjectAndPeriod(PROJECT_ID, NOV_W1_START, NOV_W1_END, CONFIG_ID)
                .getData().getSnapshotId()).isEqualTo(first.getSnapshotId());
    }

    @Test
    void sourceUnavailable_isReportedPerPeriod_notThrown() {
        expense("EXP-BAD", LocalDate.of(2026, 11, 5), "100");
        xmsQueue.get(0).setPmsProjectId(999L); // XMS contradicts the request

        BillingPeriodDiscoveryResponseDto discovery = periodService.discoverPeriods(CONFIG_ID, false);

        assertThat(discovery.getWarnings()).anyMatch(w -> w.contains("Expense Management"));
        assertThat(discovery.getPeriods()).allMatch(p -> p.getReadiness() == BillingPeriodReadiness.SOURCE_UNAVAILABLE);
    }

    // ================================================================ next period

    @Test
    void nextPeriod_resolvesTheConfigurationById_andOffersTheEarliestPeriodWithUnbilledWork() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        timesheet("TS-2", LocalDate.of(2026, 11, 12), "4");

        NextBillingPeriodResponseDto next = periodService.nextPeriod(CONFIG_ID);

        assertThat(next.getBillingConfigurationId()).isEqualTo(CONFIG_ID);
        assertThat(next.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(next.getBillingFrequency()).isEqualTo("Weekly");
        assertThat(next.getBillingPeriodStart()).isEqualTo(NOV_W1_START);
        assertThat(next.getBillingPeriodEnd()).isEqualTo(NOV_W1_END);
        assertThat(next.isPeriodEnded()).isTrue();
        assertThat(next.isAcquisitionAllowed()).isTrue();
        assertThat(next.getEligibleRecordCount()).isEqualTo(1);
        assertThat(next.getEligibleHours()).isEqualByComparingTo("8");
        assertThat(next.getPreviousSnapshotId()).isNull();
        assertThat(snapshotRepository.count()).as("read-only").isZero();
    }

    @Test
    void nextPeriod_afterAnInvoicedWeek_offersTheFollowingWeek_withThePreviousBatch() {
        timesheet("TS-1", LocalDate.of(2026, 11, 4), "8");
        BillingSnapshotResponseDto first = acquired(NOV_W1_START, NOV_W1_END);
        invoice(first.getSnapshotId(), "INV-0001");
        timesheet("TS-2", LocalDate.of(2026, 11, 12), "4");

        NextBillingPeriodResponseDto next = periodService.nextPeriod(CONFIG_ID);

        assertThat(next.getBillingPeriodStart()).isEqualTo(NOV_W2_START);
        assertThat(next.getBillingPeriodEnd()).isEqualTo(NOV_W2_END);
        assertThat(next.isAcquisitionAllowed()).isTrue();
        assertThat(next.isAlreadyInvoiced()).isFalse();
        assertThat(next.getPreviousSnapshotId()).isEqualTo(first.getSnapshotId());
        assertThat(next.getPreviousPeriodStart()).isEqualTo(NOV_W1_START);
        assertThat(next.getPreviousSnapshotStatus()).isEqualTo("INVOICED");
        assertThat(next.isPreviousPeriodInvoiced()).isTrue();
    }

    @Test
    void nextPeriod_nothingUnbilled_offersTheCurrentOpenPeriod_withoutAllowingAcquisition() {
        NextBillingPeriodResponseDto next = periodService.nextPeriod(CONFIG_ID);

        assertThat(next.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(next.isPeriodEnded()).isFalse();
        assertThat(next.isAcquisitionAllowed()).isFalse();
        assertThat(next.getReason()).contains("can be acquired once it has ended");
    }

    @Test
    void nextPeriod_sourceUnavailable_isNotAllowed_andExplainsWhy() {
        expense("EXP-BAD", LocalDate.of(2026, 11, 5), "100");
        xmsQueue.get(0).setPmsProjectId(999L);

        NextBillingPeriodResponseDto next = periodService.nextPeriod(CONFIG_ID);

        assertThat(next.isAcquisitionAllowed()).isFalse();
        assertThat(next.getReason()).contains("Expense Management");
    }


    @Test
    void nextPeriod_earlierBlockedPeriod_doesNotHoldBackALaterReadyOne_andIsReportedAsWarning() {
        timesheet("TS-BAD", LocalDate.of(2026, 10, 22), "0"); // invalid hours: Oct week 4 BLOCKED
        timesheet("TS-OK", LocalDate.of(2026, 11, 12), "4");

        NextBillingPeriodResponseDto next = periodService.nextPeriod(CONFIG_ID);

        assertThat(next.getBillingPeriodStart()).isEqualTo(NOV_W2_START);
        assertThat(next.isAcquisitionAllowed()).isTrue();
        assertThat(next.getWarnings()).singleElement().satisfies(w -> {
            assertThat(w).contains(OCT_W4_START.toString());
            assertThat(w).contains("invalid hours");
        });
        assertThat(claimsOf(acquired(NOV_W2_START, NOV_W2_END).getSnapshotId())).containsExactly("TS-OK");
    }

    @Test
    void nextPeriod_noReadyPeriod_offersTheEarliestBlockedOne_notAllowed() {
        timesheet("TS-BAD", LocalDate.of(2026, 10, 22), "0");

        NextBillingPeriodResponseDto next = periodService.nextPeriod(CONFIG_ID);

        assertThat(next.getBillingPeriodStart()).isEqualTo(OCT_W4_START);
        assertThat(next.isAcquisitionAllowed()).isFalse();
        assertThat(next.getReason()).contains("invalid hours");
        assertThat(next.getWarnings()).isEmpty();
    }

    // ---- legacy (non-aligned) snapshots

    @Test
    void legacyEndpoint_rejectsAPeriodThatIsNotOneOfTheWeeklyPeriods_creatingNothing() {
        timesheet("TS-1", LocalDate.of(2026, 11, 5), "8");

        ApiResponse<BillingSnapshotResponseDto> response = snapshotService.createBillingSnapshot(
                BillingSnapshotCreateRequestDto.builder().projectId(PROJECT_ID).billingConfigurationId(CONFIG_ID)
                        .billingPeriodStart(LocalDate.of(2026, 11, 1)).billingPeriodEnd(LocalDate.of(2026, 11, 30))
                        .build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("is not one of this Time & Material configuration's billing periods");
        assertThat(snapshotRepository.count()).isZero();
        assertThat(allClaims()).isEmpty();
    }

    @Test
    void legacyEndpoint_stillAcceptsAConfiguredWeeklyPeriod() {
        timesheet("TS-1", LocalDate.of(2026, 11, 5), "8");

        ApiResponse<BillingSnapshotResponseDto> response = snapshotService.createBillingSnapshot(
                BillingSnapshotCreateRequestDto.builder().projectId(PROJECT_ID).billingConfigurationId(CONFIG_ID)
                        .billingPeriodStart(NOV_W1_START).billingPeriodEnd(NOV_W1_END).build());

        assertThat(response.isSuccess()).as(response.getMessage()).isTrue();
        assertThat(allClaims()).containsExactly("TS-1");
    }

    @Test
    void existingMonthlySnapshotOnWeeklyConfig_isShownOnOverlappingWeeks_andItsTimesheetsAreNeverBilledAgain() {
        timesheet("TS-N1", LocalDate.of(2026, 11, 5), "8");
        timesheet("TS-N2", LocalDate.of(2026, 11, 12), "8");
        // A snapshot created before the period check existed (as BS-20261125165121-BD55FD60 was).
        ReflectionTestUtils.setField(snapshotService, "billingOccurrenceService", null);
        BillingSnapshotResponseDto monthly = snapshotService.createBillingSnapshot(
                BillingSnapshotCreateRequestDto.builder().projectId(PROJECT_ID).billingConfigurationId(CONFIG_ID)
                        .billingPeriodStart(LocalDate.of(2026, 11, 1)).billingPeriodEnd(LocalDate.of(2026, 11, 30))
                        .build()).getData();
        invoice(monthly.getSnapshotId(), "INV-NOV");
        wire(TODAY);
        String before = fingerprint(monthly.getSnapshotId());

        Map<String, BillingPeriodAcquisitionDto> periods = periodsByStart();
        for (LocalDate start : List.of(NOV_W1_START, NOV_W2_START, NOV_W3_START)) {
            BillingPeriodAcquisitionDto week = periods.get(start.toString());
            assertThat(week.getBatches()).as("aligned batches only").isEmpty();
            assertThat(week.isPreviouslyAcquired()).isFalse();
            assertThat(week.getNewTimesheetCount()).isZero();
            assertThat(week.getOverlappingSnapshots()).singleElement().satisfies(s -> {
                assertThat(s.getSnapshotId()).isEqualTo(monthly.getSnapshotId());
                assertThat(s.isPeriodAligned()).isFalse();
                assertThat(s.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
                assertThat(s.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 30));
                assertThat(s.getInvoiceNumber()).isEqualTo("INV-NOV");
            });
        }
        assertThat(periods.get(OCT_W4_START.toString()).getOverlappingSnapshots()).isEmpty();

        ApiResponse<BillingSnapshotResponseDto> again = acquire(NOV_W1_START, NOV_W1_END, null);
        assertThat(again.isSuccess()).isFalse();
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(claimsOf(monthly.getSnapshotId())).containsExactly("TS-N1", "TS-N2");
        assertThat(fingerprint(monthly.getSnapshotId())).as("display only").isEqualTo(before);
    }

    @Test
    void nextPeriod_unknownConfiguration_isStillNotFound() {
        assertThatThrownBy(() -> periodService.nextPeriod(UUID.randomUUID()))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Approved Billing Configuration not found");
    }
}
