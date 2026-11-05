package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
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
 * {@link BillingOccurrenceServiceImpl#requireFixedPriceOccurrenceBillable(UUID)}:
 * the check automatic billing applies to a Fixed Price occurrence before it
 * is taxed. The amount billed is always the occurrence's own - one payment
 * of the full contract value, or its share of a split contract - and is only
 * validated here, never recomputed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedPriceOccurrenceBillabilityTest {

    private static final BigDecimal CONTRACT_VALUE = new BigDecimal("1000000.00");

    @Mock
    private com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository automaticBillingRunRepository;

    @Mock private BillingScheduleRepository scheduleRepository;
    @Mock private BillingFixedPriceRepository fixedPriceRepository;
    @InjectMocks private BillingOccurrenceServiceImpl service;

    private BillingConfiguration configuration;
    private final List<BillingSchedule> activeOccurrences = new ArrayList<>();

    @BeforeEach
    void setUp() {
        configuration = BillingConfiguration.builder()
                .billingConfigurationId(UUID.randomUUID())
                .currency(CurrencyMaster.builder().currencyId(UUID.randomUUID()).currencyCode("INR").build())
                .build();
        fixedPriceIs(CONTRACT_VALUE);
        when(scheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration))
                .thenReturn(activeOccurrences);
    }

    private void fixedPriceIs(BigDecimal contractValue) {
        when(fixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                .thenReturn(Optional.of(BillingFixedPriceConfiguration.builder()
                        .billingConfiguration(configuration)
                        .contractValue(contractValue)
                        .isActive(true)
                        .build()));
    }

    private BillingSchedule occurrence(int periodNumber, String amount, BillingPeriodStatus status) {
        BillingSchedule occurrence = BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingConfiguration(configuration)
                .periodNumber(periodNumber)
                .periodStartDate(LocalDate.of(2026, periodNumber, 1))
                .periodEndDate(LocalDate.of(2026, periodNumber, 28))
                .billingDate(LocalDate.of(2026, periodNumber, 28))
                .billingAmount(amount != null ? new BigDecimal(amount) : null)
                .periodStatus(status)
                .isActive(true)
                .build();
        activeOccurrences.add(occurrence);
        when(scheduleRepository.findById(occurrence.getBillingScheduleId())).thenReturn(Optional.of(occurrence));
        return occurrence;
    }

    private void check(BillingSchedule occurrence) {
        service.requireFixedPriceOccurrenceBillable(occurrence.getBillingScheduleId());
    }

    // ================================================================ payment structures

    @Test
    void singlePayment_ofTheFullContractValue_isBillable() {
        BillingSchedule oneTime = occurrence(1, "1000000.00", BillingPeriodStatus.SCHEDULED);

        assertThatCode(() -> check(oneTime)).doesNotThrowAnyException();
    }

    @Test
    void contractSplitIntoScheduledPayments_eachPaymentIsBillableForItsOwnShare() {
        BillingSchedule first = occurrence(1, "333333.33", BillingPeriodStatus.INVOICED);
        BillingSchedule second = occurrence(2, "333333.33", BillingPeriodStatus.TAX_PENDING);
        BillingSchedule last = occurrence(3, "333333.34", BillingPeriodStatus.SCHEDULED);

        assertThatCode(() -> check(second)).doesNotThrowAnyException();
        assertThatCode(() -> check(last)).doesNotThrowAnyException();
        assertThatCode(() -> check(first)).doesNotThrowAnyException();
    }

    @Test
    void scheduleRegeneratedFromTheFullContractValueAfterBilling_isNotBilled() {
        // Period 1 was invoiced; the remaining periods were regenerated over the whole contract value.
        occurrence(1, "500000.00", BillingPeriodStatus.INVOICED);
        BillingSchedule second = occurrence(2, "500000.00", BillingPeriodStatus.SCHEDULED);
        occurrence(3, "500000.00", BillingPeriodStatus.SCHEDULED);

        assertThatThrownBy(() -> check(second))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("total 1500000.00")
                .hasMessageContaining("more than the Fixed Price contract value 1000000.00");
    }

    @Test
    void cancelledOccurrences_doNotCountAgainstTheContractValue() {
        occurrence(1, "1000000.00", BillingPeriodStatus.CANCELLED);
        BillingSchedule replacement = occurrence(2, "1000000.00", BillingPeriodStatus.SCHEDULED);

        assertThatCode(() -> check(replacement)).doesNotThrowAnyException();
    }

    // ================================================================ missing or invalid data

    @Test
    void missingFixedPriceConfiguration_isNotBilled() {
        BillingSchedule oneTime = occurrence(1, "1000000.00", BillingPeriodStatus.SCHEDULED);
        when(fixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> check(oneTime))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("No active Fixed Price configuration");
    }

    @Test
    void missingOrZeroOccurrenceAmount_isNotBilled() {
        BillingSchedule missing = occurrence(1, null, BillingPeriodStatus.SCHEDULED);
        BillingSchedule zero = occurrence(2, "0.00", BillingPeriodStatus.SCHEDULED);

        assertThatThrownBy(() -> check(missing)).hasMessageContaining("has no positive billing amount");
        assertThatThrownBy(() -> check(zero)).hasMessageContaining("has no positive billing amount");
    }

    @Test
    void missingContractValue_isNotBilled() {
        BillingSchedule oneTime = occurrence(1, "1000000.00", BillingPeriodStatus.SCHEDULED);
        fixedPriceIs(null);

        assertThatThrownBy(() -> check(oneTime)).hasMessageContaining("contract value")
                .hasMessageContaining("missing or not positive");
    }

    @Test
    void missingCurrency_isNotBilled() {
        BillingSchedule oneTime = occurrence(1, "1000000.00", BillingPeriodStatus.SCHEDULED);
        configuration.setCurrency(null);

        assertThatThrownBy(() -> check(oneTime))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("No currency is configured");
    }

    @Test
    void unknownOccurrence_isNotBilled() {
        UUID unknown = UUID.randomUUID();
        when(scheduleRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireFixedPriceOccurrenceBillable(unknown))
                .hasMessageContaining("no longer exists");
    }
}
