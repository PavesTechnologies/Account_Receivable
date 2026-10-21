package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.UserDTO;
import com.AccountReceivableManagement.dto.projectbilling_config.RenewalRequestDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.company_profile.CompanyProfile;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxConfigurationComponent;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRegionMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxTypeMaster;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingStep;
import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingContext;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ContractValueSource;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.InvoiceGenerationType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalOptionType;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.company_profile.CompanyProfileRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingMilestonePlanRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTypeMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.CurrencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxTypeMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import com.AccountReceivableManagement.service_interface.projectbilling_config.RecurringBillingService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Batch 1 financial-integrity regression tests (audit 2026-10-21: G-01, G-02,
 * G-04, G-05, G-06, G-07, G-09, G-10). Real application context on an
 * in-memory database (H2, MySQL mode): configurations are approved and
 * re-approved through BillingConfigurationService (with the record lock),
 * renewed through RecurringBillingService, and taxed / invoiced through the
 * manual service entry points. Assertions are on persisted occurrence
 * amounts and identities. "Billed" periods are set with SQL, as invoicing
 * leaves them (INVOICED, invoiced flag) - the invoice itself is not needed
 * for schedule reconciliation.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:financial_integrity;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "client-cdc.enabled=false",
        "project-cdc.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"
})
@ActiveProfiles("test")
class FinancialIntegrityRegressionTest {

    @MockBean private TimesheetIntegration timesheetIntegration;
    @MockBean private EmailService emailService;
    @MockBean private CurrentUserService currentUserService;

    @Autowired private BillingConfigurationService configurationService;
    @Autowired private RecurringBillingService recurringBillingService;
    @Autowired private BillingOccurrenceServiceImpl occurrenceService;
    @Autowired private TaxCalculationService taxCalculationService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private AutomaticBillingRunService runService;
    @Autowired private RecordActionLockService lockService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectMasterReferenceRepository projectRepository;
    @Autowired private CurrencyMasterRepository currencyRepository;
    @Autowired private TaxRegionMasterRepository taxRegionRepository;
    @Autowired private TaxTypeMasterRepository taxTypeRepository;
    @Autowired private TaxConfigurationRepository taxConfigurationRepository;
    @Autowired private PaymentTermsMasterRepository paymentTermsRepository;
    @Autowired private BillingFrequencyMasterRepository frequencyRepository;
    @Autowired private BillingTypeMasterRepository billingTypeRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private BillingConfigurationRepository configurationRepository;
    @Autowired private BillingFixedPriceRepository fixedPriceRepository;
    @Autowired private BillingRecurringConfigurationRepository recurringRepository;
    @Autowired private BillingMilestonePlanRepository milestonePlanRepository;
    @Autowired private BillingPaymentEntryRepository paymentEntryRepository;
    @Autowired private BillingScheduleRepository scheduleRepository;
    @Autowired private TaxCalculationRepository taxCalculationRepository;
    @Autowired private AutomaticBillingRunRepository runRepository;

