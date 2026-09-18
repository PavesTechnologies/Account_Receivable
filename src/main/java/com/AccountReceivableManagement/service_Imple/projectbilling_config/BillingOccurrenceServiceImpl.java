package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFixedPriceConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingFrequencyMaster;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingTypeMaster;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.RenewalDurationUnit;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFixedPriceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingFrequencyMasterRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingRecurringConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
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
@RequiredArgsConstructor
public class BillingOccurrenceServiceImpl {

    private final BillingScheduleRepository billingScheduleRepository;
    private final BillingFixedPriceRepository billingFixedPriceRepository;
    private final BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;
    private final BillingPeriodCalculatorServiceImpl billingPeriodCalculatorService;
    private final com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository billingConfigurationRepository;
    private final BillingFrequencyMasterRepository billingFrequencyRepository;
    private final TaxCalculationRepository taxCalculationRepository;
    private final TaxCalculationService taxCalculationService;
    private final BillingOccurrenceTransactionService billingOccurrenceTransactionService;

    private void validateConfigurationApproved(BillingConfiguration configuration) {
        if (configuration.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Configuration must be APPROVED to generate Billing Occurrences. Current status: " +
                    configuration.getApprovalStatus());
        }
    }

    public void generateOccurrencesForFixedPrice(UUID billingConfigurationId) {
        BillingConfiguration configuration = billingConfigurationRepository
                .findById(billingConfigurationId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found"));

        validateConfigurationApproved(configuration);

        BillingFixedPriceConfiguration fixedPrice = billingFixedPriceRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Fixed Price Configuration not found"));

        LocalDate effectiveFrom = fixedPrice.getEffectiveFrom() != null
                ? fixedPrice.getEffectiveFrom()
                : configuration.getEffectiveFrom();

        LocalDate effectiveTo = fixedPrice.getEffectiveTo() != null
                ? fixedPrice.getEffectiveTo()
                : configuration.getEffectiveTo();

        if (effectiveFrom == null || effectiveTo == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From and Effective To dates are required for Fixed Price billing");
        }

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To");
        }

        BillingFrequencyMaster frequency = configuration.getBillingFrequency();
        if (frequency == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency is required for Fixed Price billing");
        }

        String frequencyName = frequency.getBillingFrequencyName().trim();

        if (frequencyName.equalsIgnoreCase("One-Time")) {
            generateOneTimeFixedPriceOccurrence(configuration, fixedPrice, effectiveFrom, effectiveTo);
        } else {
            generateRecurringFixedPriceOccurrences(configuration, fixedPrice, frequency, effectiveFrom, effectiveTo);
        }
    }

    public void generateOccurrencesForRecurring(UUID billingConfigurationId) {
        BillingConfiguration configuration = billingConfigurationRepository
                .findById(billingConfigurationId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found"));

        validateConfigurationApproved(configuration);

        BillingRecurringConfiguration recurring = billingRecurringConfigurationRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Recurring Configuration not found"));

        LocalDate effectiveFrom = recurring.getRecurringStartDate() != null
                ? recurring.getRecurringStartDate()
                : configuration.getEffectiveFrom();

        LocalDate effectiveTo = recurring.getRecurringEndDate() != null
                ? recurring.getRecurringEndDate()
                : configuration.getEffectiveTo();

        if (effectiveFrom == null || effectiveTo == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring Start Date and Recurring End Date are required");
        }

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring Start Date cannot be after Recurring End Date");
        }

        BillingFrequencyMaster frequency = recurring.getBillingFrequency() != null
                ? recurring.getBillingFrequency()
                : configuration.getBillingFrequency();

        if (frequency == null) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Frequency is required for Recurring billing");
        }

        generateRecurringOccurrences(configuration, recurring, frequency, effectiveFrom, effectiveTo);
    }

    public void reconcileOccurrencesOnConfigurationUpdate(UUID billingConfigurationId) {
        BillingConfiguration configuration = billingConfigurationRepository
                .findById(billingConfigurationId)
                .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                        "Billing Configuration not found"));

        validateConfigurationApproved(configuration);

        BillingTypeMaster billingType = configuration.getBillingType();
        if (billingType == null) {
            return;
        }

        String billingTypeName = billingType.getBillingTypeName().trim();

        if (billingTypeName.equalsIgnoreCase("Fixed Price")) {
            reconcileFixedPriceOccurrences(configuration);
        } else if (billingTypeName.equalsIgnoreCase("Recurring") ||
                billingTypeName.equalsIgnoreCase("Subscription")) {
            reconcileRecurringOccurrences(configuration);
        }
    }

    private void generateOneTimeFixedPriceOccurrence(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        if (billingScheduleRepository.existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
                configuration, effectiveFrom, effectiveTo)) {
            log.info("One-time occurrence already exists for configuration {}", configuration.getBillingConfigurationId());
            return;
        }

        BillingSchedule schedule = BillingSchedule.builder()
                .billingConfiguration(configuration)
                .periodNumber(1)
                .periodStartDate(effectiveFrom)
                .periodEndDate(effectiveTo)
                .billingDate(effectiveTo)
                .billingAmount(fixedPrice.getContractValue())
                .scheduleType(BillingScheduleType.PRIMARY)
                .isPartialPeriod(false)
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .taxStatus(BillingPeriodStatus.PENDING)
                .isInvoiced(false)
                .isActive(true)
                .build();

        try {
            billingScheduleRepository.save(schedule);

            log.info(
                    "Generated one-time occurrence for Fixed Price configuration {} with period {} to {}",
                    configuration.getBillingConfigurationId(),
                    effectiveFrom,
                    effectiveTo
            );

        } catch (DataIntegrityViolationException e) {
            log.error(
                    "Data integrity violation while saving ONE-TIME billing occurrence for configuration {}. " +
                    "Period: {} to {}. Root cause: {}. This may indicate a duplicate occurrence.",
                    configuration.getBillingConfigurationId(),
                    effectiveFrom,
                    effectiveTo,
                    e.getMostSpecificCause().getMessage(),
                    e
            );
            throw e;
        } catch (Exception e) {

            log.error(
                    "Failed to save ONE-TIME billing occurrence for configuration {}. " +
                    "Period: {} to {}. Root cause: {}",
                    configuration.getBillingConfigurationId(),
                    effectiveFrom,
                    effectiveTo,
                    e.getMessage(),
                    e
            );

            throw e;
        }
    }

    private void generateRecurringFixedPriceOccurrences(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            BillingFrequencyMaster frequency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        String frequencyName = frequency.getBillingFrequencyName() != null 
                ? frequency.getBillingFrequencyName().trim().toLowerCase() 
                : "";

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Effective From cannot be after Effective To.");
        }

        List<BillingSchedule> existingSchedules = billingScheduleRepository
                .findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration);

        List<BillingSchedule> processedSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED ||
                        s.getPeriodStatus() == BillingPeriodStatus.INVOICED)
                .toList();

        LocalDate generationStartDate = effectiveFrom;
        if (!processedSchedules.isEmpty()) {
            LocalDate lastProcessedDate = processedSchedules.stream()
                    .map(BillingSchedule::getPeriodEndDate)
                    .max(LocalDate::compareTo)
                    .orElse(effectiveFrom);
            generationStartDate = lastProcessedDate.plusDays(1);
        }

        if (generationStartDate.isAfter(effectiveTo)) {
            log.info("No new occurrences to generate for Fixed Price configuration {}", configuration.getBillingConfigurationId());
            return;
        }

        List<BillingSchedule> newSchedules = calculateAndCreateSchedules(
                configuration,
                null,
                frequency,
                generationStartDate,
                effectiveTo,
                fixedPrice.getContractValue(),
                BillingScheduleType.PRIMARY,
                existingSchedules.size() + 1
        );

