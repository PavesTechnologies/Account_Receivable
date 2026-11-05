package com.AccountReceivableManagement.strategy.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.TimesheetDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTMRateCard;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingRatePeriod;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.PricingModel;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegration;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingTMRateCardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeAndMaterialBillingStrategyTest {

    private static final Long PROJECT_ID = 23L;

    @Mock
    private TimesheetIntegration timesheetIntegration;

    @Mock
    private BillingTMRateCardRepository billingTMRateCardRepository;

    @InjectMocks
    private TimeAndMaterialBillingStrategy strategy;

    private final UUID configurationId = UUID.randomUUID();

    private BillingSnapshotCreateRequestDto request(LocalDate start, LocalDate end) {
        return BillingSnapshotCreateRequestDto.builder()
                .projectId(PROJECT_ID)
                .billingConfigurationId(configurationId)
                .billingPeriodStart(start)
                .billingPeriodEnd(end)
                .build();
    }

    private BillingConfigurationResponseDto configuration(PricingModel pricingModel, BigDecimal hourlyRate) {
        return BillingConfigurationResponseDto.builder()
                .billingConfigurationId(configurationId)
                .pricingModel(pricingModel)
                .hourlyRate(hourlyRate)
                .approved(true)
                .build();
    }

    private TimesheetDto timesheet(String id, String role, LocalDate workDate) {
        return TimesheetDto.builder()
                .sourceReferenceId(id)
                .resourceName("Resource " + id)
                .role(role)
                .workDate(workDate)
                .hours(new BigDecimal("8"))
                .approved(true)
                .billable(true)
                .build();
    }

    private BillingTMRateCard rateCard(String role, String rate) {
        return BillingTMRateCard.builder()
                .rateCardId(UUID.randomUUID())
                .roleName(role)
                .rate(new BigDecimal(rate))
                .ratePeriod(BillingRatePeriod.HOURLY)
                .isActive(true)
                .build();
    }

    @Test
    void roleBased_eachTimesheetGetsTheRateOfItsOwnRole() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end)).thenReturn(List.of(
                timesheet("TS-1", "Developer", workDate),
                timesheet("TS-2", "senior developer ", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(rateCard("Senior Developer", "2000"), rateCard("Developer", "1500")));

        BillingAcquisitionResultDto result =
                strategy.acquire(configuration(PricingModel.ROLE_BASED, null), request(start, end));

        assertThat(result.getTimesheets())
                .extracting(TimesheetDto::getHourlyRate)
                .containsExactly(new BigDecimal("1500"), new BigDecimal("2000"));
    }

    @Test
    void roleBased_rateIsTheOneEffectiveOnTheWorkDate() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate beforeRevision = LocalDate.of(2026, 10, 10);
        LocalDate afterRevision = LocalDate.of(2026, 10, 20);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end)).thenReturn(List.of(
                timesheet("TS-1", "Developer", beforeRevision),
                timesheet("TS-2", "Developer", afterRevision)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, beforeRevision))
                .thenReturn(List.of(rateCard("Developer", "1500")));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, afterRevision))
                .thenReturn(List.of(rateCard("Developer", "1650")));

        BillingAcquisitionResultDto result =
                strategy.acquire(configuration(PricingModel.ROLE_BASED, null), request(start, end));

        assertThat(result.getTimesheets())
                .extracting(TimesheetDto::getHourlyRate)
                .containsExactly(new BigDecimal("1500"), new BigDecimal("1650"));
    }

    @Test
    void roleBased_unmatchedRole_leavesRateUnsetWithoutFallingBack() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Architect", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(rateCard("Developer", "1500")));

        BillingAcquisitionResultDto result =
                strategy.acquire(configuration(PricingModel.ROLE_BASED, new BigDecimal("999")), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isNull();
        verify(billingTMRateCardRepository, never())
                .findByBillingConfiguration_BillingConfigurationIdAndIsActiveTrue(any());
    }

    @Test
    void standard_usesConfigurationHourlyRateWhenNoRateCardExists() {
        LocalDate start = LocalDate.of(2026, 10, 5);
        LocalDate end = LocalDate.of(2026, 10, 11);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of());

        BillingAcquisitionResultDto result =
                strategy.acquire(configuration(PricingModel.STANDARD, new BigDecimal("1200")), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1200");
    }

    @Test
    void anyPeriod_isPassedToTmsExactlyAsRequested() {
        LocalDate start = LocalDate.of(2026, 10, 15);
        LocalDate end = LocalDate.of(2026, 11, 14);
        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end)).thenReturn(List.of());

        strategy.acquire(configuration(PricingModel.STANDARD, new BigDecimal("1200")), request(start, end));

        verify(timesheetIntegration).getApprovedTimesheets(PROJECT_ID, start, end);
    }

    // ================================================================ rate period (Phase 12 regression)

    @Test
    void roleBased_dailyRateCard_isNeverMultipliedByHours() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);
        BillingTMRateCard daily = rateCard("Developer", "12000");
        daily.setRatePeriod(BillingRatePeriod.DAILY);
        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(daily));

        BillingAcquisitionResultDto result =
                strategy.acquire(configuration(PricingModel.ROLE_BASED, null), request(start, end));

        // No hourly rate: the validator rejects the acquisition instead of billing 8 x 12000.
        assertThat(result.getTimesheets().get(0).getHourlyRate()).isNull();
    }

    @Test
    void roleBased_hourlyCardIsUsed_whenTheRoleAlsoHasADailyCard() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);
        BillingTMRateCard daily = rateCard("Developer", "12000");
        daily.setRatePeriod(BillingRatePeriod.DAILY);
        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(daily, rateCard("Developer", "1500")));

        BillingAcquisitionResultDto result =
                strategy.acquire(configuration(PricingModel.ROLE_BASED, null), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1500");
    }

    @Test
    void standard_monthlyRateCard_fallsBackToTheConfiguredHourlyRate() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);
        BillingTMRateCard monthly = rateCard(null, "250000");
        monthly.setRatePeriod(BillingRatePeriod.MONTHLY);
        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", null, workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(monthly));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.STANDARD, new BigDecimal("1400")), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1400");
    }

    // ================================================================ G-23: Deterministic Standard Rate Selection

    @Test
    void standard_matchingRoleRateCard_selectsMatchingRoleRate() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(rateCard("Senior Developer", "2200"), rateCard("Developer", "1600")));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.STANDARD, new BigDecimal("1000")), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1600");
    }

    @Test
    void standard_unrelatedRoleRateCard_doesNotFallbackToUnrelatedRole() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Architect", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(rateCard("Developer", "1600")));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.STANDARD, new BigDecimal("1200")), request(start, end));

        // Falls back to configuration hourly rate rather than picking unrelated Developer rate card
        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1200");
    }

    @Test
    void standard_generalRateCardWithoutRole_isUsedWhenTimesheetHasNoMatchingRole() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "QA Engineer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(rateCard("Developer", "1600"), rateCard(null, "1450")));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.STANDARD, new BigDecimal("1000")), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1450");
    }

    @Test
    void standard_noActiveRateCardOrConfigurationRate_leavesHourlyRateNull() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 6);

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of());

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.STANDARD, null), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isNull();
        verify(billingTMRateCardRepository, never())
                .findByBillingConfiguration_BillingConfigurationIdAndIsActiveTrue(any());
    }

    @Test
    void competingEligibleRateCards_picksFirstRateInDeterministicOrder() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 15);

        // Suppose repository returned two competing active cards for "Developer":
        // 1. Revised rate 1800 (effectiveFrom 2026-10-01)
        // 2. Baseline rate 1500 (effectiveFrom NULL or older)
        BillingTMRateCard newerCard = BillingTMRateCard.builder()
                .rateCardId(UUID.randomUUID())
                .roleName("Developer")
                .rate(new BigDecimal("1800"))
                .ratePeriod(BillingRatePeriod.HOURLY)
                .effectiveFrom(LocalDate.of(2026, 10, 1))
                .isActive(true)
                .build();
        BillingTMRateCard olderCard = BillingTMRateCard.builder()
                .rateCardId(UUID.randomUUID())
                .roleName("Developer")
                .rate(new BigDecimal("1500"))
                .ratePeriod(BillingRatePeriod.HOURLY)
                .effectiveFrom(null)
                .isActive(true)
                .build();

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(newerCard, olderCard));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.ROLE_BASED, null), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1800");
    }

    @Test
    void competingRateCards_nonHourlyCardFilteredOut_hourlyCardSelected() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate workDate = LocalDate.of(2026, 10, 15);

        BillingTMRateCard monthlyCard = BillingTMRateCard.builder()
                .rateCardId(UUID.randomUUID())
                .roleName("Developer")
                .rate(new BigDecimal("160000"))
                .ratePeriod(BillingRatePeriod.MONTHLY)
                .isActive(true)
                .build();
        BillingTMRateCard hourlyCard = BillingTMRateCard.builder()
                .rateCardId(UUID.randomUUID())
                .roleName("Developer")
                .rate(new BigDecimal("1000"))
                .ratePeriod(BillingRatePeriod.HOURLY)
                .isActive(true)
                .build();

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end))
                .thenReturn(List.of(timesheet("TS-1", "Developer", workDate)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, workDate))
                .thenReturn(List.of(monthlyCard, hourlyCard));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.ROLE_BASED, null), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1000");
    }

    @Test
    void multipleTimesheets_differentWorkDates_queryRatesForRespectiveDates() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        LocalDate date1 = LocalDate.of(2026, 10, 5);
        LocalDate date2 = LocalDate.of(2026, 10, 25);

        BillingTMRateCard earlyRate = rateCard("Developer", "1200");
        BillingTMRateCard lateRate = rateCard("Developer", "1400");

        when(timesheetIntegration.getApprovedTimesheets(PROJECT_ID, start, end)).thenReturn(List.of(
                timesheet("TS-1", "Developer", date1),
                timesheet("TS-2", "Developer", date2)));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, date1))
                .thenReturn(List.of(earlyRate));
        when(billingTMRateCardRepository.findActiveRatesByConfigurationAndDate(configurationId, date2))
                .thenReturn(List.of(lateRate));

        BillingAcquisitionResultDto result = strategy.acquire(
                configuration(PricingModel.ROLE_BASED, null), request(start, end));

        assertThat(result.getTimesheets().get(0).getHourlyRate()).isEqualByComparingTo("1200");
        assertThat(result.getTimesheets().get(1).getHourlyRate()).isEqualByComparingTo("1400");
    }
}
