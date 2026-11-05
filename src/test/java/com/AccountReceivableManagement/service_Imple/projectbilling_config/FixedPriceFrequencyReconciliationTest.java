package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 12 regression: updating an approved Fixed Price configuration
 * infers the schedule's previous frequency from its occurrences' periods.
 * Periods end inclusively (1 Jan - 31 Jan is one month), but the inference
 * counted 0 months for them - so no frequency ever matched, the comparison
 * of that {@code null} threw a NullPointerException and every such update
 * failed. Now the previous frequency is inferred correctly, a frequency
 * change regenerates the unprocessed occurrences, and a duration that
 * matches no frequency is handled without failing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedPriceFrequencyReconciliationTest {

    @Mock
    private com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository automaticBillingRunRepository;

    @Mock private BillingConfigurationRepository configurationRepository;
    @Mock private BillingFixedPriceRepository fixedPriceRepository;
    @Mock private BillingScheduleRepository scheduleRepository;
    @Mock private BillingFrequencyMasterRepository frequencyRepository;
    @InjectMocks private BillingOccurrenceServiceImpl service;

    private final BillingFrequencyMaster monthly = frequency("Monthly", 1);
    private final BillingFrequencyMaster quarterly = frequency("Quarterly", 3);
    private BillingConfiguration configuration;
    private final List<BillingSchedule> existing = new ArrayList<>();

    private static BillingFrequencyMaster frequency(String name, int months) {
        return BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName(name)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .durationValue(months)
                .isActive(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        configuration = BillingConfiguration.builder()
                .billingConfigurationId(UUID.randomUUID())
                .approvalStatus(ApprovalStatus.APPROVED)
                .billingType(BillingTypeMaster.builder().billingTypeName("Fixed Price").build())
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(LocalDate.of(2026, 6, 30))
                .build();
        when(configurationRepository.findById(configuration.getBillingConfigurationId()))
                .thenReturn(Optional.of(configuration));
        when(fixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(configuration))
                .thenReturn(Optional.of(BillingFixedPriceConfiguration.builder()
                        .billingConfiguration(configuration)
                        .contractValue(new BigDecimal("600000.00"))
                        .isActive(true)
                        .build()));
        when(scheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration))
                .thenReturn(existing);
        // The monthly schedule generated earlier: Jan - Jun, nothing billed yet.
        for (int month = 1; month <= 6; month++) {
            LocalDate start = LocalDate.of(2026, month, 1);
            existing.add(BillingSchedule.builder()
                    .billingScheduleId(UUID.randomUUID())
                    .billingConfiguration(configuration)
                    .periodNumber(month)
                    .periodStartDate(start)
                    .periodEndDate(start.withDayOfMonth(start.lengthOfMonth()))
                    .billingDate(start.withDayOfMonth(start.lengthOfMonth()))
                    .billingAmount(new BigDecimal("100000.00"))
                    .isPartialPeriod(false)
                    .periodStatus(BillingPeriodStatus.SCHEDULED)
                    .isActive(true)
                    .build());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthlyScheduleChangedToQuarterly_isDetected_andRegenerated() {
        when(frequencyRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc())
                .thenReturn(List.of(monthly, quarterly));
        configuration.setBillingFrequency(quarterly);

        service.reconcileOccurrencesOnConfigurationUpdate(configuration.getBillingConfigurationId());

        // First the six monthly occurrences are retired (deactivated, not deleted),
        // then the two quarterly periods are inserted.
        ArgumentCaptor<Iterable<BillingSchedule>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(scheduleRepository, org.mockito.Mockito.times(2)).saveAll(saved.capture());
        assertThat(saved.getAllValues().get(0)).hasSize(6)
                .allSatisfy(retired -> assertThat(retired.getIsActive()).isFalse());
        assertThat(saved.getAllValues().get(1)).extracting(BillingSchedule::getPeriodEndDate)
                .containsExactly(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 6, 30));
    }

    @Test
    void unchangedMonthlySchedule_isNotRegenerated() {
        when(frequencyRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc())
                .thenReturn(List.of(monthly, quarterly));
        configuration.setBillingFrequency(monthly);

        service.reconcileOccurrencesOnConfigurationUpdate(configuration.getBillingConfigurationId());

        verify(scheduleRepository, never()).saveAll(any());
    }

    @Test
    void scheduleWhosePeriodsMatchNoFrequency_doesNotFailTheUpdate() {
        // Only a weekly (days-based) frequency exists - the monthly periods match none.
        BillingFrequencyMaster weekly = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Weekly")
                .durationUnit(RenewalDurationUnit.DAYS)
                .durationValue(7)
                .isActive(true)
                .build();
        when(frequencyRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc()).thenReturn(List.of(weekly));
        configuration.setBillingFrequency(weekly);

        assertThatCode(() -> service.reconcileOccurrencesOnConfigurationUpdate(
                configuration.getBillingConfigurationId())).doesNotThrowAnyException();
    }
}
