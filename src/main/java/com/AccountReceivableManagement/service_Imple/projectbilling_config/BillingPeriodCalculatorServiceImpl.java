package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingPeriodDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingScheduleCalculationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingScheduleCalculationResponseDto;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingPeriodCalculatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BillingPeriodCalculatorServiceImpl implements BillingPeriodCalculatorService {

    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingConfigurationRepository billingConfigurationRepository;
    private final BillingRecurringConfigurationRepository recurringConfigurationRepository;

    @Override
    public BillingScheduleCalculationResponseDto calculateBillingSchedule(
            BillingScheduleCalculationRequestDto request) {

        List<BillingPeriodDto> periods = calculatePeriodsWithAmount(
                request.getStartDate(),
                request.getEndDate(),
                request.getDurationValue(),
                request.getDurationUnit(),
                request.getTotalContractValue()
        );

        return BillingScheduleCalculationResponseDto.builder()
                .periods(periods)
                .totalPeriods(periods.size())
                .totalScheduledAmount(periods.stream()
                        .map(BillingPeriodDto::getBillingAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    @Override
    public List<BillingPeriodDto> calculatePeriods(
            LocalDate startDate,
            LocalDate endDate,
            Integer durationValue,
            String durationUnit) {

        if (startDate == null || endDate == null) {
            throw new GlobalExceptionHandler.ValidationException("Start date and end date are required.");
        }

        if (startDate.isAfter(endDate)) {
            throw new GlobalExceptionHandler.ValidationException("Start date cannot be after end date.");
        }

        if (durationValue == null || durationValue <= 0) {
            throw new GlobalExceptionHandler.ValidationException("Duration value must be positive.");
        }

        List<BillingPeriodDto> periods = new ArrayList<>();
        LocalDate currentStart = startDate;
        int periodNumber = 1;

        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = calculateEndDate(currentStart, durationValue, durationUnit);

            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            boolean isPartial = !calculateEndDate(currentStart, durationValue, durationUnit)
                    .isEqual(currentEnd);

            periods.add(BillingPeriodDto.builder()
                    .periodNumber(periodNumber)
                    .periodStartDate(currentStart)
                    .periodEndDate(currentEnd)
                    .isPartialPeriod(isPartial)
                    .build());

            currentStart = currentEnd.plusDays(1);
            periodNumber++;
        }

        return periods;
    }

    @Override
    public List<BillingPeriodDto> calculatePeriodsWithAmount(
            LocalDate startDate,
            LocalDate endDate,
            Integer durationValue,
            String durationUnit,
            BigDecimal totalContractValue) {

        if (totalContractValue == null ||
                totalContractValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Total contract value cannot be negative.");
        }

        List<BillingPeriodDto> periods =
                calculatePeriods(startDate, endDate, durationValue, durationUnit);

        if (periods.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "No billing periods could be generated.");
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        if (totalDays <= 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invalid contract date range.");
        }

        BigDecimal allocatedAmount = BigDecimal.ZERO;

        for (int i = 0; i < periods.size(); i++) {

            BillingPeriodDto period = periods.get(i);

            boolean isLastPeriod = i == periods.size() - 1;

            /*
             * Always calculate the final period as the remainder.
             * This guarantees that the total of all periods is exactly
             * equal to the contract value, avoiding rounding differences.
             */
            if (isLastPeriod) {

                BigDecimal finalAmount = totalContractValue
                        .subtract(allocatedAmount)
                        .setScale(2, RoundingMode.HALF_EVEN);

                period.setBillingAmount(finalAmount);

                continue;
            }

            long periodDays = ChronoUnit.DAYS.between(
                    period.getPeriodStartDate(),
                    period.getPeriodEndDate()
            ) + 1;

            if (periodDays <= 0) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Invalid billing period date range.");
            }

            BigDecimal periodAmount = totalContractValue
                    .multiply(BigDecimal.valueOf(periodDays))
                    .divide(
                            BigDecimal.valueOf(totalDays),
                            2,
                            RoundingMode.HALF_EVEN
                    );

            period.setBillingAmount(periodAmount);

            allocatedAmount = allocatedAmount.add(periodAmount);
        }

        return periods;
    }

    @Override
    public BigDecimal calculatePeriodAmount(
            BigDecimal totalContractValue,
            int totalPeriods,
            boolean isPartialPeriod,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate totalStart,
            LocalDate totalEnd) {

        if (totalContractValue == null ||
                totalContractValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Total contract value cannot be negative.");
        }

        if (periodStart == null ||
                periodEnd == null ||
                totalStart == null ||
                totalEnd == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Period dates are required.");
        }

        if (periodStart.isAfter(periodEnd)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Period start date cannot be after period end date.");
        }

        if (totalStart.isAfter(totalEnd)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Total start date cannot be after total end date.");
        }

        long totalDays =
                ChronoUnit.DAYS.between(totalStart, totalEnd) + 1;

        long periodDays =
                ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;

        if (totalDays <= 0 || periodDays <= 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invalid date range for amount calculation.");
        }

        return totalContractValue
                .multiply(BigDecimal.valueOf(periodDays))
                .divide(
                        BigDecimal.valueOf(totalDays),
                        2,
                        RoundingMode.HALF_EVEN
                );
    }

    @Override
    public LocalDate calculateEndDate(
            LocalDate startDate,
            Integer durationValue,
            String durationUnit) {

        if (startDate == null || durationValue == null || durationUnit == null) {
            throw new GlobalExceptionHandler.ValidationException("Start date, duration value, and unit are required.");
        }

        RenewalDurationUnit unit = RenewalDurationUnit.valueOf(durationUnit.toUpperCase());

        return switch (unit) {
            case DAYS -> startDate.plusDays(durationValue - 1);
            case MONTHS -> startDate.plusMonths(durationValue).minusDays(1);
            case YEARS -> startDate.plusYears(durationValue).minusDays(1);
        };
    }

    @Override
    public long calculateDurationInDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new GlobalExceptionHandler.ValidationException("Start date and end date are required.");
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    @Override
    public int calculateNumberOfPeriods(
            LocalDate startDate,
            LocalDate endDate,
            Integer durationValue,
            String durationUnit) {

        if (startDate == null || endDate == null) {
            throw new GlobalExceptionHandler.ValidationException("Start date and end date are required.");
        }

        if (durationValue == null || durationValue <= 0) {
            throw new GlobalExceptionHandler.ValidationException("Duration value must be positive.");
        }

        long totalDays = calculateDurationInDays(startDate, endDate);
        long periodDays = calculateDurationInDays(
                startDate,
                calculateEndDate(startDate, durationValue, durationUnit)
        );

        if (periodDays <= 0) {
            throw new GlobalExceptionHandler.ValidationException("Invalid period duration calculation.");
        }

        int fullPeriods = (int) (totalDays / periodDays);
        long remainingDays = totalDays % periodDays;

        return remainingDays > 0 ? fullPeriods + 1 : fullPeriods;
    }

    @Override
    public void recalculateScheduleForProjectDurationChange(
            UUID billingConfigurationId,
            LocalDate newProjectEndDate) {

        BillingConfiguration configuration = billingConfigurationRepository.findById(billingConfigurationId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found."));

        BillingRecurringConfiguration recurring =
                recurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(configuration)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Recurring Configuration not found."));

        LocalDate recurringStart = recurring.getRecurringStartDate();
        if (recurringStart == null) {
            recurringStart = configuration.getProject().getStartDate();
        }

        LocalDate recurringEnd = recurring.getRecurringEndDate();
        if (recurringEnd == null) {
            recurringEnd = newProjectEndDate;
        } else if (recurringEnd.isAfter(newProjectEndDate)) {
            recurringEnd = newProjectEndDate;
        }

        BillingFrequencyMaster frequency = configuration.getBillingFrequency();
        if (frequency == null || frequency.getDurationUnit() == null) {
            throw new GlobalExceptionHandler.ValidationException("Billing frequency not properly configured.");
        }

        List<BillingSchedule> existingSchedules = billingScheduleRepository
                .findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurring);

        List<BillingSchedule> invoicedSchedules = existingSchedules.stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getIsInvoiced()))
                .toList();

        if (!invoicedSchedules.isEmpty()) {
            LocalDate lastInvoicedDate = invoicedSchedules.stream()
                    .map(BillingSchedule::getPeriodEndDate)
                    .max(LocalDate::compareTo)
                    .orElse(recurringStart);

            if (lastInvoicedDate.isAfter(recurringStart)) {
                recurringStart = lastInvoicedDate.plusDays(1);
            }
        }

        billingScheduleRepository.deleteByRecurringConfiguration(recurring);

        List<BillingPeriodDto> newPeriods = calculatePeriodsWithAmount(
                recurringStart,
                recurringEnd,
                frequency.getDurationValue(),
                frequency.getDurationUnit().name(),
                recurring.getContractValue()
        );

        int startingPeriodNumber = invoicedSchedules.size() + 1;

        for (BillingPeriodDto periodDto : newPeriods) {
            BillingSchedule schedule = BillingSchedule.builder()
                    .billingConfiguration(configuration)
                    .recurringConfiguration(recurring)
                    .periodNumber(startingPeriodNumber++)
                    .periodStartDate(periodDto.getPeriodStartDate())
                    .periodEndDate(periodDto.getPeriodEndDate())
                    .billingAmount(periodDto.getBillingAmount())
                    .scheduleType(BillingScheduleType.PRIMARY)
                    .isPartialPeriod(periodDto.getIsPartialPeriod())
                    .periodStatus(BillingPeriodStatus.PENDING)
                    .isInvoiced(false)
                    .isActive(true)
                    .build();

            billingScheduleRepository.save(schedule);
        }
    }

    @Override
    public void recalculateScheduleForBudgetChange(
            UUID billingConfigurationId,
            BigDecimal newContractValue) {

        BillingConfiguration configuration = billingConfigurationRepository.findById(billingConfigurationId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found."));

        BillingRecurringConfiguration recurring =
                recurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(configuration)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Recurring Configuration not found."));

        recurring.setContractValue(newContractValue);
        recurringConfigurationRepository.save(recurring);

        List<BillingSchedule> pendingSchedules = billingScheduleRepository
                .findByRecurringConfigurationAndPeriodStatusAndIsActiveTrue(
                        recurring,
                        BillingPeriodStatus.PENDING);

        if (pendingSchedules.isEmpty()) {
            return;
        }

        if (newContractValue == null ||
                newContractValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Contract value cannot be negative.");
        }

        long totalPendingDays = pendingSchedules.stream()
                .mapToLong(schedule ->
                        ChronoUnit.DAYS.between(
                                schedule.getPeriodStartDate(),
                                schedule.getPeriodEndDate()
                        ) + 1
                )
                .sum();

        if (totalPendingDays <= 0) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invalid pending billing period dates.");
        }

        BigDecimal allocatedAmount = BigDecimal.ZERO;

        for (int i = 0; i < pendingSchedules.size(); i++) {

            BillingSchedule schedule = pendingSchedules.get(i);

            boolean isLastPending =
                    i == pendingSchedules.size() - 1;

            if (isLastPending) {

                BigDecimal finalAmount =
                        newContractValue
                                .subtract(allocatedAmount)
                                .setScale(2, RoundingMode.HALF_EVEN);

                schedule.setBillingAmount(finalAmount);

            } else {

                long periodDays =
                        ChronoUnit.DAYS.between(
                                schedule.getPeriodStartDate(),
                                schedule.getPeriodEndDate()
                        ) + 1;

                BigDecimal periodAmount =
                        newContractValue
                                .multiply(BigDecimal.valueOf(periodDays))
                                .divide(
                                        BigDecimal.valueOf(totalPendingDays),
                                        2,
                                        RoundingMode.HALF_EVEN
                                );

                schedule.setBillingAmount(periodAmount);

                allocatedAmount =
                        allocatedAmount.add(periodAmount);
            }

            billingScheduleRepository.save(schedule);
        }
    }

    @Override
    public void recalculateScheduleForDurationAndBudgetChange(
            UUID billingConfigurationId,
            LocalDate newProjectEndDate,
            BigDecimal newContractValue) {

        recalculateScheduleForProjectDurationChange(billingConfigurationId, newProjectEndDate);
        recalculateScheduleForBudgetChange(billingConfigurationId, newContractValue);
    }

    @Override
    public List<BillingPeriodDto> calculateRenewalPeriods(
            LocalDate renewalStartDate,
            Integer renewalDurationValue,
            String renewalDurationUnit,
            Integer billingFrequencyValue,
            String billingFrequencyUnit,
            BigDecimal renewalContractValue) {

        if (renewalStartDate == null) {
            throw new GlobalExceptionHandler.ValidationException("Renewal start date is required.");
        }

        if (renewalDurationValue == null || renewalDurationValue <= 0) {
            throw new GlobalExceptionHandler.ValidationException("Renewal duration value must be positive.");
        }

        if (billingFrequencyValue == null || billingFrequencyValue <= 0) {
            throw new GlobalExceptionHandler.ValidationException("Billing frequency value must be positive.");
        }

        LocalDate renewalEndDate = calculateEndDate(
                renewalStartDate,
                renewalDurationValue,
                renewalDurationUnit
        );

        return calculatePeriodsWithAmount(
                renewalStartDate,
                renewalEndDate,
                billingFrequencyValue,
                billingFrequencyUnit,
                renewalContractValue != null ? renewalContractValue : BigDecimal.ZERO
        );
    }
}