    private TransactionTemplate tx;
    private final LocalDate today = LocalDate.now();
    private Client client;
    private ProjectMasterReference project;
    private CurrencyMaster inr;
    private TaxRegionMaster karnataka;
    private PaymentTermsMaster net30;
    private BillingFrequencyMaster monthly;
    private BillingFrequencyMaster quarterly;
    private BillingFrequencyMaster yearly;
    private BillingFrequencyMaster oneTime;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        emptyDatabase();
        when(currentUserService.getCurrentUser()).thenReturn(new UserDTO(
                7L, "finance.manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of()));
        when(currentUserService.getUserId()).thenReturn(7L);
        tx.executeWithoutResult(status -> seedMasterData());
    }

    // ================================================================ G-01 recurring re-approval

    @Nested
    class G01_RecurringReapproval {

        @Test
        void unchangedReapproval_keepsEveryProratedAmountAndOccurrenceIdentity() {
            BillingConfiguration configuration = recurring("120000.00", futureMonthStart(), 4);
            Map<UUID, BigDecimal> before = amountsById(configuration);
            assertThat(before).hasSize(4);
            assertThat(sum(before)).isEqualByComparingTo("120000.00");

            reapprove(configuration);

            assertThat(amountsById(configuration)).isEqualTo(before);
        }

        @Test
        void reapprovalAfterAPeriodWasInvoiced_succeeds_andLeavesAmountsUnchanged() {
            BillingConfiguration configuration = recurring("120000.00", futureMonthStart(), 4);
            markInvoiced(active(configuration).get(0));
            Map<UUID, BigDecimal> before = amountsById(configuration);

            reapprove(configuration);

            assertThat(amountsById(configuration)).isEqualTo(before);
            assertThat(approvalStatus(configuration)).isEqualTo(ApprovalStatus.APPROVED);
        }

        @Test
        void contractValueChangeBeforeBilling_reallocatesTheProratedShares_inPlace() {
            BillingConfiguration configuration = recurring("120000.00", futureMonthStart(), 4);
            Map<UUID, BigDecimal> before = amountsById(configuration);
            markTaxPending(active(configuration).get(0));

            updateRecurring(configuration, recurring -> recurring.setContractValue(new BigDecimal("240000.00")));
            reapprove(configuration);

            Map<UUID, BigDecimal> after = amountsById(configuration);
            assertThat(after.keySet()).isEqualTo(before.keySet());
            assertThat(sum(after)).isEqualByComparingTo("240000.00");
            // Every prorated share doubles (up to the last period's rounding remainder); none is the contract value.
            before.forEach((id, amount) -> assertThat(after.get(id))
                    .isBetween(amount.multiply(BigDecimal.valueOf(2)).subtract(new BigDecimal("0.02")),
                            amount.multiply(BigDecimal.valueOf(2)).add(new BigDecimal("0.02")))
                    .isLessThan(new BigDecimal("240000.00")));
        }

        @Test
        void contractValueChangeAfterBilling_isRejected_withoutPartialChanges() {
            BillingConfiguration configuration = recurring("120000.00", futureMonthStart(), 4);
            markInvoiced(active(configuration).get(0));
            Map<UUID, BigDecimal> before = amountsById(configuration);

            updateRecurring(configuration, recurring -> recurring.setContractValue(new BigDecimal("240000.00")));
            assertThatThrownBy(() -> reapprove(configuration))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("already been billed");

            assertThat(amountsById(configuration)).isEqualTo(before);
            assertThat(approvalStatus(configuration)).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        }
    }

    // ================================================================ G-02 / G-04 fixed price

    @Nested
    class G02_G04_FixedPrice {

        @Test
        void unchangedReapproval_isIdempotent() {
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, futureMonthStart(), 3);
            Map<UUID, BigDecimal> before = amountsById(configuration);

            reapprove(configuration);
            reapprove(configuration);

            assertThat(amountsById(configuration)).isEqualTo(before);
            assertThat(before.values()).allSatisfy(a -> assertThat(a).isEqualByComparingTo("100000.00"));
        }

        @Test
        void reapprovalAfterAPeriodWasInvoiced_succeeds_andRewritesNothing() {
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, futureMonthStart(), 3);
            markInvoiced(active(configuration).get(0));
            Map<UUID, BigDecimal> before = amountsById(configuration);

            reapprove(configuration);

            assertThat(amountsById(configuration)).isEqualTo(before);
        }

        @Test
        void regenerationAfterPartialBilling_allocatesOnlyTheUnbilledRemainder() {
            // Contract 300,000 over three periods of 100,000; the first is invoiced, then Effective To is extended.
            LocalDate start = futureMonthStart();
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, start, 3);
            BillingSchedule billed = active(configuration).get(0);
            markInvoiced(billed);

            updateConfiguration(configuration, c -> c.setEffectiveTo(start.plusMonths(4).minusDays(1)));
            reapprove(configuration);