// Add this check here
        if (newSchedules.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "No billing schedule was generated for Fixed Price configuration "
                            + configuration.getBillingConfigurationId()
                            + ". Please verify Effective From, Effective To and Billing Frequency."
            );
        }

        try {
            billingScheduleRepository.saveAll(newSchedules);

            log.info(
                    "Generated {} occurrences for Fixed Price configuration {} from {} to {}",
                    newSchedules.size(),
                    configuration.getBillingConfigurationId(),
                    generationStartDate,
                    effectiveTo
            );
            log.info("Generated {} occurrences for Fixed Price configuration {} from {} to {}",
                    newSchedules.size(), configuration.getBillingConfigurationId(), generationStartDate, effectiveTo);
        } catch (DataIntegrityViolationException e) {
            log.error(
                    "Data integrity violation while saving billing schedules for Fixed Price configuration {}. " +
                    "Generation range: {} to {}. Occurrences to save: {}. Root cause: {}. This may indicate duplicate occurrences.",
                    configuration.getBillingConfigurationId(),
                    generationStartDate,
                    effectiveTo,
                    newSchedules.size(),
                    e.getMostSpecificCause().getMessage(),
                    e
            );
            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to save billing schedules for Fixed Price configuration {}. " +
                    "Generation range: {} to {}. Occurrences to save: {}. Root cause: {}",
                    configuration.getBillingConfigurationId(),
                    generationStartDate,
                    effectiveTo,
                    newSchedules.size(),
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }

    private void generateRecurringOccurrences(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            BillingFrequencyMaster frequency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        // For Recurring billing, require a positive period (at least one day)
        if (effectiveFrom.isAfter(effectiveTo) || effectiveFrom.isEqual(effectiveTo)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Recurring billing requires a positive period. Recurring Start Date must be before Recurring End Date.");
        }

        List<BillingSchedule> existingSchedules = billingScheduleRepository
                .findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurring);

        List<BillingSchedule> processedSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED ||
                        s.getPeriodStatus() == BillingPeriodStatus.INVOICED)
                .toList();

        LocalDate generationStartDate = effectiveFrom;
        if (!processedSchedules.isEmpty()) {
            LocalDate lastProcessedDate = processedSchedules.stream()
                    .map(BillingSchedule::getPeriodEndDate)
                    .max(LocalDate::compareTo)
                    .orElse(effectiveFrom);
            generationStartDate = lastProcessedDate.plusDays(1);
        }

        if (generationStartDate.isAfter(effectiveTo)) {
            log.info("No new occurrences to generate for Recurring configuration {}", recurring.getRecurringConfigurationId());
            return;
        }

        List<BillingSchedule> newSchedules = calculateAndCreateSchedules(
                configuration,
                recurring,
                frequency,
                generationStartDate,
                effectiveTo,
                recurring.getContractValue(),
                BillingScheduleType.PRIMARY,
                existingSchedules.size() + 1
        );

        try {
            billingScheduleRepository.saveAll(newSchedules);

            log.info(
                    "Generated {} occurrences for Recurring configuration {} from {} to {}",
                    newSchedules.size(),
                    recurring.getRecurringConfigurationId(),
                    generationStartDate,
                    effectiveTo
            );

        } catch (DataIntegrityViolationException e) {

            log.error(
                    "Data integrity violation while saving billing schedules for Recurring configuration {}. " +
                    "Generation range: {} to {}. Occurrences to save: {}. Root cause: {}. This may indicate duplicate occurrences.",
                    recurring.getRecurringConfigurationId(),
                    generationStartDate,
                    effectiveTo,
                    newSchedules.size(),
                    e.getMostSpecificCause().getMessage(),
                    e
            );

            throw e;
        } catch (Exception e) {
            log.error(
                    "Failed to save billing schedules for Recurring configuration {}. " +
                    "Generation range: {} to {}. Occurrences to save: {}. Root cause: {}",
                    recurring.getRecurringConfigurationId(),
                    generationStartDate,
                    effectiveTo,
                    newSchedules.size(),
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }

    private List<BillingSchedule> calculateAndCreateSchedules(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            BillingFrequencyMaster frequency,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalContractValue,
            BillingScheduleType scheduleType,
            int startPeriodNumber) {

        List<BillingSchedule> schedules = new ArrayList<>();
        LocalDate currentStart = startDate;
        int periodNumber = startPeriodNumber;

        // Determine if this is Fixed Price (no recurring config) or Recurring billing
        boolean isFixedPrice = (recurring == null);

        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = calculatePeriodEndDate(currentStart, frequency);

            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            boolean isPartial = !calculatePeriodEndDate(currentStart, frequency).isEqual(currentEnd);
            boolean isLastPeriod = currentEnd.isEqual(endDate);

            BigDecimal periodAmount;
            if (isFixedPrice) {
                // For Fixed Price: equal distribution across all occurrences
                // We'll calculate the total number of periods first, then distribute equally
                // This is handled by counting periods before the loop
                periodAmount = calculateFixedPricePeriodAmount(
                        totalContractValue, 
                        startDate, 
                        endDate, 
                        frequency, 
                        currentStart, 
                        isLastPeriod);
            } else {
                // For Recurring: day-based proration (existing behavior)
                long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
                long periodDays = ChronoUnit.DAYS.between(currentStart, currentEnd) + 1;
                
                if (isLastPeriod) {
                    BigDecimal allocatedAmount = schedules.stream()
                            .map(BillingSchedule::getBillingAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    periodAmount = totalContractValue.subtract(allocatedAmount).setScale(2, RoundingMode.HALF_EVEN);
                } else {
                    periodAmount = totalContractValue
                            .multiply(BigDecimal.valueOf(periodDays))
                            .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_EVEN);
                }
            }

            BillingSchedule schedule = BillingSchedule.builder()
                    .billingConfiguration(configuration)
                    .recurringConfiguration(recurring)
                    .periodNumber(periodNumber)
                    .periodStartDate(currentStart)
                    .periodEndDate(currentEnd)
                    .billingDate(currentEnd)
                    .billingAmount(periodAmount)
                    .scheduleType(scheduleType)
                    .isPartialPeriod(isPartial)
                    .periodStatus(BillingPeriodStatus.SCHEDULED)
                    .taxStatus(BillingPeriodStatus.PENDING)
                    .isInvoiced(false)
                    .isActive(true)
                    .build();

            schedules.add(schedule);

            currentStart = currentEnd.plusDays(1);
            periodNumber++;
        }

        return schedules;
    }

    /**
     * Calculates the period amount for Fixed Price billing using equal distribution.
     * The final period absorbs any rounding remainder to ensure total equals contract value.
     * 
     * @param totalContractValue Total contract value
     * @param startDate Overall start date
     * @param endDate Overall end date
     * @param frequency Billing frequency
     * @param currentPeriodStart Start date of current period
     * @param isLastPeriod Whether this is the last period
     * @return Amount for this period
     */
    private BigDecimal calculateFixedPricePeriodAmount(
            BigDecimal totalContractValue,
            LocalDate startDate,
            LocalDate endDate,
            BillingFrequencyMaster frequency,
            LocalDate currentPeriodStart,
            boolean isLastPeriod) {
        
        // Count total number of periods
        int totalPeriods = countPeriods(startDate, endDate, frequency);
        
        if (totalPeriods <= 0) {
            return totalContractValue;
        }

        // Calculate equal distribution
        BigDecimal baseAmount = totalContractValue
                .divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_EVEN);
        
        if (isLastPeriod) {
            // Last period gets the remainder to ensure total matches exactly
            BigDecimal allocatedAmount = baseAmount.multiply(BigDecimal.valueOf(totalPeriods - 1));
            return totalContractValue.subtract(allocatedAmount).setScale(2, RoundingMode.HALF_EVEN);
        }
        
        return baseAmount;
    }

    /**
     * Counts the number of billing periods for a given date range and frequency.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @param frequency Billing frequency
     * @return Number of periods
     */
    private int countPeriods(LocalDate startDate, LocalDate endDate, BillingFrequencyMaster frequency) {
        int count = 0;
        LocalDate currentStart = startDate;
        
        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = calculatePeriodEndDate(currentStart, frequency);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }
            count++;
            currentStart = currentEnd.plusDays(1);
        }
        
        return count;
    }

    /**
     * Infers the frequency ID from the period duration in months.
     * This is used to detect frequency changes by comparing existing schedule periods.
     * 
     * @param monthsBetween Number of months between period start and end
     * @param configuration Billing configuration (for accessing frequency repository)
     * @return The frequency ID that matches the duration, or null if not found
     */
    private UUID inferFrequencyIdFromDuration(long monthsBetween, BillingConfiguration configuration) {
        // Get all active frequencies
        List<BillingFrequencyMaster> frequencies = billingFrequencyRepository.findByIsActiveTrueOrderByBillingFrequencyNameAsc();
        
        for (BillingFrequencyMaster frequency : frequencies) {
            if (frequency.getDurationUnit() == RenewalDurationUnit.MONTHS) {
                // Check if the duration matches
                if (frequency.getDurationValue() != null && frequency.getDurationValue() == monthsBetween) {
                    return frequency.getBillingFrequencyId();
                }
            }
        }
        
        return null;
    }

    LocalDate calculatePeriodEndDate(LocalDate startDate, BillingFrequencyMaster frequency) {
        RenewalDurationUnit unit = frequency.getDurationUnit();
        Integer durationValue = frequency.getDurationValue();

        return switch (unit) {
            case DAYS -> startDate.plusDays(durationValue - 1);
            case MONTHS -> startDate.plusMonths(durationValue).minusDays(1);
            case YEARS -> startDate.plusYears(durationValue).minusDays(1);
        };
    }

    /**
     * Checks if a billing frequency is eligible for the given date range.
     * A frequency is eligible if at least one complete billing cycle fits within the range.
     * 
     * @param frequency The billing frequency to check
     * @param effectiveFrom The start date of the billing period
     * @param effectiveTo The end date of the billing period
     * @return true if the frequency is eligible, false otherwise
     */
    private boolean isFrequencyEligible(BillingFrequencyMaster frequency, LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (frequency == null || effectiveFrom == null || effectiveTo == null) {
            return false;
        }

        String frequencyName = frequency.getBillingFrequencyName() != null 
                ? frequency.getBillingFrequencyName().trim().toLowerCase() 
                : "";

        // One-Time is always eligible as long as dates are valid
        if (frequencyName.equals("one-time")) {
            return true;
        }

        RenewalDurationUnit unit = frequency.getDurationUnit();
        Integer durationValue = frequency.getDurationValue();

        if (unit == null || durationValue == null || durationValue <= 0) {
            return false;
        }

        // Calculate the end of the first complete cycle
        LocalDate cycleEnd;
        switch (unit) {
            case DAYS -> cycleEnd = effectiveFrom.plusDays(durationValue - 1);
            case MONTHS -> cycleEnd = effectiveFrom.plusMonths(durationValue).minusDays(1);
            case YEARS -> cycleEnd = effectiveFrom.plusYears(durationValue).minusDays(1);
            default -> cycleEnd = effectiveFrom;
        }

        // At least one complete cycle exists if cycleEnd <= effectiveTo
        return !cycleEnd.isAfter(effectiveTo);
    }

    private void reconcileFixedPriceOccurrences(BillingConfiguration configuration) {
        BillingFixedPriceConfiguration fixedPrice = billingFixedPriceRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElse(null);

        if (fixedPrice == null) {
            return;
        }

        List<BillingSchedule> existingSchedules = billingScheduleRepository
                .findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(configuration);

        ConfigurationChangeDetector changeDetector = detectFixedPriceChanges(configuration, fixedPrice, existingSchedules);

        if (changeDetector.hasEffectiveFromChanged()) {
            handleEffectiveFromChangeForFixedPrice(configuration, fixedPrice, existingSchedules, changeDetector);
        } else if (changeDetector.hasFrequencyChanged()) {
            handleFrequencyChangeForFixedPrice(configuration, fixedPrice, existingSchedules, changeDetector);
        } else if (changeDetector.hasAmountChanged()) {
            handleAmountChangeForFixedPrice(configuration, fixedPrice, existingSchedules, changeDetector);
        } else {
            handleEffectiveToChangeForFixedPrice(configuration, fixedPrice, existingSchedules);
        }
    }

    private void reconcileRecurringOccurrences(BillingConfiguration configuration) {
        BillingRecurringConfiguration recurring = billingRecurringConfigurationRepository
                .findByBillingConfigurationAndIsActiveTrue(configuration)
                .orElse(null);

        if (recurring == null) {
            return;
        }

        List<BillingSchedule> existingSchedules = billingScheduleRepository
                .findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurring);

        ConfigurationChangeDetector changeDetector = detectRecurringChanges(configuration, recurring, existingSchedules);

        if (changeDetector.hasEffectiveFromChanged()) {
            handleEffectiveFromChangeForRecurring(configuration, recurring, existingSchedules, changeDetector);
        } else if (changeDetector.hasFrequencyChanged()) {
            handleFrequencyChangeForRecurring(configuration, recurring, existingSchedules, changeDetector);
        } else if (changeDetector.hasAmountChanged()) {
            handleAmountChangeForRecurring(configuration, recurring, existingSchedules, changeDetector);
        } else {
            handleEffectiveToChangeForRecurring(configuration, recurring, existingSchedules);
        }
    }

    public void transitionScheduledToTaxPending() {
        LocalDate today = LocalDate.now();

        List<BillingSchedule> scheduledOccurrences = billingScheduleRepository
                .findByBillingDateLessThanEqualAndTaxStatusAndIsActiveTrue(today, BillingPeriodStatus.PENDING);

        int successCount = 0;
        int failureCount = 0;

        for (BillingSchedule schedule : scheduledOccurrences) {
            try {
                billingOccurrenceTransactionService.transitionSingleOccurrenceToTaxPending(schedule);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                UUID configId = schedule.getBillingConfiguration() != null 
                        ? schedule.getBillingConfiguration().getBillingConfigurationId() 
                        : (schedule.getRecurringConfiguration() != null 
                                ? schedule.getRecurringConfiguration().getRecurringConfigurationId() 
                                : null);
                log.error("Failed to transition occurrence {} (config: {}) to TAX_PENDING: {}", 
                        schedule.getBillingScheduleId(), configId, e.getMessage(), e);
                // Continue processing other occurrences - one failure should not prevent others
            }
        }

        log.info("Completed transition of SCHEDULED occurrences to TAX_PENDING: {} succeeded, {} failed", 
                successCount, failureCount);
    }

    private static class ConfigurationChangeDetector {
        private boolean effectiveFromChanged;
        private boolean effectiveToChanged;
        private boolean frequencyChanged;
        private boolean amountChanged;
        private LocalDate oldEffectiveFrom;
        private LocalDate newEffectiveFrom;
        private LocalDate oldEffectiveTo;
        private LocalDate newEffectiveTo;
        private UUID oldFrequencyId;
        private UUID newFrequencyId;
        private BigDecimal oldAmount;
        private BigDecimal newAmount;

        public boolean hasEffectiveFromChanged() { return effectiveFromChanged; }
        public boolean hasEffectiveToChanged() { return effectiveToChanged; }
        public boolean hasFrequencyChanged() { return frequencyChanged; }
        public boolean hasAmountChanged() { return amountChanged; }
        public LocalDate getOldEffectiveFrom() { return oldEffectiveFrom; }
        public LocalDate getNewEffectiveFrom() { return newEffectiveFrom; }
        public LocalDate getOldEffectiveTo() { return oldEffectiveTo; }
        public LocalDate getNewEffectiveTo() { return newEffectiveTo; }
        public UUID getOldFrequencyId() { return oldFrequencyId; }
        public UUID getNewFrequencyId() { return newFrequencyId; }
        public BigDecimal getOldAmount() { return oldAmount; }
        public BigDecimal getNewAmount() { return newAmount; }

        public void setEffectiveFromChanged(boolean changed) { this.effectiveFromChanged = changed; }
        public void setEffectiveToChanged(boolean changed) { this.effectiveToChanged = changed; }
        public void setFrequencyChanged(boolean changed) { this.frequencyChanged = changed; }
        public void setAmountChanged(boolean changed) { this.amountChanged = changed; }
        public void setOldEffectiveFrom(LocalDate date) { this.oldEffectiveFrom = date; }
        public void setNewEffectiveFrom(LocalDate date) { this.newEffectiveFrom = date; }
        public void setOldEffectiveTo(LocalDate date) { this.oldEffectiveTo = date; }
        public void setNewEffectiveTo(LocalDate date) { this.newEffectiveTo = date; }
        public void setOldFrequencyId(UUID id) { this.oldFrequencyId = id; }
        public void setNewFrequencyId(UUID id) { this.newFrequencyId = id; }
        public void setOldAmount(BigDecimal amount) { this.oldAmount = amount; }
        public void setNewAmount(BigDecimal amount) { this.newAmount = amount; }
    }

    private ConfigurationChangeDetector detectFixedPriceChanges(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            List<BillingSchedule> existingSchedules) {
        
        ConfigurationChangeDetector detector = new ConfigurationChangeDetector();
        
        LocalDate currentEffectiveFrom = fixedPrice.getEffectiveFrom() != null
                ? fixedPrice.getEffectiveFrom()
                : configuration.getEffectiveFrom();
        
        LocalDate currentEffectiveTo = fixedPrice.getEffectiveTo() != null
                ? fixedPrice.getEffectiveTo()
                : configuration.getEffectiveTo();
        
        BillingFrequencyMaster currentFrequency = configuration.getBillingFrequency();
        BigDecimal currentAmount = fixedPrice.getContractValue();
        
        if (!existingSchedules.isEmpty()) {
            LocalDate oldEffectiveFrom = existingSchedules.stream()
                    .map(BillingSchedule::getPeriodStartDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            
            LocalDate oldEffectiveTo = existingSchedules.stream()
                    .map(BillingSchedule::getPeriodEndDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            
            detector.setOldEffectiveFrom(oldEffectiveFrom);
            detector.setNewEffectiveFrom(currentEffectiveFrom);
            detector.setEffectiveFromChanged(oldEffectiveFrom != null && !oldEffectiveFrom.equals(currentEffectiveFrom));
            
            detector.setOldEffectiveTo(oldEffectiveTo);
            detector.setNewEffectiveTo(currentEffectiveTo);
            detector.setEffectiveToChanged(oldEffectiveTo != null && !oldEffectiveTo.equals(currentEffectiveTo));
            
            // Detect frequency change by inferring old frequency from existing schedule periods
            // Compare the period duration of existing schedules with the current frequency
            if (currentFrequency != null && !existingSchedules.isEmpty()) {
                UUID newFrequencyId = currentFrequency.getBillingFrequencyId();
                detector.setNewFrequencyId(newFrequencyId);
                
                // Infer old frequency only from full (non-partial) periods
                // Filter out partial periods to avoid incorrect inference from final partial occurrences
                List<BillingSchedule> fullPeriodSchedules = existingSchedules.stream()
                        .filter(s -> s.getIsPartialPeriod() == null || !s.getIsPartialPeriod())
                        .toList();
                
                UUID oldFrequencyId = null;
                
                if (!fullPeriodSchedules.isEmpty()) {
                    // Prefer SCHEDULED full occurrences for most reliable inference
                    List<BillingSchedule> scheduledFullPeriods = fullPeriodSchedules.stream()
                            .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                            .toList();
                    
                    BillingSchedule scheduleToUse = !scheduledFullPeriods.isEmpty() 
                            ? scheduledFullPeriods.get(0) 
                            : fullPeriodSchedules.get(0);
                    
                    LocalDate periodStart = scheduleToUse.getPeriodStartDate();
                    LocalDate periodEnd = scheduleToUse.getPeriodEndDate();
                    
                    // Calculate the duration in months using calendar-aware semantics
                    long monthsBetween = ChronoUnit.MONTHS.between(periodStart, periodEnd);
                    
                    // Map duration to expected frequency using BillingFrequencyMaster properties
                    oldFrequencyId = inferFrequencyIdFromDuration(monthsBetween, configuration);
                    
                    // If multiple full periods exist, verify consistency
                    if (fullPeriodSchedules.size() > 1) {
                        UUID frequencyIdForComparison = oldFrequencyId;
                        boolean consistent = fullPeriodSchedules.stream()
                                .allMatch(s -> {
                                    long months = ChronoUnit.MONTHS.between(
                                            s.getPeriodStartDate(), 
                                            s.getPeriodEndDate()
                                    );
                                    UUID freqId = inferFrequencyIdFromDuration(months, configuration);
                                    return frequencyIdForComparison.equals(freqId);
                                });
                        
                        if (!consistent) {
                            // Inconsistent durations - cannot reliably infer frequency
                            oldFrequencyId = null;
                        }
                    }
                }
                // If only partial/non-inferable occurrences exist, oldFrequencyId remains null
                // This is handled safely by the reconciliation architecture
                
                detector.setOldFrequencyId(oldFrequencyId);
                detector.setFrequencyChanged(oldFrequencyId != null && !oldFrequencyId.equals(newFrequencyId));
            }
            
            // Detect amount change by comparing with existing schedules
            BigDecimal oldAmount = existingSchedules.get(0).getBillingAmount();
            if (oldAmount != null && currentAmount != null) {
                detector.setOldAmount(oldAmount);
                detector.setNewAmount(currentAmount);
                detector.setAmountChanged(oldAmount.compareTo(currentAmount) != 0);
            }
        } else {
            detector.setNewAmount(currentAmount);
        }
        
        if (currentFrequency != null) {
            detector.setNewFrequencyId(currentFrequency.getBillingFrequencyId());
        }
        
        return detector;
    }

    private ConfigurationChangeDetector detectRecurringChanges(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            List<BillingSchedule> existingSchedules) {
        
        ConfigurationChangeDetector detector = new ConfigurationChangeDetector();
        
        LocalDate currentEffectiveFrom = recurring.getRecurringStartDate() != null
                ? recurring.getRecurringStartDate()
                : configuration.getEffectiveFrom();
        
        LocalDate currentEffectiveTo = recurring.getRecurringEndDate() != null
                ? recurring.getRecurringEndDate()
                : configuration.getEffectiveTo();
        
        BillingFrequencyMaster currentFrequency = recurring.getBillingFrequency() != null
                ? recurring.getBillingFrequency()
                : configuration.getBillingFrequency();
        
        BigDecimal currentAmount = recurring.getContractValue();
        
        if (!existingSchedules.isEmpty()) {
            LocalDate oldEffectiveFrom = existingSchedules.stream()
                    .map(BillingSchedule::getPeriodStartDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            
            LocalDate oldEffectiveTo = existingSchedules.stream()
                    .map(BillingSchedule::getPeriodEndDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            
            detector.setOldEffectiveFrom(oldEffectiveFrom);
            detector.setNewEffectiveFrom(currentEffectiveFrom);
            detector.setEffectiveFromChanged(oldEffectiveFrom != null && !oldEffectiveFrom.equals(currentEffectiveFrom));
            
            detector.setOldEffectiveTo(oldEffectiveTo);
            detector.setNewEffectiveTo(currentEffectiveTo);
            detector.setEffectiveToChanged(oldEffectiveTo != null && !oldEffectiveTo.equals(currentEffectiveTo));
            
            // Detect frequency change by comparing frequency IDs directly
            // This correctly distinguishes calendar-based frequencies (Monthly, Quarterly, etc.)
            // without using approximate day conversions
            if (currentFrequency != null && !existingSchedules.isEmpty()) {
                UUID oldFrequencyId = recurring.getBillingFrequency() != null 
                        ? recurring.getBillingFrequency().getBillingFrequencyId() 
                        : null;
                UUID newFrequencyId = currentFrequency.getBillingFrequencyId();
                detector.setOldFrequencyId(oldFrequencyId);
                detector.setNewFrequencyId(newFrequencyId);
                detector.setFrequencyChanged(oldFrequencyId != null && !oldFrequencyId.equals(newFrequencyId));
            }
            
            // Detect amount change by comparing with existing schedules
            BigDecimal oldAmount = existingSchedules.get(0).getBillingAmount();
            if (oldAmount != null && currentAmount != null) {
                detector.setOldAmount(oldAmount);
                detector.setNewAmount(currentAmount);
                detector.setAmountChanged(oldAmount.compareTo(currentAmount) != 0);
            }
        } else {
            detector.setNewAmount(currentAmount);
        }
        
        if (currentFrequency != null) {
            detector.setNewFrequencyId(currentFrequency.getBillingFrequencyId());
        }
        
        return detector;
    }

    private void handleEffectiveFromChangeForFixedPrice(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            List<BillingSchedule> existingSchedules,
            ConfigurationChangeDetector detector) {
        
        LocalDate newEffectiveFrom = detector.getNewEffectiveFrom();
        LocalDate oldEffectiveFrom = detector.getOldEffectiveFrom();
        
        if (newEffectiveFrom == null || oldEffectiveFrom == null) {
            log.warn("Cannot determine effective from change for configuration {}", configuration.getBillingConfigurationId());
            return;
        }
        
        List<BillingSchedule> scheduledBeforeNewStart = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                .filter(s -> s.getPeriodEndDate().isBefore(newEffectiveFrom))
                .toList();
        
        if (!scheduledBeforeNewStart.isEmpty()) {
            billingScheduleRepository.deleteAll(scheduledBeforeNewStart);
            log.info("Deleted {} scheduled occurrences before new effective from {} for Fixed Price configuration",
                    scheduledBeforeNewStart.size(), newEffectiveFrom);
        }
        
        List<BillingSchedule> taxPendingOrCalculatedBeforeNewStart = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.TAX_PENDING ||
                           s.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED)
                .filter(s -> s.getPeriodEndDate().isBefore(newEffectiveFrom))
                .toList();
        
        if (!taxPendingOrCalculatedBeforeNewStart.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Cannot change Effective From to " + newEffectiveFrom + 
                    " because it conflicts with " + taxPendingOrCalculatedBeforeNewStart.size() +
                    " processed billing occurrences. Please resolve these occurrences first.");
        }
        
        generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
    }

    private void handleEffectiveFromChangeForRecurring(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            List<BillingSchedule> existingSchedules,
            ConfigurationChangeDetector detector) {
        
        LocalDate newEffectiveFrom = detector.getNewEffectiveFrom();
        LocalDate oldEffectiveFrom = detector.getOldEffectiveFrom();
        
        if (newEffectiveFrom == null || oldEffectiveFrom == null) {
            log.warn("Cannot determine effective from change for configuration {}", configuration.getBillingConfigurationId());
            return;
        }
        
        List<BillingSchedule> scheduledBeforeNewStart = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                .filter(s -> s.getPeriodEndDate().isBefore(newEffectiveFrom))
                .toList();
        
        if (!scheduledBeforeNewStart.isEmpty()) {
            billingScheduleRepository.deleteAll(scheduledBeforeNewStart);
            log.info("Deleted {} scheduled occurrences before new effective from {} for Recurring configuration",
                    scheduledBeforeNewStart.size(), newEffectiveFrom);
        }
        
        List<BillingSchedule> taxPendingOrCalculatedBeforeNewStart = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.TAX_PENDING ||
                           s.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED)
                .filter(s -> s.getPeriodEndDate().isBefore(newEffectiveFrom))
                .toList();
        
        if (!taxPendingOrCalculatedBeforeNewStart.isEmpty()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Cannot change Effective From to " + newEffectiveFrom + 
                    " because it conflicts with " + taxPendingOrCalculatedBeforeNewStart.size() +
                    " processed billing occurrences. Please resolve these occurrences first.");
        }
        
        generateOccurrencesForRecurring(configuration.getBillingConfigurationId());
    }

    private void handleFrequencyChangeForFixedPrice(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            List<BillingSchedule> existingSchedules,
            ConfigurationChangeDetector detector) {
        
        List<BillingSchedule> scheduledSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                .toList();
        
        if (!scheduledSchedules.isEmpty()) {
            billingScheduleRepository.deleteAll(scheduledSchedules);
            log.info("Deleted {} scheduled occurrences for frequency change in Fixed Price configuration",
                    scheduledSchedules.size());
        }
        
        generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
    }

    private void handleFrequencyChangeForRecurring(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            List<BillingSchedule> existingSchedules,
            ConfigurationChangeDetector detector) {
        
        List<BillingSchedule> scheduledSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                .toList();
        
        if (!scheduledSchedules.isEmpty()) {
            billingScheduleRepository.deleteAll(scheduledSchedules);
            log.info("Deleted {} scheduled occurrences for frequency change in Recurring configuration",
                    scheduledSchedules.size());
        }
        
        generateOccurrencesForRecurring(configuration.getBillingConfigurationId());
    }

    private void handleAmountChangeForFixedPrice(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            List<BillingSchedule> existingSchedules,
            ConfigurationChangeDetector detector) {
        
        BigDecimal newContractValue = detector.getNewAmount();
        
        // Filter eligible occurrences for amount update (SCHEDULED and TAX_PENDING only)
        List<BillingSchedule> eligibleSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED || 
                           s.getPeriodStatus() == BillingPeriodStatus.TAX_PENDING)
                .toList();
        
        if (eligibleSchedules.isEmpty()) {
            log.info("No eligible SCHEDULED or TAX_PENDING occurrences to update for Fixed Price configuration {}", 
                    configuration.getBillingConfigurationId());
            return;
        }
        
        // Get the effective date range from the configuration
        LocalDate effectiveFrom = fixedPrice.getEffectiveFrom() != null 
                ? fixedPrice.getEffectiveFrom() 
                : configuration.getEffectiveFrom();
        LocalDate effectiveTo = fixedPrice.getEffectiveTo() != null 
                ? fixedPrice.getEffectiveTo() 
                : configuration.getEffectiveTo();
        
        BillingFrequencyMaster frequency = configuration.getBillingFrequency();
        
        // Distribute the new contract value equally across eligible occurrences
        int totalEligiblePeriods = eligibleSchedules.size();
        BigDecimal baseAmount = newContractValue
                .divide(BigDecimal.valueOf(totalEligiblePeriods), 2, RoundingMode.HALF_EVEN);
        
        for (int i = 0; i < eligibleSchedules.size(); i++) {
            BillingSchedule schedule = eligibleSchedules.get(i);
            boolean isLastEligible = (i == eligibleSchedules.size() - 1);
            
            BigDecimal periodAmount;
            if (isLastEligible) {
                // Last eligible occurrence gets the remainder to ensure total matches exactly
                BigDecimal allocatedAmount = baseAmount.multiply(BigDecimal.valueOf(totalEligiblePeriods - 1));
                periodAmount = newContractValue.subtract(allocatedAmount).setScale(2, RoundingMode.HALF_EVEN);
            } else {
                periodAmount = baseAmount;
            }
            
            if (schedule.getPeriodStatus() == BillingPeriodStatus.SCHEDULED) {
                schedule.setBillingAmount(periodAmount);
                billingScheduleRepository.save(schedule);
                log.info("Updated billing amount for SCHEDULED occurrence {} to {}", 
                        schedule.getBillingScheduleId(), periodAmount);
            } else if (schedule.getPeriodStatus() == BillingPeriodStatus.TAX_PENDING) {
                // First update the billing schedule amount
                schedule.setBillingAmount(periodAmount);
                billingScheduleRepository.save(schedule);
                
                // Then delete the tax calculation - this ensures schedule is updated before tax is invalidated
                taxCalculationRepository.deleteByBillingScheduleId(schedule.getBillingScheduleId());
                log.info("Updated billing amount for TAX_PENDING occurrence {} to {} and invalidated tax", 
                        schedule.getBillingScheduleId(), periodAmount);
            }
        }
        
        // Check for TAX_CALCULATED or INVOICED occurrences and throw error if found
        for (BillingSchedule schedule : existingSchedules) {
            if (schedule.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Cannot change billing amount because occurrence " + schedule.getBillingScheduleId() +
                        " is already TAX_CALCULATED. Please recalculate tax first or contact support.");
            } else if (schedule.getIsInvoiced()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Cannot change billing amount because occurrence " + schedule.getBillingScheduleId() +
                        " is already INVOICED. Historical invoiced amounts cannot be modified.");
            }
        }
    }

    private void handleAmountChangeForRecurring(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            List<BillingSchedule> existingSchedules,
            ConfigurationChangeDetector detector) {
        
        BigDecimal newAmount = detector.getNewAmount();
        
        for (BillingSchedule schedule : existingSchedules) {
            if (schedule.getPeriodStatus() == BillingPeriodStatus.SCHEDULED) {
                schedule.setBillingAmount(newAmount);
                billingScheduleRepository.save(schedule);
                log.info("Updated billing amount for SCHEDULED occurrence {} to {}", 
                        schedule.getBillingScheduleId(), newAmount);
            } else if (schedule.getPeriodStatus() == BillingPeriodStatus.TAX_PENDING) {
                // First update the billing schedule amount
                schedule.setBillingAmount(newAmount);
                billingScheduleRepository.save(schedule);
                
                // Then delete the tax calculation - this ensures schedule is updated before tax is invalidated
                // If tax deletion fails, the schedule still has the new amount and can be recalculated
                taxCalculationRepository.deleteByBillingScheduleId(schedule.getBillingScheduleId());
                log.info("Updated billing amount for TAX_PENDING occurrence {} to {} and invalidated tax", 
                        schedule.getBillingScheduleId(), newAmount);
            } else if (schedule.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Cannot change billing amount because occurrence " + schedule.getBillingScheduleId() +
                        " is already TAX_CALCULATED. Please recalculate tax first or contact support.");
            } else if (schedule.getIsInvoiced()) {
                throw new GlobalExceptionHandler.ValidationException(
                        "Cannot change billing amount because occurrence " + schedule.getBillingScheduleId() +
                        " is already INVOICED. Historical invoiced amounts cannot be modified.");
            }
        }
    }

    private void handleEffectiveToChangeForFixedPrice(
            BillingConfiguration configuration,
            BillingFixedPriceConfiguration fixedPrice,
            List<BillingSchedule> existingSchedules) {
        
        List<BillingSchedule> scheduledSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                .toList();
        
        if (!scheduledSchedules.isEmpty()) {
            billingScheduleRepository.deleteAll(scheduledSchedules);
            log.info("Deleted {} scheduled occurrences for Effective To change in Fixed Price configuration",
                    scheduledSchedules.size());
        }
        
        generateOccurrencesForFixedPrice(configuration.getBillingConfigurationId());
    }

    private void handleEffectiveToChangeForRecurring(
            BillingConfiguration configuration,
            BillingRecurringConfiguration recurring,
            List<BillingSchedule> existingSchedules) {
        
        List<BillingSchedule> scheduledSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED)
                .toList();
        
        if (!scheduledSchedules.isEmpty()) {
            billingScheduleRepository.deleteAll(scheduledSchedules);
            log.info("Deleted {} scheduled occurrences for Effective To change in Recurring configuration",
                    scheduledSchedules.size());
        }
        
        generateOccurrencesForRecurring(configuration.getBillingConfigurationId());
    }
}
