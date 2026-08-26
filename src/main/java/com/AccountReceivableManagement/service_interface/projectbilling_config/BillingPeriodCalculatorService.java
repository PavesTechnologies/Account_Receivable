package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingScheduleCalculationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingScheduleCalculationResponseDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BillingPeriodCalculatorService {

    BillingScheduleCalculationResponseDto calculateBillingSchedule(
            BillingScheduleCalculationRequestDto request);

    List<BillingPeriodDto> calculatePeriods(
            LocalDate startDate,
            LocalDate endDate,
            Integer durationValue,
            String durationUnit);

    List<BillingPeriodDto> calculatePeriodsWithAmount(
            LocalDate startDate,
            LocalDate endDate,
            Integer durationValue,
            String durationUnit,
            BigDecimal totalContractValue);

    BigDecimal calculatePeriodAmount(
            BigDecimal totalContractValue,
            int totalPeriods,
            boolean isPartialPeriod,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate totalStart,
            LocalDate totalEnd);

    LocalDate calculateEndDate(
            LocalDate startDate,
            Integer durationValue,
            String durationUnit);

    long calculateDurationInDays(
            LocalDate startDate,
            LocalDate endDate);

    int calculateNumberOfPeriods(
            LocalDate startDate,
            LocalDate endDate,
            Integer durationValue,
            String durationUnit);

    void recalculateScheduleForProjectDurationChange(
            UUID billingConfigurationId,
            LocalDate newProjectEndDate);

    void recalculateScheduleForBudgetChange(
            UUID billingConfigurationId,
            BigDecimal newContractValue);

    void recalculateScheduleForDurationAndBudgetChange(
            UUID billingConfigurationId,
            LocalDate newProjectEndDate,
            BigDecimal newContractValue);

    List<BillingPeriodDto> calculateRenewalPeriods(
            LocalDate renewalStartDate,
            Integer renewalDurationValue,
            String renewalDurationUnit,
            Integer billingFrequencyValue,
            String billingFrequencyUnit,
            BigDecimal renewalContractValue);
}
