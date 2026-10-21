package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingMilestonePlanRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 2 (automation readiness): Billing Occurrences as the source of truth
 * for billing periods - generation per billing type, idempotent creation,
 * due detection, the T&amp;M period resolution and the scheduler transition.
 */
@ExtendWith(MockitoExtension.class)
class BillingOccurrenceScheduleReadinessTest {

    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private BillingFixedPriceRepository billingFixedPriceRepository;
    @Mock private BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;
    @Mock private BillingMilestonePlanRepository billingMilestonePlanRepository;
    @Mock private BillingPaymentEntryRepository billingPaymentEntryRepository;
    @Mock private BillingPeriodCalculatorServiceImpl billingPeriodCalculatorService;
    @Mock private BillingConfigurationRepository billingConfigurationRepository;
    @Mock private BillingFrequencyMasterRepository billingFrequencyRepository;
    @Mock private TaxCalculationRepository taxCalculationRepository;
    @Mock private TaxCalculationService taxCalculationService;
    @Mock private BillingOccurrenceTransactionService billingOccurrenceTransactionService;

    @InjectMocks
    private BillingOccurrenceServiceImpl billingOccurrenceService;

    private UUID configurationId;
    private BillingConfiguration configuration;

    private static final BillingFrequencyMaster WEEKLY = frequency("Weekly", 7, RenewalDurationUnit.DAYS);
    private static final BillingFrequencyMaster MONTHLY = frequency("Monthly", 1, RenewalDurationUnit.MONTHS);
    private static final BillingFrequencyMaster QUARTERLY = frequency("Quarterly", 3, RenewalDurationUnit.MONTHS);
    private static final BillingFrequencyMaster ONE_TIME = frequency("One-Time", 1, RenewalDurationUnit.DAYS);

    private static BillingFrequencyMaster frequency(String name, int value, RenewalDurationUnit unit) {
        return BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName(name)
                .durationValue(value)
                .durationUnit(unit)
                .build();
    }

    @BeforeEach
    void setUp() {
        configurationId = UUID.randomUUID();
        configuration = BillingConfiguration.builder()
                .billingConfigurationId(configurationId)
                .approvalStatus(ApprovalStatus.APPROVED)
                .billingStatus(BillingConfigurationStatus.ACTIVE)
                .project(ProjectMasterReference.builder().pmsProjectId(23L).build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<BillingSchedule> capturedSaveAll() {
        ArgumentCaptor<List<BillingSchedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(billingScheduleRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private BillingSchedule existingOccurrence(LocalDate start, LocalDate end, BillingPeriodStatus status,
            boolean active) {
        return BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingConfiguration(configuration)
                .periodNumber(99)
                .periodStartDate(start)
                .periodEndDate(end)
                .billingDate(end)
                .billingAmount(new BigDecimal("1.00"))
                .scheduleType(BillingScheduleType.PRIMARY)
                .periodStatus(status)
                .taxStatus(status == BillingPeriodStatus.SCHEDULED ? BillingPeriodStatus.PENDING : status)
                .isInvoiced(status == BillingPeriodStatus.INVOICED)
                .isActive(active)
                .build();
    }

    // ================================================================ Fixed Price

    @Nested
    class FixedPrice {

        private void stubFixedPrice(BillingFrequencyMaster frequency, LocalDate from, LocalDate to, String value) {
            configuration.setBillingFrequency(frequency);
            when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(configuration));
            when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                    .thenReturn(Optional.of(BillingFixedPriceConfiguration.builder()
                            .billingConfiguration(configuration)
                            .contractValue(new BigDecimal(value))
                            .effectiveFrom(from)
                            .effectiveTo(to)
                            .build()));
        }

        @Test
        void oneTime_createsOneOccurrenceForTheWholeContractValue() {
            stubFixedPrice(ONE_TIME, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "1000000.00");

            billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId);

            assertThat(capturedSaveAll()).singleElement().satisfies(occurrence -> {
                assertThat(occurrence.getBillingConfiguration()).isSameAs(configuration);
                assertThat(occurrence.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
                assertThat(occurrence.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
                assertThat(occurrence.getBillingDate()).isEqualTo(LocalDate.of(2026, 12, 31));
                assertThat(occurrence.getBillingAmount()).isEqualByComparingTo("1000000.00");
                assertThat(occurrence.getPeriodStatus()).isEqualTo(BillingPeriodStatus.SCHEDULED);
                assertThat(occurrence.getTaxStatus()).isEqualTo(BillingPeriodStatus.PENDING);
            });
            verify(billingScheduleRepository).flush();
        }

        @Test
        void monthly_createsOneOccurrencePerCalendarMonthWithEqualShares() {
            stubFixedPrice(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "300000.00");

            billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId);

            assertThat(capturedSaveAll())
                    .extracting(BillingSchedule::getPeriodNumber, BillingSchedule::getPeriodStartDate,
                            BillingSchedule::getPeriodEndDate, BillingSchedule::getBillingDate)
                    .containsExactly(
                            tuple(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31)),
                            tuple(2, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 30)),
                            tuple(3, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)));
            assertThat(capturedSaveAll()).allSatisfy(occurrence ->
                    assertThat(occurrence.getBillingAmount()).isEqualByComparingTo("100000.00"));
        }

        @Test
        void quarterly_lastPeriodIsClippedToTheEffectiveTo() {
            stubFixedPrice(QUARTERLY, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 15), "200000.00");

            billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId);

