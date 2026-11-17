package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

/**
 * Billing periods follow the configured frequency (duration value + unit)
 * and the configuration's own start date - never a hardcoded calendar
 * month, month end or first-of-month. This is the existing calculator the
 * later T&M automation phase derives its periods from.
 */
class BillingPeriodCalculatorServiceImplPeriodTest {

    private final BillingPeriodCalculatorServiceImpl calculator = new BillingPeriodCalculatorServiceImpl(
            mock(BillingScheduleRepository.class),
            mock(BillingConfigurationRepository.class),
            mock(BillingRecurringConfigurationRepository.class));

    @Test
    void weeklyFrequency_producesSevenDayPeriodsFromTheStartDate() {
        List<BillingPeriodDto> periods = calculator.calculatePeriods(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 25), 7, "DAYS");

        assertThat(periods)
                .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)),
                        tuple(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)),
                        tuple(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));
    }

    @Test
    void monthlyFrequency_startingOnTheFirst_alignsWithCalendarMonths() {
        List<BillingPeriodDto> periods = calculator.calculatePeriods(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), 1, "MONTHS");

        assertThat(periods)
                .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
                        tuple(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                        tuple(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void monthlyFrequency_startingMidMonth_keepsTheMidMonthCycle() {
        List<BillingPeriodDto> periods = calculator.calculatePeriods(
                LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 14), 1, "MONTHS");

        assertThat(periods)
                .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14)),
                        tuple(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 14)),
                        tuple(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 14)));
    }

    @Test
    void quarterlyFrequency_withShortFinalPeriod_endsOnTheConfiguredEndDate() {
        List<BillingPeriodDto> periods = calculator.calculatePeriods(
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 15), 3, "MONTHS");

        assertThat(periods)
                .extracting(BillingPeriodDto::getPeriodStartDate, BillingPeriodDto::getPeriodEndDate,
                        BillingPeriodDto::getIsPartialPeriod)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), false),
                        tuple(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 15), true));
    }
}