            List<BillingSchedule> after = active(configuration);
            assertThat(after).hasSize(4);
            assertThat(after.get(0).getBillingScheduleId()).isEqualTo(billed.getBillingScheduleId());
            assertThat(after.get(0).getBillingAmount()).isEqualByComparingTo("100000.00");
            assertThat(after.stream().filter(s -> s.getPeriodStartDate().equals(start))).hasSize(1);
            assertThat(after.subList(1, 4)).extracting(BillingSchedule::getBillingAmount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("66666.67"), new BigDecimal("66666.67"), new BigDecimal("66666.66"));
            assertThat(sum(amountsById(configuration))).isEqualByComparingTo("300000.00");
        }

        @Test
        void regenerationBeforeBilling_spreadsTheContractOverAllPeriods() {
            LocalDate start = futureMonthStart();
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, start, 3);

            updateConfiguration(configuration, c -> c.setEffectiveTo(start.plusMonths(4).minusDays(1)));
            reapprove(configuration);

            assertThat(active(configuration)).extracting(BillingSchedule::getBillingAmount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsOnly(new BigDecimal("75000.00"));
            assertThat(sum(amountsById(configuration))).isEqualByComparingTo("300000.00");
        }

        @Test
        void contractValueChangeAfterBilling_isRejected_withoutPartialUpdates() {
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, futureMonthStart(), 3);
            markInvoiced(active(configuration).get(0));
            Map<UUID, BigDecimal> before = amountsById(configuration);

            updateFixedPrice(configuration, fp -> fp.setContractValue(new BigDecimal("360000.00")));
            assertThatThrownBy(() -> reapprove(configuration))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);

            assertThat(amountsById(configuration)).isEqualTo(before);
            assertThat(approvalStatus(configuration)).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        }
    }

    // ================================================================ G-05 one-time fixed price

    @Nested
    class G05_OneTimeFixedPrice {

        @Test
        void redatingAfterInvoicing_isRejected_andNoSecondObligationExists() {
            LocalDate start = futureMonthStart();
            BillingConfiguration configuration = fixedPrice("500000.00", oneTime, start, 2);
            assertThat(active(configuration)).hasSize(1);
            markInvoiced(active(configuration).get(0));

            updateConfiguration(configuration, c -> c.setEffectiveTo(start.plusMonths(3).minusDays(1)));
            assertThatThrownBy(() -> reapprove(configuration))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("One-Time");

            assertThat(allOccurrences(configuration)).hasSize(1);
        }

        @Test
        void redatingBeforeBilling_leavesExactlyOneActiveFullValueOccurrence() {
            LocalDate start = futureMonthStart();
            BillingConfiguration configuration = fixedPrice("500000.00", oneTime, start, 2);

            updateConfiguration(configuration, c -> c.setEffectiveTo(start.plusMonths(3).minusDays(1)));
            reapprove(configuration);

            assertThat(active(configuration)).singleElement().satisfies(occurrence -> {
                assertThat(occurrence.getBillingAmount()).isEqualByComparingTo("500000.00");
                assertThat(occurrence.getPeriodEndDate()).isEqualTo(start.plusMonths(3).minusDays(1));
            });
        }

        @Test
        void repeatedReconciliation_neverDuplicatesTheOccurrence() {
            BillingConfiguration configuration = fixedPrice("500000.00", oneTime, futureMonthStart(), 2);
            UUID id = active(configuration).get(0).getBillingScheduleId();

            reapprove(configuration);
            reapprove(configuration);

            assertThat(allOccurrences(configuration)).extracting(BillingSchedule::getBillingScheduleId)
                    .containsExactly(id);
        }
    }

    // ================================================================ G-06 manual occurrence tax / invoice

    @Nested
    class G06_ManualOccurrenceProcessing {

        /** Past-dated so approval promotes the due periods to TAX_PENDING, ready for manual tax. */
        private BillingConfiguration dueFixedPrice() {
            BillingConfiguration configuration = fixedPrice("300000.00", monthly,
                    today.minusMonths(4).withDayOfMonth(1), 3);
            assertThat(active(configuration)).allSatisfy(o ->
                    assertThat(o.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING));
            return configuration;
        }

        @Test
        void validOccurrence_isTaxedAndInvoicedManually() {
            BillingSchedule occurrence = active(dueFixedPrice()).get(0);

            taxCalculationService.calculateTaxForSchedule(occurrence.getBillingScheduleId());
            invoiceService.generateInvoiceForSchedule(occurrence.getBillingScheduleId());

            assertThat(scheduleRepository.findById(occurrence.getBillingScheduleId()).orElseThrow().getIsInvoiced())
                    .isTrue();
        }

        @Test
        void unapprovedConfiguration_isRejected() {
            BillingConfiguration configuration = dueFixedPrice();
            BillingSchedule occurrence = active(configuration).get(0);
            updateConfiguration(configuration, c -> c.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL));

            assertThatThrownBy(() -> taxCalculationService.calculateTaxForSchedule(occurrence.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("APPROVED");
            assertThat(taxCalculationRepository.existsByBillingScheduleId(occurrence.getBillingScheduleId())).isFalse();
        }

        @Test
        void scheduleAboveTheContractValue_isRejected() {
            BillingConfiguration configuration = dueFixedPrice();
            BillingSchedule occurrence = active(configuration).get(0);
            jdbc.update("UPDATE billing_schedule SET billing_amount = billing_amount + 1 WHERE billing_schedule_id = ?",
                    active(configuration).get(1).getBillingScheduleId());

            assertThatThrownBy(() -> taxCalculationService.calculateTaxForSchedule(occurrence.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("more than the Fixed Price contract value");
        }

        @Test
        void milestoneOccurrenceThatNoLongerMatchesItsPaymentEntry_isRejected() {
            BillingConfiguration configuration = milestonePlan(today.minusMonths(1), today.minusDays(3));
            BillingSchedule first = active(configuration).get(0);
            jdbc.update("UPDATE billing_schedule SET billing_amount = 150000 WHERE billing_schedule_id = ?",
                    first.getBillingScheduleId());

            assertThatThrownBy(() -> taxCalculationService.calculateTaxForSchedule(first.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("Payment 1");
        }

        @Test
        void invoice_whoseOccurrenceAmountNoLongerMatchesItsTax_isRejected() {
            BillingConfiguration configuration = dueFixedPrice();
            BillingSchedule occurrence = active(configuration).get(0);
            taxCalculationService.calculateTaxForSchedule(occurrence.getBillingScheduleId());
            jdbc.update("UPDATE billing_schedule SET billing_amount = billing_amount - 1 WHERE billing_schedule_id = ?",
                    occurrence.getBillingScheduleId());

            assertThatThrownBy(() -> invoiceService.generateInvoiceForSchedule(occurrence.getBillingScheduleId()))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
            assertThat(scheduleRepository.findById(occurrence.getBillingScheduleId()).orElseThrow().getIsInvoiced())
                    .isFalse();
        }
    }

    // ================================================================ G-07 recurring renewal

    @Nested
    class G07_RecurringRenewal {

        private BillingRecurringConfiguration original;

        @BeforeEach
        void originalTerm() {
            BillingConfiguration configuration = recurring("120000.00", futureMonthStart(), 4);
            original = recurringRepository.findByBillingConfigurationAndIsActiveTrue(configuration).orElseThrow();
        }

        private RenewalRequestDto request(LocalDate from, LocalDate to) {
            RenewalRequestDto request = new RenewalRequestDto();
            request.setRenewalOptionType(RenewalOptionType.SAME_AS_PREVIOUS);
            request.setEffectiveFrom(from);
            request.setEffectiveTo(to);
            return request;
        }

        @Test
        void validRenewal_createsOneRecurringConfigurationLinkedToTheOriginal() {
            LocalDate from = original.getRecurringEndDate().plusDays(1);

            recurringBillingService.renew(original.getRecurringConfigurationId(), request(from, from.plusMonths(4).minusDays(1)));

            assertThat(recurringRepository.findByRenewedFrom(original)).singleElement().satisfies(renewal -> {
                assertThat(renewal.getRecurringStartDate()).isEqualTo(from);
                assertThat(renewal.getContractValue()).isEqualByComparingTo(original.getContractValue());
            });
        }

        @Test
        void renewalOverlappingTheOriginalTerm_isRejected_andCreatesNothing() {
            LocalDate from = original.getRecurringEndDate().minusDays(10);

            assertThatThrownBy(() -> recurringBillingService.renew(original.getRecurringConfigurationId(),
                    request(from, from.plusMonths(4))))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("overlaps");
            assertThat(recurringRepository.findByRenewedFrom(original)).isEmpty();
        }

        @Test
        void repeatedRenewal_isRejected_andLeavesOneRenewal() {
            LocalDate from = original.getRecurringEndDate().plusDays(1);
            recurringBillingService.renew(original.getRecurringConfigurationId(), request(from, from.plusMonths(4).minusDays(1)));

            assertThatThrownBy(() -> recurringBillingService.renew(original.getRecurringConfigurationId(),
                    request(from, from.plusMonths(4).minusDays(1))))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
            assertThat(recurringRepository.findByRenewedFrom(original)).hasSize(1);
        }
    }

    // ================================================================ G-09 occurrence / run identity

    @Nested
    class G09_OccurrenceRunIdentity {

        @Test
        void reconciliation_keepsTheOccurrenceIdentity_soItsRunStaysLinked_andAFailedRunIsRequeued() {
            LocalDate start = futureMonthStart();
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, start, 3);
            BillingSchedule second = active(configuration).get(1);
            UUID runId = saveFailedRun(configuration, second.getBillingScheduleId(),
                    second.getPeriodStartDate(), second.getPeriodEndDate());

            updateConfiguration(configuration, c -> c.setEffectiveTo(start.plusMonths(4).minusDays(1)));
            reapprove(configuration);

            assertThat(active(configuration)).extracting(BillingSchedule::getBillingScheduleId)
                    .contains(second.getBillingScheduleId());
            AutomaticBillingRun run = runRepository.findById(runId).orElseThrow();
            assertThat(run.getBillingScheduleId()).isEqualTo(second.getBillingScheduleId());
            assertThat(run.getNextRetryAt()).as("re-queued after re-approval").isNotNull();
        }

        @Test
        void runCreation_whenTheRunOfThePeriodPointsAtARemovedOccurrence_relinksInsteadOfFailing() {
            BillingConfiguration configuration = fixedPrice("300000.00", monthly,
                    today.minusMonths(4).withDayOfMonth(1), 3);
            BillingSchedule occurrence = active(configuration).get(0);
            updateConfiguration(configuration, c -> c.setInvoiceGenerationType(InvoiceGenerationType.AUTOMATIC));
            UUID runId = saveFailedRun(configuration, UUID.randomUUID(),
                    occurrence.getPeriodStartDate(), occurrence.getPeriodEndDate());

            AutomaticBillingRun run = runService.findOrCreateForOccurrence(occurrence.getBillingScheduleId());

            assertThat(run.getAutomaticBillingRunId()).isEqualTo(runId);
            assertThat(runRepository.findById(runId).orElseThrow().getBillingScheduleId())
                    .isEqualTo(occurrence.getBillingScheduleId());
            assertThat(runRepository.count()).isEqualTo(1);
        }
    }

    // ================================================================ G-10 month-end periods

    @Nested
    class G10_MonthEndPeriods {

        @Test
        void monthEndStarts_endOnMonthEnds() {
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 1, 31), monthly))
                    .isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2028, 1, 31), monthly))
                    .isEqualTo(LocalDate.of(2028, 2, 29));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 3, 31), monthly))
                    .isEqualTo(LocalDate.of(2026, 4, 30));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 2, 28), monthly))
                    .isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 1, 31), quarterly))
                    .isEqualTo(LocalDate.of(2026, 4, 30));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2028, 2, 29), yearly))
                    .isEqualTo(LocalDate.of(2029, 2, 28));
        }

        /** The end of the period starting on {@code start} in a schedule anchored on {@code anchor}. */
        private LocalDate end(LocalDate anchor, LocalDate start, BillingFrequencyMaster frequency) {
            return occurrenceService.calculatePeriodEndDate(anchor, start, frequency);
        }

        @Test
        void fixedDayAnchors_clampInShortMonths_andRestoreTheOriginalDay() {
            // 30 Jan 2027 (non-leap): 30 Jan - 27 Feb, 28 Feb - 29 Mar, 30 Mar - 29 Apr.
            LocalDate jan30 = LocalDate.of(2027, 1, 30);
            assertThat(end(jan30, jan30, monthly)).isEqualTo(LocalDate.of(2027, 2, 27));
            assertThat(end(jan30, LocalDate.of(2027, 2, 28), monthly)).isEqualTo(LocalDate.of(2027, 3, 29));
            assertThat(end(jan30, LocalDate.of(2027, 3, 30), monthly)).isEqualTo(LocalDate.of(2027, 4, 29));
            // 30 Jan 2028 (leap): Feb starts on the 29th.
            LocalDate jan30Leap = LocalDate.of(2028, 1, 30);
            assertThat(end(jan30Leap, jan30Leap, monthly)).isEqualTo(LocalDate.of(2028, 2, 28));
            assertThat(end(jan30Leap, LocalDate.of(2028, 2, 29), monthly)).isEqualTo(LocalDate.of(2028, 3, 29));
            // 29 Jan 2027 (non-leap): 28 Feb, then back to the 29th.
            LocalDate jan29 = LocalDate.of(2027, 1, 29);
            assertThat(end(jan29, jan29, monthly)).isEqualTo(LocalDate.of(2027, 2, 27));
            assertThat(end(jan29, LocalDate.of(2027, 2, 28), monthly)).isEqualTo(LocalDate.of(2027, 3, 28));
            // 29 Jan 2028 (leap): 29 Feb exists.
            LocalDate jan29Leap = LocalDate.of(2028, 1, 29);
            assertThat(end(jan29Leap, jan29Leap, monthly)).isEqualTo(LocalDate.of(2028, 2, 28));
            assertThat(end(jan29Leap, LocalDate.of(2028, 2, 29), monthly)).isEqualTo(LocalDate.of(2028, 3, 28));
            // Quarterly and annual keep the anchor day too.
            assertThat(end(jan30, jan30, quarterly)).isEqualTo(LocalDate.of(2027, 4, 29));
            assertThat(end(jan30, LocalDate.of(2027, 4, 30), quarterly)).isEqualTo(LocalDate.of(2027, 7, 29));
            assertThat(end(jan30, jan30, yearly)).isEqualTo(LocalDate.of(2028, 1, 29));
        }

        @Test
        void februaryStarts_onlyAGenuineMonthEndIsAMonthEndAnchor() {
            // 28 Feb 2024 is not the last day of February in a leap year: a fixed-day anchor.
            LocalDate feb28Leap = LocalDate.of(2024, 2, 28);
            assertThat(end(feb28Leap, feb28Leap, monthly)).isEqualTo(LocalDate.of(2024, 3, 27));
            // 29 Feb 2024 and 28 Feb 2026 are month ends.
            assertThat(end(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 29), monthly))
                    .isEqualTo(LocalDate.of(2024, 3, 31));
            assertThat(end(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 28), monthly))
                    .isEqualTo(LocalDate.of(2026, 3, 31));
            // 30 Apr is a genuine month end.
            assertThat(end(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 30), monthly))
                    .isEqualTo(LocalDate.of(2026, 5, 31));
        }

        @Test
        void scheduleFromThe30th_keepsItsAnchor_isContiguous_andIsStableAcrossReapproval() {
            LocalDate anchor = LocalDate.of(today.getYear() + 1, 1, 30);
            BillingConfiguration configuration = fixedPrice("400000.00", monthly, anchor, anchor.plusMonths(4).minusDays(1));

            List<BillingSchedule> periods = active(configuration);
            assertThat(periods).extracting(BillingSchedule::getPeriodStartDate).containsExactly(
                    anchor, anchor.plusMonths(1), anchor.plusMonths(2), anchor.plusMonths(3));
            assertContiguous(periods);
            Map<UUID, BigDecimal> before = amountsById(configuration);

            reapprove(configuration);

            assertThat(amountsById(configuration)).isEqualTo(before);
        }

        @Test
        void regenerationAfterABilledPeriod_keepsTheOriginalAnchor() {
            LocalDate anchor = LocalDate.of(today.getYear() + 1, 1, 30);
            BillingConfiguration configuration = fixedPrice("300000.00", monthly, anchor, anchor.plusMonths(3).minusDays(1));
            markInvoiced(active(configuration).get(0));

            // Regeneration restarts after the billed period (in February); the anchor stays the 30th.
            updateConfiguration(configuration, c -> c.setEffectiveTo(anchor.plusMonths(5).minusDays(1)));
            reapprove(configuration);

            List<BillingSchedule> periods = active(configuration);
            assertThat(periods).extracting(BillingSchedule::getPeriodStartDate).containsExactly(
                    anchor, anchor.plusMonths(1), anchor.plusMonths(2), anchor.plusMonths(3), anchor.plusMonths(4));
            assertContiguous(periods);
            assertThat(sum(amountsById(configuration))).isEqualByComparingTo("300000.00");
        }

        private void assertContiguous(List<BillingSchedule> periods) {
            for (int i = 1; i < periods.size(); i++) {
                assertThat(periods.get(i).getPeriodStartDate()).as("period %d follows period %d", i + 1, i)
                        .isEqualTo(periods.get(i - 1).getPeriodEndDate().plusDays(1));
            }
            assertThat(periods).allSatisfy(p -> assertThat(p.getBillingDate()).isEqualTo(p.getPeriodEndDate()));
        }

        @Test
        void otherStarts_keepTheirConfiguredCycle() {
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 9, 1), monthly))
                    .isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 10, 15), monthly))
                    .isEqualTo(LocalDate.of(2026, 11, 14));
            assertThat(occurrenceService.calculatePeriodEndDate(LocalDate.of(2026, 10, 1), quarterly))
                    .isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        void scheduleFromAMonthEnd_isContiguous_endsOnMonthEnds_andIsStableAcrossReapproval() {
            LocalDate start = YearMonthHelper.lastDayOf(today.plusMonths(2));
            BillingConfiguration configuration = fixedPrice("500000.00", monthly, start,
                    YearMonthHelper.lastDayOf(start.plusMonths(5)));

            List<BillingSchedule> periods = active(configuration);
            assertThat(periods).hasSize(5);
            for (int i = 0; i < periods.size(); i++) {
                BillingSchedule period = periods.get(i);
                assertThat(period.getPeriodEndDate()).as("period %d ends on a month end", i + 1)
                        .isEqualTo(YearMonthHelper.lastDayOf(period.getPeriodEndDate()));
                if (i > 0) {
                    assertThat(period.getPeriodStartDate()).as("period %d starts the day after the previous", i + 1)
                            .isEqualTo(periods.get(i - 1).getPeriodEndDate().plusDays(1));
                }
            }
            Map<UUID, BigDecimal> before = amountsById(configuration);

            reapprove(configuration);

            assertThat(amountsById(configuration)).isEqualTo(before);
        }
    }

    // ================================================================ fixtures

    private static final class YearMonthHelper {
        static LocalDate lastDayOf(LocalDate date) {
            return date.withDayOfMonth(date.lengthOfMonth());
        }
    }

    private LocalDate futureMonthStart() {
        return today.plusMonths(2).withDayOfMonth(1);
    }

    private BillingConfiguration fixedPrice(String contractValue, BillingFrequencyMaster frequency, LocalDate start,
            int months) {
        return fixedPrice(contractValue, frequency, start, start.plusMonths(months).minusDays(1));
    }

    private BillingConfiguration fixedPrice(String contractValue, BillingFrequencyMaster frequency, LocalDate start,
            LocalDate end) {
        BillingConfiguration configuration = configuration("Fixed Price", frequency, start, end, BillingContext.PROJECT);
        tx.executeWithoutResult(status -> fixedPriceRepository.save(BillingFixedPriceConfiguration.builder()
                .billingConfiguration(configuration).contractValue(new BigDecimal(contractValue))
                .contractValueSource(ContractValueSource.MANUAL).isActive(true).build()));
        approve(configuration);
        return configuration;
    }

    private BillingConfiguration recurring(String contractValue, LocalDate start, int months) {
        LocalDate end = start.plusMonths(months).minusDays(1);
        BillingConfiguration configuration = configuration("Recurring", monthly, start, end,
                BillingContext.PRODUCT_SERVICE);
        tx.executeWithoutResult(status -> recurringRepository.save(BillingRecurringConfiguration.builder()
                .billingConfiguration(configuration).recurringName("Support retainer")
                .contractValue(new BigDecimal(contractValue)).contractValueSource(ContractValueSource.MANUAL)
                .billingFrequency(monthly).recurringStartDate(start).recurringEndDate(end).isActive(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()));
        approve(configuration);
        return configuration;
    }

    private BillingConfiguration milestonePlan(LocalDate first, LocalDate second) {
        BillingConfiguration configuration = configuration("Milestone Plan", oneTime, today.minusMonths(3),
                today.plusMonths(3), BillingContext.PROJECT);
        tx.executeWithoutResult(status -> {
            BillingMilestonePlan plan = milestonePlanRepository.save(BillingMilestonePlan.builder()
                    .billingConfiguration(configuration).totalContractValue(new BigDecimal("300000.00"))
                    .paymentStructure(PaymentStructure.INSTALLMENTS).isActive(true).build());
            paymentEntryRepository.save(BillingPaymentEntry.builder().milestonePlan(plan).sequence(1)
                    .percentage(new BigDecimal("40.00")).amount(new BigDecimal("120000.00")).billingDate(first)
                    .isActive(true).build());
            paymentEntryRepository.save(BillingPaymentEntry.builder().milestonePlan(plan).sequence(2)
                    .percentage(new BigDecimal("60.00")).amount(new BigDecimal("180000.00")).billingDate(second)
                    .isActive(true).build());
        });
        approve(configuration);
        return configuration;
    }

    private BillingConfiguration configuration(String billingTypeName, BillingFrequencyMaster frequency,
            LocalDate from, LocalDate to, BillingContext context) {
        return tx.execute(status -> {
            BillingTypeMaster type = billingTypeRepository.findAll().stream()
                    .filter(t -> t.getBillingTypeName().equals(billingTypeName)).findFirst()
                    .orElseGet(() -> billingTypeRepository.save(BillingTypeMaster.builder()
                            .billingTypeName(billingTypeName).isActive(true).build()));
            return configurationRepository.save(BillingConfiguration.builder()
                    .client(client).project(project).billingType(type).billingFrequency(frequency)
                    .currency(inr).taxRegion(karnataka).paymentTerm(net30)
                    .effectiveFrom(from).effectiveTo(to)
                    .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                    .billingStatus(com.AccountReceivableManagement.entity_enums.projectbilling_config
                            .BillingConfigurationStatus.INACTIVE)
                    .invoiceGenerationType(InvoiceGenerationType.MANUAL)
                    .billingContext(context)
                    .manuallyDeactivated(false)
                    .build());
        });
    }

    private void approve(BillingConfiguration configuration) {
        UUID id = configuration.getBillingConfigurationId();
        lockService.acquireLock(LockResourceType.BILLING_CONFIGURATION, id, LockActionType.APPROVAL);
        try {
            configurationService.approve(id);
        } finally {
            lockService.releaseLock(LockResourceType.BILLING_CONFIGURATION, id);
        }
    }

    /** As an edit of an approved configuration leaves it, then approval again. */
    private void reapprove(BillingConfiguration configuration) {
        updateConfiguration(configuration, c -> c.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL));
        approve(configuration);
    }

    private void updateConfiguration(BillingConfiguration configuration, Consumer<BillingConfiguration> change) {
        tx.executeWithoutResult(status -> {
            BillingConfiguration current = configurationRepository
                    .findById(configuration.getBillingConfigurationId()).orElseThrow();
            change.accept(current);
            configurationRepository.save(current);
        });
    }

    private void updateFixedPrice(BillingConfiguration configuration, Consumer<BillingFixedPriceConfiguration> change) {
        tx.executeWithoutResult(status -> {
            BillingFixedPriceConfiguration current = fixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(
                    configurationRepository.findById(configuration.getBillingConfigurationId()).orElseThrow())
                    .orElseThrow();
            change.accept(current);
            fixedPriceRepository.save(current);
        });
    }

    private void updateRecurring(BillingConfiguration configuration, Consumer<BillingRecurringConfiguration> change) {
        tx.executeWithoutResult(status -> {
            BillingRecurringConfiguration current = recurringRepository.findByBillingConfigurationAndIsActiveTrue(
                    configurationRepository.findById(configuration.getBillingConfigurationId()).orElseThrow())
                    .orElseThrow();
            change.accept(current);
            recurringRepository.save(current);
        });
    }

    private ApprovalStatus approvalStatus(BillingConfiguration configuration) {
        return configurationRepository.findById(configuration.getBillingConfigurationId()).orElseThrow()
                .getApprovalStatus();
    }

    private List<BillingSchedule> active(BillingConfiguration configuration) {
        return scheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
                configurationRepository.findById(configuration.getBillingConfigurationId()).orElseThrow());
    }

    private List<BillingSchedule> allOccurrences(BillingConfiguration configuration) {
        return scheduleRepository.findAll().stream()
                .filter(s -> s.getBillingConfiguration().getBillingConfigurationId()
                        .equals(configuration.getBillingConfigurationId()))
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()) || Boolean.TRUE.equals(s.getIsInvoiced()))
                .toList();
    }

    private Map<UUID, BigDecimal> amountsById(BillingConfiguration configuration) {
        Map<UUID, BigDecimal> amounts = new LinkedHashMap<>();
        active(configuration).forEach(s -> amounts.put(s.getBillingScheduleId(), s.getBillingAmount().setScale(2)));
        return amounts;
    }

    private static BigDecimal sum(Map<UUID, BigDecimal> amounts) {
        return amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void markInvoiced(BillingSchedule occurrence) {
        jdbc.update("UPDATE billing_schedule SET period_status = 'INVOICED', tax_status = 'TAX_CALCULATED', "
                + "is_invoiced = TRUE, invoice_date = ? WHERE billing_schedule_id = ?", today,
                occurrence.getBillingScheduleId());
    }

    private void markTaxPending(BillingSchedule occurrence) {
        jdbc.update("UPDATE billing_schedule SET period_status = 'TAX_PENDING', tax_status = 'TAX_PENDING' "
                + "WHERE billing_schedule_id = ?", occurrence.getBillingScheduleId());
    }

    private UUID saveFailedRun(BillingConfiguration configuration, UUID occurrenceId, LocalDate start, LocalDate end) {
        return tx.execute(status -> runRepository.save(AutomaticBillingRun.builder()
                .billingConfiguration(configurationRepository.getReferenceById(configuration.getBillingConfigurationId()))
                .billingScheduleId(occurrenceId)
                .billingPeriodStart(start)
                .billingPeriodEnd(end)
                .status(AutomaticBillingRunStatus.FAILED)
                .failureStep(AutomaticBillingStep.SOURCE_DATA_ACQUISITION)
                .failureReason("test failure")
                .attemptCount(1)
                .build()).getAutomaticBillingRunId());
    }

    private void seedMasterData() {
        inr = currencyRepository.save(CurrencyMaster.builder().currencyCode("INR").currencyName("Indian Rupee")
                .currencySymbol("₹").isActive(true).build());
        karnataka = taxRegionRepository.save(TaxRegionMaster.builder()
                .taxRegionCode("KA").taxRegionName("Karnataka").currencyCode("INR").isActive(true).build());
        TaxConfiguration gst = TaxConfiguration.builder().taxRegion(karnataka).effectiveFrom(LocalDate.of(2020, 1, 1))
                .isActive(true).components(new ArrayList<>()).build();
        for (String[] component : new String[][] {{"CGST", "9.00", "SAME_JURISDICTION"},
                {"SGST", "9.00", "SAME_JURISDICTION"}, {"IGST", "18.00", "DIFFERENT_JURISDICTION"}}) {
            TaxTypeMaster type = taxTypeRepository.save(TaxTypeMaster.builder()
                    .taxTypeCode(component[0]).taxTypeName(component[0]).isActive(true).build());
            gst.getComponents().add(TaxConfigurationComponent.builder().taxConfiguration(gst).taxType(type)
                    .taxRate(new BigDecimal(component[1]))
                    .applicabilityType(TaxApplicabilityType.valueOf(component[2])).isActive(true).build());
        }
        taxConfigurationRepository.save(gst);
        net30 = paymentTermsRepository.save(PaymentTermsMaster.builder()
                .paymentTermName("Net 30").paymentDays(30).isActive(true).build());
        monthly = frequencyRepository.save(BillingFrequencyMaster.builder().billingFrequencyName("Monthly")
                .durationUnit(RenewalDurationUnit.MONTHS).durationValue(1).isActive(true).build());
        quarterly = frequencyRepository.save(BillingFrequencyMaster.builder().billingFrequencyName("Quarterly")
                .durationUnit(RenewalDurationUnit.MONTHS).durationValue(3).isActive(true).build());
        yearly = frequencyRepository.save(BillingFrequencyMaster.builder().billingFrequencyName("Yearly")
                .durationUnit(RenewalDurationUnit.YEARS).durationValue(1).isActive(true).build());
        oneTime = frequencyRepository.save(BillingFrequencyMaster.builder().billingFrequencyName("One-Time")
                .durationUnit(RenewalDurationUnit.MONTHS).durationValue(1).isActive(true).build());
        companyProfileRepository.save(CompanyProfile.builder().legalName("Paves Technologies Pvt Ltd")
                .state("Karnataka").country("India").gstin("29ABCDE1234F1Z5").email("billing@paves.example")
                .isActive(true).build());
        client = clientRepository.save(Client.builder().clientId(UUID.randomUUID()).clientName("Acme Retail")
                .email("ap@acme.example").build());
        project = projectRepository.save(ProjectMasterReference.builder().pmsProjectId(4242L)
                .clientId(client.getClientId()).projectName("Store Modernisation").primaryLocation("Karnataka")
                .build());
    }

    /** Every table emptied, so each test sees only its own records. In-memory test database only. */
    private void emptyDatabase() {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = CURRENT_SCHEMA AND table_type = 'BASE TABLE'", String.class);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            tables.forEach(table -> jdbc.execute("TRUNCATE TABLE \"" + table + "\""));
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