            assertThat(capturedSaveAll())
                    .extracting(BillingSchedule::getPeriodStartDate, BillingSchedule::getPeriodEndDate,
                            BillingSchedule::getIsPartialPeriod)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), false),
                            tuple(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 15), true));
        }

        /**
         * Re-approval after the October occurrence became due (TAX_PENDING) and
         * the configuration was deactivated (November soft-deleted): October is
         * kept exactly as it is, November is reactivated instead of colliding
         * with uk_billing_schedule_period, December is inserted - and numbering
         * stays aligned with the period's position.
         */
        @Test
        void regeneration_keepsDueOccurrence_reactivatesInactiveOne_insertsOnlyTheMissingOne() {
            stubFixedPrice(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "300000.00");
            BillingSchedule octoberDue = existingOccurrence(
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BillingPeriodStatus.TAX_PENDING, true);
            BillingSchedule novemberInactive = existingOccurrence(
                    LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), BillingPeriodStatus.TAX_PENDING, false);
            when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration))
                    .thenReturn(List.of(octoberDue));
            when(billingScheduleRepository.findByBillingConfigurationAndPeriodStartDateAndPeriodEndDate(
                    configuration, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                    .thenReturn(Optional.of(octoberDue));
            when(billingScheduleRepository.findByBillingConfigurationAndPeriodStartDateAndPeriodEndDate(
                    configuration, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                    .thenReturn(Optional.of(novemberInactive));

            billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId);

            List<BillingSchedule> saved = capturedSaveAll();
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0)).isSameAs(novemberInactive);
            assertThat(novemberInactive.getIsActive()).isTrue();
            assertThat(novemberInactive.getPeriodStatus()).isEqualTo(BillingPeriodStatus.SCHEDULED);
            assertThat(novemberInactive.getTaxStatus()).isEqualTo(BillingPeriodStatus.PENDING);
            assertThat(novemberInactive.getPeriodNumber()).isEqualTo(2);
            assertThat(novemberInactive.getBillingAmount()).isEqualByComparingTo("100000.00");
            assertThat(saved.get(1).getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(saved.get(1).getPeriodNumber()).isEqualTo(3);
            // The due October occurrence is untouched.
            assertThat(octoberDue.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING);
            assertThat(octoberDue.getBillingAmount()).isEqualByComparingTo("1.00");
        }

        @Test
        void regeneration_whenEveryPeriodAlreadyHasAnOccurrence_insertsNothing() {
            stubFixedPrice(ONE_TIME, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "1000000.00");
            when(billingScheduleRepository.findByBillingConfigurationAndPeriodStartDateAndPeriodEndDate(
                    configuration, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)))
                    .thenReturn(Optional.of(existingOccurrence(LocalDate.of(2026, 10, 1),
                            LocalDate.of(2026, 12, 31), BillingPeriodStatus.SCHEDULED, true)));

            billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId);

            verify(billingScheduleRepository, never()).saveAll(any());
            verify(billingScheduleRepository, never()).save(any());
        }

        @Test
        void inactiveInvoicedOccurrence_isNeverReactivated() {
            stubFixedPrice(ONE_TIME, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "1000000.00");
            BillingSchedule invoicedInactive = existingOccurrence(LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 12, 31), BillingPeriodStatus.INVOICED, false);
            when(billingScheduleRepository.findByBillingConfigurationAndPeriodStartDateAndPeriodEndDate(
                    configuration, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)))
                    .thenReturn(Optional.of(invoicedInactive));

            billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId);

            verify(billingScheduleRepository, never()).saveAll(any());
            assertThat(invoicedInactive.getIsActive()).isFalse();
        }

        /**
         * A concurrent writer that committed the same period between the
         * lookup and the insert: the unique-key violation is raised by
         * generation itself (flush) and propagates, so the surrounding
         * approval rolls back instead of committing a partial set.
         */
        @Test
        void concurrentDuplicateInsert_propagatesFromGeneration() {
            stubFixedPrice(ONE_TIME, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "1000000.00");
            doThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_billing_schedule_period'"))
                    .when(billingScheduleRepository).flush();

            assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForFixedPrice(configurationId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ================================================================ Recurring

    @Nested
    class Recurring {

        private BillingRecurringConfiguration recurring;

        private void stubRecurring(BillingFrequencyMaster frequency, LocalDate from, LocalDate to, String value) {
            recurring = BillingRecurringConfiguration.builder()
                    .recurringConfigurationId(UUID.randomUUID())
                    .billingConfiguration(configuration)
                    .recurringName("Monthly Retainer")
                    .contractValue(new BigDecimal(value))
                    .billingFrequency(frequency)
                    .recurringStartDate(from)
                    .recurringEndDate(to)
                    .isActive(true)
                    .build();
            when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(configuration));
            when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                    .thenReturn(Optional.of(recurring));
        }

        @Test
        void monthlyRetainer_octoberNovemberDecember_areIndependentOccurrences() {
            stubRecurring(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "600000.00");

            billingOccurrenceService.generateOccurrencesForRecurring(configurationId);

            List<BillingSchedule> saved = capturedSaveAll();
            assertThat(saved)
                    .extracting(BillingSchedule::getPeriodStartDate, BillingSchedule::getPeriodEndDate)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
                            tuple(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                            tuple(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
            assertThat(saved).allSatisfy(occurrence -> {
                assertThat(occurrence.getRecurringConfiguration()).isSameAs(recurring);
                assertThat(occurrence.getBillingConfiguration()).isSameAs(configuration);
                assertThat(occurrence.getBillingDate()).isEqualTo(occurrence.getPeriodEndDate());
            });
            // The contract value is distributed by days, totalling exactly the contract value.
            assertThat(saved.stream().map(BillingSchedule::getBillingAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("600000.00");
            assertThat(saved.get(0).getBillingAmount()).isEqualByComparingTo("202173.91");
        }

        @Test
        void weeklyRecurring_producesSevenDayOccurrences() {
            stubRecurring(WEEKLY, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 25), "30000.00");

            billingOccurrenceService.generateOccurrencesForRecurring(configurationId);

            assertThat(capturedSaveAll())
                    .extracting(BillingSchedule::getPeriodStartDate, BillingSchedule::getPeriodEndDate)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)),
                            tuple(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)),
                            tuple(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));
        }

        @Test
        void regeneration_keepsAlreadyInvoicedOctober_andGeneratesFromNovember() {
            stubRecurring(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "600000.00");
            BillingSchedule octoberInvoiced = existingOccurrence(
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), BillingPeriodStatus.INVOICED, true);
            octoberInvoiced.setPeriodNumber(1);
            when(billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurring))
                    .thenReturn(List.of(octoberInvoiced));

            billingOccurrenceService.generateOccurrencesForRecurring(configurationId);

            assertThat(capturedSaveAll())
                    .extracting(BillingSchedule::getPeriodNumber, BillingSchedule::getPeriodStartDate)
                    .containsExactly(
                            tuple(2, LocalDate.of(2026, 11, 1)),
                            tuple(3, LocalDate.of(2026, 12, 1)));
        }
    }

    // ================================================================ Milestone / Installments

    @Nested
    class MilestonesAndInstallments {

        private BillingMilestonePlan plan(PaymentStructure structure) {
            return BillingMilestonePlan.builder()
                    .milestonePlanId(UUID.randomUUID())
                    .billingConfiguration(configuration)
                    .paymentStructure(structure)
                    .isActive(true)
                    .build();
        }

        private BillingPaymentEntry entry(int sequence, LocalDate billingDate, String amount) {
            return BillingPaymentEntry.builder()
                    .paymentEntryId(UUID.randomUUID())
                    .sequence(sequence)
                    .billingDate(billingDate)
                    .amount(new BigDecimal(amount))
                    .isActive(true)
                    .build();
        }

        @Test
        void oneTimeMilestone_createsOneSingleDayOccurrence() {
            billingOccurrenceService.generateOccurrencesForMilestonePlan(configuration,
                    plan(PaymentStructure.FULL_PAYMENT),
                    List.of(entry(1, LocalDate.of(2026, 10, 20), "500000.00")));

            assertThat(capturedSaveAll()).singleElement().satisfies(occurrence -> {
                assertThat(occurrence.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 10, 20));
                assertThat(occurrence.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 10, 20));
                assertThat(occurrence.getBillingDate()).isEqualTo(LocalDate.of(2026, 10, 20));
                assertThat(occurrence.getBillingAmount()).isEqualByComparingTo("500000.00");
                assertThat(occurrence.getPeriodNumber()).isEqualTo(1);
            });
        }

        @Test
        void installments_eachInstallmentIsItsOwnOccurrence() {
            billingOccurrenceService.generateOccurrencesForMilestonePlan(configuration,
                    plan(PaymentStructure.INSTALLMENTS),
                    List.of(entry(1, LocalDate.of(2026, 10, 15), "400000.00"),
                            entry(2, LocalDate.of(2026, 11, 15), "400000.00"),
                            entry(3, LocalDate.of(2026, 12, 15), "200000.00")));

            assertThat(capturedSaveAll())
                    .extracting(BillingSchedule::getPeriodNumber, BillingSchedule::getBillingDate,
                            BillingSchedule::getBillingAmount, BillingSchedule::getRemarks)
                    .containsExactly(
                            tuple(1, LocalDate.of(2026, 10, 15), new BigDecimal("400000.00"), "Payment 1"),
                            tuple(2, LocalDate.of(2026, 11, 15), new BigDecimal("400000.00"), "Payment 2"),
                            tuple(3, LocalDate.of(2026, 12, 15), new BigDecimal("200000.00"), "Payment 3"));
        }

        @Test
        void installments_regenerated_createNoDuplicateOccurrence() {
            BillingPaymentEntry second = entry(2, LocalDate.of(2026, 11, 15), "400000.00");
            when(billingScheduleRepository.findByBillingConfigurationAndPeriodStartDateAndPeriodEndDate(
                    configuration, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)))
                    .thenReturn(Optional.of(existingOccurrence(LocalDate.of(2026, 11, 15),
                            LocalDate.of(2026, 11, 15), BillingPeriodStatus.TAX_PENDING, true)));

            billingOccurrenceService.generateOccurrencesForMilestonePlan(configuration,
                    plan(PaymentStructure.INSTALLMENTS), List.of(second));

            verify(billingScheduleRepository, never()).saveAll(any());
        }

        @Test
        void multipleMilestones_onDifferentDates_produceIndependentOccurrences() {
            billingOccurrenceService.generateOccurrencesForMilestonePlan(configuration,
                    plan(PaymentStructure.INSTALLMENTS),
                    List.of(entry(1, LocalDate.of(2026, 10, 5), "300000.00"),
                            entry(2, LocalDate.of(2026, 11, 20), "700000.00")));

            assertThat(capturedSaveAll())
                    .extracting(BillingSchedule::getBillingDate)
                    .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 20));
        }

        /** Existing rule, kept: the occurrence key is (configuration, date), so one date = one occurrence. */
        @Test
        void twoMilestonesOnTheSameDate_areRejectedNotMerged() {
            assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForMilestonePlan(configuration,
                    plan(PaymentStructure.INSTALLMENTS),
                    List.of(entry(1, LocalDate.of(2026, 10, 5), "300000.00"),
                            entry(2, LocalDate.of(2026, 10, 5), "700000.00"))))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("Each billing date can have only one billing occurrence");
            verify(billingScheduleRepository, never()).saveAll(any());
        }
    }

    // ================================================================ T&M billing periods

    @Nested
    class TimeAndMaterialPeriods {

        private void stubTimeAndMaterial(BillingFrequencyMaster frequency, LocalDate from, LocalDate to) {
            configuration.setBillingType(BillingTypeMaster.builder().billingTypeName("Timesheet Based").build());
            configuration.setBillingFrequency(frequency);
            configuration.setEffectiveFrom(from);
            configuration.setEffectiveTo(to);
            when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(configuration));
        }

        @Test
        void monthlyFromTheFirst_givesCalendarMonthPeriodsBilledOnTheirLastDay() {
            stubTimeAndMaterial(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

            List<BillingPeriodDto> periods =
                    billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 10, 1));

            assertThat(periods)
                    .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate,
                            BillingPeriodDto::getBillingDate)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31)),
                            tuple(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 30)),
                            tuple(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)));
        }

        @Test
        void monthlyFromMidMonth_keepsTheConfiguredCycleInsteadOfMonthEnd() {
            stubTimeAndMaterial(MONTHLY, LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 14));

            List<BillingPeriodDto> periods =
                    billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 10, 15));

            assertThat(periods)
                    .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate,
                            BillingPeriodDto::getBillingDate)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14)),
                            tuple(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 14)),
                            tuple(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 14), LocalDate.of(2027, 1, 14)));
        }

        @Test
        void weekly_givesSevenDayPeriods() {
            stubTimeAndMaterial(WEEKLY, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18));

            List<BillingPeriodDto> periods =
                    billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 10, 5));

            assertThat(periods)
                    .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)),
                            tuple(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)));
        }

        /** 01-Oct..31-Oct configuration, resolved after it expired: the final period is still there. */
        @Test
        void finalPeriod_isStillResolvedAfterTheConfigurationExpired() {
            stubTimeAndMaterial(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            configuration.setBillingStatus(BillingConfigurationStatus.EXPIRED);

            List<BillingPeriodDto> periods =
                    billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 11, 1));

            assertThat(periods).singleElement().satisfies(period -> {
                assertThat(period.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
                assertThat(period.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
                assertThat(period.getIsPartialPeriod()).isFalse();
            });
        }

        @Test
        void finalPeriod_isClippedToTheProjectEndWhenThatComesFirst() {
            stubTimeAndMaterial(MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));
            configuration.setProject(ProjectMasterReference.builder()
                    .pmsProjectId(23L).endDate(LocalDate.of(2026, 11, 20)).build());

            List<BillingPeriodDto> periods =
                    billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 12, 1));

            assertThat(periods).last().satisfies(period -> {
                assertThat(period.getPeriodStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                assertThat(period.getPeriodEndDate()).isEqualTo(LocalDate.of(2026, 11, 20));
                assertThat(period.getIsPartialPeriod()).isTrue();
            });
        }

        @Test
        void openEnded_resolvesUpToThePeriodContainingTheProcessingDate() {
            stubTimeAndMaterial(MONTHLY, LocalDate.of(2026, 9, 15), null);

            List<BillingPeriodDto> periods =
                    billingOccurrenceService.resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 11, 2));

            assertThat(periods)
                    .extracting(BillingPeriodDto::getPeriodStartDate)
                    .containsExactly(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 15));
        }

        @Test
        void notTimeAndMaterial_isRejected() {
            configuration.setBillingType(BillingTypeMaster.builder().billingTypeName("Fixed Price").build());
            when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(configuration));

            assertThatThrownBy(() -> billingOccurrenceService
                    .resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 10, 1)))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }

        @Test
        void notApproved_isRejected() {
            configuration.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(configuration));

            assertThatThrownBy(() -> billingOccurrenceService
                    .resolveTimeAndMaterialBillingPeriods(configurationId, LocalDate.of(2026, 10, 1)))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        }
    }

    // ================================================================ Due detection

    @Nested
    class DueForBilling {

        private BillingSchedule octoberOccurrence() {
            return existingOccurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                    BillingPeriodStatus.SCHEDULED, true);
        }

        @Test
        void isDueOnItsBillingDate() {
            assertThat(octoberOccurrence().isDueForBilling(LocalDate.of(2026, 10, 31))).isTrue();
        }

        /** Beginning-of-month processing: the October period, processed on 01-Nov, is due. */
        @Test
        void isDueWhenProcessedAfterItsBillingDate() {
            assertThat(octoberOccurrence().isDueForBilling(LocalDate.of(2026, 11, 1))).isTrue();
        }

        /** Missed while nothing ran: still due weeks later. */
        @Test
        void overdueOccurrence_isStillDue() {
            assertThat(octoberOccurrence().isDueForBilling(LocalDate.of(2026, 12, 15))).isTrue();
        }

        @Test
        void isNotDueBeforeItsBillingDate() {
            assertThat(octoberOccurrence().isDueForBilling(LocalDate.of(2026, 10, 30))).isFalse();
        }

        @Test
        void taxPendingAndTaxCalculated_areStillDue() {
            BillingSchedule occurrence = octoberOccurrence();
            occurrence.setPeriodStatus(BillingPeriodStatus.TAX_PENDING);
            assertThat(occurrence.isDueForBilling(LocalDate.of(2026, 11, 1))).isTrue();
            occurrence.setPeriodStatus(BillingPeriodStatus.TAX_CALCULATED);
            assertThat(occurrence.isDueForBilling(LocalDate.of(2026, 11, 1))).isTrue();
        }

        @Test
        void invoicedInactiveOrCancelled_areNotDue() {
            BillingSchedule invoiced = octoberOccurrence();
            invoiced.setIsInvoiced(true);
            invoiced.setPeriodStatus(BillingPeriodStatus.INVOICED);
            BillingSchedule inactive = octoberOccurrence();
            inactive.setIsActive(false);
            BillingSchedule cancelled = octoberOccurrence();
            cancelled.setPeriodStatus(BillingPeriodStatus.CANCELLED);

            LocalDate processingDate = LocalDate.of(2026, 11, 1);
            assertThat(invoiced.isDueForBilling(processingDate)).isFalse();
            assertThat(inactive.isDueForBilling(processingDate)).isFalse();
            assertThat(cancelled.isDueForBilling(processingDate)).isFalse();
        }
    }

    // ================================================================ Scheduler selection

    @Nested
    class SchedulerSelection {

        @Test
        void selectsOnlyDueScheduledOccurrences_andOneFailureDoesNotStopTheRest() {
            BillingSchedule first = existingOccurrence(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                    BillingPeriodStatus.SCHEDULED, true);
            BillingSchedule second = existingOccurrence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1),
                    BillingPeriodStatus.SCHEDULED, true);
            when(billingScheduleRepository.findByBillingDateLessThanEqualAndPeriodStatusAndTaxStatusAndIsActiveTrue(
                    any(LocalDate.class), any(), any()))
                    .thenReturn(new ArrayList<>(List.of(first, second)));
            doThrow(new RuntimeException("lock wait timeout"))
                    .when(billingOccurrenceTransactionService).transitionSingleOccurrenceToTaxPending(first);

            billingOccurrenceService.transitionScheduledToTaxPending();

            verify(billingScheduleRepository).findByBillingDateLessThanEqualAndPeriodStatusAndTaxStatusAndIsActiveTrue(
                    LocalDate.now(), BillingPeriodStatus.SCHEDULED, BillingPeriodStatus.PENDING);
            verify(billingOccurrenceTransactionService).transitionSingleOccurrenceToTaxPending(second);
            verify(billingOccurrenceTransactionService, times(2)).transitionSingleOccurrenceToTaxPending(any());
        }
    }
}
