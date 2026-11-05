package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingMilestonePlan;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingPaymentEntry;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PaymentStructure;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingMilestonePlanRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingPaymentEntryRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link BillingOccurrenceServiceImpl#requireMilestonePlanOccurrenceBillable(UUID)}:
 * the check automatic billing applies to a Milestone Plan occurrence - a
 * milestone payment (FULL_PAYMENT) or an installment (INSTALLMENTS) - before
 * it is taxed. Each occurrence bills its own payment entry's amount; the
 * check only confirms the occurrence still is that entry.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MilestonePlanOccurrenceBillabilityTest {

    @Mock
    private com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository automaticBillingRunRepository;

    @Mock private BillingScheduleRepository scheduleRepository;
    @Mock private BillingMilestonePlanRepository milestonePlanRepository;
    @Mock private BillingPaymentEntryRepository paymentEntryRepository;
    @InjectMocks private BillingOccurrenceServiceImpl service;

    private BillingConfiguration configuration;
    private BillingMilestonePlan plan;
    private final List<BillingPaymentEntry> entries = new ArrayList<>();
    private final List<BillingSchedule> activeOccurrences = new ArrayList<>();

    @BeforeEach
    void setUp() {
        configuration = BillingConfiguration.builder()
                .billingConfigurationId(UUID.randomUUID())
                .currency(CurrencyMaster.builder().currencyId(UUID.randomUUID()).currencyCode("INR").build())
                .build();
        when(scheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration))
                .thenReturn(activeOccurrences);
    }

    private void planIs(PaymentStructure structure, String totalContractValue) {
        plan = BillingMilestonePlan.builder()
                .milestonePlanId(UUID.randomUUID())
                .billingConfiguration(configuration)
                .totalContractValue(totalContractValue != null ? new BigDecimal(totalContractValue) : null)
                .paymentStructure(structure)
                .isActive(true)
                .build();
        when(milestonePlanRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                .thenReturn(Optional.of(plan));
        when(paymentEntryRepository.findByMilestonePlanAndIsActiveTrueOrderBySequenceAsc(plan)).thenReturn(entries);
    }

    private void entry(int sequence, String amount, LocalDate billingDate) {
        entries.add(BillingPaymentEntry.builder()
                .paymentEntryId(UUID.randomUUID())
                .milestonePlan(plan)
                .sequence(sequence)
                .amount(new BigDecimal(amount))
                .billingDate(billingDate)
                .isActive(true)
                .build());
    }

    /** An occurrence as generateOccurrencesForMilestonePlan creates it: one day, sequence as period number. */
    private BillingSchedule occurrence(int sequence, String amount, LocalDate billingDate, BillingPeriodStatus status) {
        BillingSchedule occurrence = BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingConfiguration(configuration)
                .periodNumber(sequence)
                .periodStartDate(billingDate)
                .periodEndDate(billingDate)
                .billingDate(billingDate)
                .billingAmount(amount != null ? new BigDecimal(amount) : null)
                .periodStatus(status)
                .isActive(true)
                .build();
        activeOccurrences.add(occurrence);
        when(scheduleRepository.findById(occurrence.getBillingScheduleId())).thenReturn(Optional.of(occurrence));
        return occurrence;
    }

    private void check(BillingSchedule occurrence) {
        service.requireMilestonePlanOccurrenceBillable(occurrence.getBillingScheduleId());
    }

    private static LocalDate oct(int day) {
        return LocalDate.of(2026, 10, day);
    }

    // ================================================================ milestone and installment payments

    @Test
    void fullPaymentMilestone_isBillableForItsAmount() {
        planIs(PaymentStructure.FULL_PAYMENT, "800000.00");
        entry(1, "800000.00", oct(15));
        BillingSchedule milestone = occurrence(1, "800000.00", oct(15), BillingPeriodStatus.SCHEDULED);

        assertThatCode(() -> check(milestone)).doesNotThrowAnyException();
    }

    @Test
    void installments_eachIsBillableForOnlyItsOwnAmount() {
        planIs(PaymentStructure.INSTALLMENTS, "1000000.00");
        entry(1, "200000.00", oct(1));
        entry(2, "300000.00", oct(15));
        entry(3, "500000.00", oct(31));
        BillingSchedule first = occurrence(1, "200000.00", oct(1), BillingPeriodStatus.INVOICED);
        BillingSchedule second = occurrence(2, "300000.00", oct(15), BillingPeriodStatus.TAX_PENDING);
        BillingSchedule third = occurrence(3, "500000.00", oct(31), BillingPeriodStatus.SCHEDULED);

        assertThatCode(() -> check(first)).doesNotThrowAnyException();
        assertThatCode(() -> check(second)).doesNotThrowAnyException();
        assertThatCode(() -> check(third)).doesNotThrowAnyException();
    }

    // ================================================================ occurrence no longer matches its entry

    @Test
    void installmentWhoseAmountChangedAfterItLeftScheduled_isNotBilledAtTheStaleAmount() {
        planIs(PaymentStructure.INSTALLMENTS, "1000000.00");
        entry(1, "500000.00", oct(1));
        entry(2, "500000.00", oct(15));
        BillingSchedule stale = occurrence(2, "400000.00", oct(15), BillingPeriodStatus.TAX_PENDING);

        assertThatThrownBy(() -> check(stale))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Payment 2 is now 500000.00 but its Billing Occurrence is 400000.00");
    }

    @Test
    void installmentWhoseDateChanged_isNotBilledOnTheStaleDate() {
        planIs(PaymentStructure.INSTALLMENTS, "1000000.00");
        entry(1, "1000000.00", oct(20));
        BillingSchedule stale = occurrence(1, "1000000.00", oct(15), BillingPeriodStatus.TAX_PENDING);

        assertThatThrownBy(() -> check(stale))
                .hasMessageContaining("Payment 1 is now dated 2026-10-20")
                .hasMessageContaining("dated 2026-10-15");
    }

    @Test
    void reDatedEntryAlreadyInvoiced_isNotBilledASecondTime() {
        planIs(PaymentStructure.INSTALLMENTS, "1000000.00");
        entry(1, "500000.00", oct(20));
        entry(2, "500000.00", oct(31));
        // Payment 1 was invoiced on its old date; the re-dated entry produced a second occurrence.
        occurrence(1, "500000.00", oct(15), BillingPeriodStatus.INVOICED);
        BillingSchedule second = occurrence(1, "500000.00", oct(20), BillingPeriodStatus.SCHEDULED);

        assertThatThrownBy(() -> check(second))
                .hasMessageContaining("Payment 1 is represented by 2 active Billing Occurrences");
    }

    @Test
    void occurrenceWhosePaymentEntryWasRemoved_isNotBilled() {
        planIs(PaymentStructure.INSTALLMENTS, "1000000.00");
        entry(1, "1000000.00", oct(1));
        BillingSchedule orphan = occurrence(2, "500000.00", oct(15), BillingPeriodStatus.TAX_PENDING);

        assertThatThrownBy(() -> check(orphan))
                .hasMessageContaining("Payment 2 no longer exists in the active Milestone Plan");
    }

    @Test
    void occurrencesExceedingThePlanTotal_areNotBilled() {
        planIs(PaymentStructure.INSTALLMENTS, "500000.00");
        entry(1, "300000.00", oct(1));
        entry(2, "300000.00", oct(15));
        occurrence(1, "300000.00", oct(1), BillingPeriodStatus.INVOICED);
        BillingSchedule second = occurrence(2, "300000.00", oct(15), BillingPeriodStatus.SCHEDULED);

        assertThatThrownBy(() -> check(second))
                .hasMessageContaining("total 600000.00")
                .hasMessageContaining("more than the Milestone Plan total contract value 500000.00");
    }

    // ================================================================ missing or invalid data

    @Test
    void missingMilestonePlan_isNotBilled() {
        BillingSchedule milestone = occurrence(1, "800000.00", oct(15), BillingPeriodStatus.SCHEDULED);
        when(milestonePlanRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> check(milestone))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("No active Milestone Plan");
    }

    @Test
    void zeroAmountInstallment_isNotBilled() {
        planIs(PaymentStructure.INSTALLMENTS, "1000000.00");
        entry(1, "0.00", oct(1));
        BillingSchedule zero = occurrence(1, "0.00", oct(1), BillingPeriodStatus.SCHEDULED);

        assertThatThrownBy(() -> check(zero)).hasMessageContaining("has no positive billing amount");
    }

    @Test
    void missingPlanTotal_isNotBilled() {
        planIs(PaymentStructure.FULL_PAYMENT, null);
        entry(1, "800000.00", oct(15));
        BillingSchedule milestone = occurrence(1, "800000.00", oct(15), BillingPeriodStatus.SCHEDULED);

        assertThatThrownBy(() -> check(milestone)).hasMessageContaining("missing or not positive");
    }

    @Test
    void missingCurrency_isNotBilled() {
        planIs(PaymentStructure.FULL_PAYMENT, "800000.00");
        entry(1, "800000.00", oct(15));
        BillingSchedule milestone = occurrence(1, "800000.00", oct(15), BillingPeriodStatus.SCHEDULED);
        configuration.setCurrency(null);

        assertThatThrownBy(() -> check(milestone)).hasMessageContaining("No currency is configured");
    }
}
