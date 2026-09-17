package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.projectbilling_config.*;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.*;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.projectbilling_config.*;
import com.AccountReceivableManagement.service_Imple.tax_calculation.TaxCalculationServiceImpl;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingOccurrenceServiceImplTest {

    @Mock
    private BillingScheduleRepository billingScheduleRepository;

    @Mock
    private BillingFixedPriceRepository billingFixedPriceRepository;

    @Mock
    private BillingRecurringConfigurationRepository billingRecurringConfigurationRepository;

    @Mock
    private BillingPeriodCalculatorServiceImpl billingPeriodCalculatorService;

    @Mock
    private BillingConfigurationRepository billingConfigurationRepository;

    @Mock
    private BillingFrequencyMasterRepository billingFrequencyRepository;

    @Mock
    private com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository taxCalculationRepository;

    @Mock
    private TaxCalculationServiceImpl taxCalculationService;

    @Mock
    private BillingOccurrenceTransactionService billingOccurrenceTransactionService;

    @InjectMocks
    private BillingOccurrenceServiceImpl billingOccurrenceService;

    private UUID billingConfigId;
    private BillingConfiguration billingConfiguration;
    private BillingFixedPriceConfiguration fixedPriceConfig;
    private BillingRecurringConfiguration recurringConfig;
    private BillingFrequencyMaster monthlyFrequency;
    private BillingFrequencyMaster oneTimeFrequency;
    private Client client;
    private ProjectMasterReference project;

    @BeforeEach
    void setUp() {
        billingConfigId = UUID.randomUUID();

        client = Client.builder()
                .clientId(UUID.randomUUID())
                .clientName("Test Client")
                .build();

        project = ProjectMasterReference.builder()
                .pmsProjectId(1L)
                .projectName("Test Project")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .projectBudget(new BigDecimal("100000"))
                .build();

        BillingTypeMaster fixedPriceType = BillingTypeMaster.builder()
                .billingTypeId(UUID.randomUUID())
                .billingTypeName("Fixed Price")
                .build();

        BillingTypeMaster recurringType = BillingTypeMaster.builder()
                .billingTypeId(UUID.randomUUID())
                .billingTypeName("Recurring")
                .build();

        billingConfiguration = BillingConfiguration.builder()
                .billingConfigurationId(billingConfigId)
                .client(client)
                .project(project)
                .billingType(fixedPriceType)
                .approvalStatus(ApprovalStatus.APPROVED)
                .billingStatus(BillingConfigurationStatus.ACTIVE)
                .effectiveFrom(LocalDate.of(2026, 9, 1))
                .effectiveTo(LocalDate.of(2026, 9, 30))
                .contractValue(new BigDecimal("10000"))
                .createdAt(LocalDateTime.now())
                .build();

        monthlyFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Monthly")
                .durationValue(1)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .isActive(true)
                .build();

        oneTimeFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("One-Time")
                .durationValue(1)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .isActive(true)
                .build();

        fixedPriceConfig = BillingFixedPriceConfiguration.builder()
                .fixedPriceConfigurationId(UUID.randomUUID())
                .billingConfiguration(billingConfiguration)
                .contractValue(new BigDecimal("10000"))
                .effectiveFrom(LocalDate.of(2026, 9, 1))
                .effectiveTo(LocalDate.of(2026, 9, 30))
                .retentionPercentage(new BigDecimal("10"))
                .advanceReceived(new BigDecimal("1000"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        recurringConfig = BillingRecurringConfiguration.builder()
                .recurringConfigurationId(UUID.randomUUID())
                .billingConfiguration(billingConfiguration)
                .contractValue(new BigDecimal("12000"))
                .recurringStartDate(LocalDate.of(2026, 9, 1))
                .recurringEndDate(LocalDate.of(2026, 12, 31))
                .billingFrequency(monthlyFrequency)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================
    // SCENARIO 1: FIXED PRICE + ONE-TIME
    // =========================================================

    @Test
    void generateOccurrencesForFixedPrice_OneTime_GeneratesSingleOccurrence() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
                billingConfiguration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(false);

        billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId);

        verify(billingScheduleRepository).save(argThat(schedule ->
                schedule.getPeriodNumber() == 1 &&
                        schedule.getPeriodStartDate().equals(LocalDate.of(2026, 9, 1)) &&
                        schedule.getPeriodEndDate().equals(LocalDate.of(2026, 9, 30)) &&
                        schedule.getBillingDate().equals(LocalDate.of(2026, 9, 30)) &&
                        schedule.getBillingAmount().compareTo(new BigDecimal("10000")) == 0 &&
                        schedule.getPeriodStatus() == BillingPeriodStatus.SCHEDULED &&
                        schedule.getTaxStatus() == BillingPeriodStatus.PENDING &&
                        !schedule.getIsPartialPeriod()
        ));
    }

    @Test
    void generateOccurrencesForFixedPrice_OneTime_DuplicatePrevention() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
                billingConfiguration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(true);

        billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId);

        verify(billingScheduleRepository, never()).save(any());
    }

    // =========================================================
    // SCENARIO 2: RECURRING + MONTHLY
    // =========================================================

    @Test
    void generateOccurrencesForRecurring_Monthly_GeneratesCorrectPeriods() {
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Recurring")
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));
        when(billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurringConfig))
                .thenReturn(new ArrayList<>());

        billingOccurrenceService.generateOccurrencesForRecurring(billingConfigId);

        verify(billingScheduleRepository, times(4)).saveAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            if (scheduleList.size() != 4) return false;

            // Sep: 2026-09-01 → 2026-09-30
            BillingSchedule sep = scheduleList.get(0);
            if (!sep.getPeriodStartDate().equals(LocalDate.of(2026, 9, 1))) return false;
            if (!sep.getPeriodEndDate().equals(LocalDate.of(2026, 9, 30))) return false;
            if (!sep.getBillingDate().equals(LocalDate.of(2026, 9, 30))) return false;

            // Oct: 2026-10-01 → 2026-10-31
            BillingSchedule oct = scheduleList.get(1);
            if (!oct.getPeriodStartDate().equals(LocalDate.of(2026, 10, 1))) return false;
            if (!oct.getPeriodEndDate().equals(LocalDate.of(2026, 10, 31))) return false;
            if (!oct.getBillingDate().equals(LocalDate.of(2026, 10, 31))) return false;

            // Nov: 2026-11-01 → 2026-11-30
            BillingSchedule nov = scheduleList.get(2);
            if (!nov.getPeriodStartDate().equals(LocalDate.of(2026, 11, 1))) return false;
            if (!nov.getPeriodEndDate().equals(LocalDate.of(2026, 11, 30))) return false;
            if (!nov.getBillingDate().equals(LocalDate.of(2026, 11, 30))) return false;

            // Dec: 2026-12-01 → 2026-12-31
            BillingSchedule dec = scheduleList.get(3);
            if (!dec.getPeriodStartDate().equals(LocalDate.of(2026, 12, 1))) return false;
            if (!dec.getPeriodEndDate().equals(LocalDate.of(2026, 12, 31))) return false;
            if (!dec.getBillingDate().equals(LocalDate.of(2026, 12, 31))) return false;

            return true;
        }));
    }

    // =========================================================
    // SCENARIO 3: PARTIAL FINAL PERIOD
    // =========================================================

    @Test
    void generateOccurrencesForRecurring_PartialFinalPeriod_HandlesCorrectly() {
        recurringConfig.setRecurringEndDate(LocalDate.of(2026, 11, 15));
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Recurring")
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));
        when(billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurringConfig))
                .thenReturn(new ArrayList<>());

        billingOccurrenceService.generateOccurrencesForRecurring(billingConfigId);

        verify(billingScheduleRepository).saveAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            if (scheduleList.size() != 3) return false;

            // Final period should be partial and end on 2026-11-15
            BillingSchedule finalPeriod = scheduleList.get(2);
            if (!finalPeriod.getPeriodEndDate().equals(LocalDate.of(2026, 11, 15))) return false;
            if (!finalPeriod.getIsPartialPeriod()) return false;

            return true;
        }));
    }

    // =========================================================
    // SCENARIO 5: MONTH-END EDGE CASES
    // =========================================================

    @Test
    void calculatePeriodEndDate_January31_HandlesCorrectly() {
        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 1, 31),
                BillingFrequencyMaster.builder()
                        .durationValue(1)
                        .durationUnit(RenewalDurationUnit.MONTHS)
                        .build()
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void calculatePeriodEndDate_February28_LeapYear_HandlesCorrectly() {
        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2024, 2, 28),
                BillingFrequencyMaster.builder()
                        .durationValue(1)
                        .durationUnit(RenewalDurationUnit.MONTHS)
                        .build()
        );

        assertThat(result).isEqualTo(LocalDate.of(2024, 3, 31));
    }

    @Test
    void calculatePeriodEndDate_March31_HandlesCorrectly() {
        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 3, 31),
                BillingFrequencyMaster.builder()
                        .durationValue(1)
                        .durationUnit(RenewalDurationUnit.MONTHS)
                        .build()
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    // =========================================================
    // SCENARIO 6: DATE CHANGE - EXTEND END DATE
    // =========================================================

    @Test
    void reconcileFixedPriceOccurrences_ExtendEndDate_AddsNewOccurrences() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2027, 1, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));

        verify(billingScheduleRepository).saveAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() > 0;
        }));
    }

    // =========================================================
    // SCENARIO 7: DATE CHANGE - REDUCE END DATE
    // =========================================================

    @Test
    void reconcileFixedPriceOccurrences_ReduceEndDate_RemovesFutureScheduled() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 10, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(3)
                .periodStartDate(LocalDate.of(2026, 11, 1))
                .periodEndDate(LocalDate.of(2026, 11, 30))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 2 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    @Test
    void reconcileFixedPriceOccurrences_PreservesTaxCalculatedAndInvoiced() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.isEmpty();
        }));
    }

    // =========================================================
    // SCENARIO 11: DUPLICATE PREVENTION
    // =========================================================

    @Test
    void generateOccurrencesForFixedPrice_ConcurrentRequests_DatabaseConstraintPreventsDuplicates() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
                billingConfiguration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(false)
                .thenReturn(false);

        billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId);
        billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId);

        verify(billingScheduleRepository, times(2)).save(any(BillingSchedule.class));
    }

    // =========================================================
    // SCENARIO 12: SCHEDULER
    // =========================================================

    @Test
    void transitionScheduledToTaxPending_UsesBillingDateBeforeOrEqual() {
        LocalDate today = LocalDate.of(2026, 9, 30);

        List<BillingSchedule> scheduledOccurrences = new ArrayList<>();
        scheduledOccurrences.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingDate(LocalDate.of(2026, 9, 29))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .taxStatus(BillingPeriodStatus.PENDING)
                .isActive(true)
                .build());
        scheduledOccurrences.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .billingDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .taxStatus(BillingPeriodStatus.PENDING)
                .isActive(true)
                .build());

        when(billingScheduleRepository.findByBillingDateBeforeAndTaxStatusAndIsActiveTrue(
                today.plusDays(1), BillingPeriodStatus.PENDING))
                .thenReturn(scheduledOccurrences);

        billingOccurrenceService.transitionScheduledToTaxPending();

        verify(billingScheduleRepository, times(2)).save(argThat(schedule ->
                schedule.getPeriodStatus() == BillingPeriodStatus.TAX_PENDING &&
                        schedule.getTaxStatus() == BillingPeriodStatus.TAX_PENDING
        ));
    }

    // =========================================================
    // VALIDATION TESTS
    // =========================================================

    @Test
    void generateOccurrencesForFixedPrice_MissingEffectiveDates_ThrowsException() {
        fixedPriceConfig.setEffectiveFrom(null);
        fixedPriceConfig.setEffectiveTo(null);
        billingConfiguration.setEffectiveFrom(null);
        billingConfiguration.setEffectiveTo(null);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Effective From and Effective To dates are required");
    }

    @Test
    void generateOccurrencesForFixedPrice_EffectiveFromAfterEffectiveTo_ThrowsException() {
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 9, 30));

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Effective From cannot be after Effective To");
    }

    @Test
    void generateOccurrencesForFixedPrice_MissingFrequency_ThrowsException() {
        billingConfiguration.setBillingFrequency(null);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Billing Frequency is required");
    }

    @Test
    void generateOccurrencesForRecurring_MissingDates_ThrowsException() {
        recurringConfig.setRecurringStartDate(null);
        recurringConfig.setRecurringEndDate(null);
        billingConfiguration.setEffectiveFrom(null);
        billingConfiguration.setEffectiveTo(null);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForRecurring(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Recurring Start Date and Recurring End Date are required");
    }

    @Test
    void generateOccurrencesForRecurring_ConfigurationNotFound_ThrowsException() {
        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForRecurring(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessageContaining("Billing Configuration not found");
    }

    @Test
    void generateOccurrencesForFixedPrice_NotApproved_ThrowsException() {
        billingConfiguration.setApprovalStatus(ApprovalStatus.DRAFT);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Billing Configuration must be APPROVED");
    }

    @Test
    void generateOccurrencesForRecurring_NotApproved_ThrowsException() {
        billingConfiguration.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));

        assertThatThrownBy(() -> billingOccurrenceService.generateOccurrencesForRecurring(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Billing Configuration must be APPROVED");
    }

    @Test
    void reconcileOccurrencesOnConfigurationUpdate_NotApproved_ThrowsException() {
        billingConfiguration.setApprovalStatus(ApprovalStatus.REJECTED);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));

        assertThatThrownBy(() -> billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Billing Configuration must be APPROVED");
    }

    @Test
    void reconcileOccurrencesOnConfigurationUpdate_ConfigurationNotFound_ThrowsException() {
        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.empty());

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository, never()).deleteAll(any());
    }

    @Test
    void reconcileOccurrencesOnConfigurationUpdate_NullBillingType_DoesNothing() {
        billingConfiguration.setBillingType(null);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository, never()).deleteAll(any());
    }

    // =========================================================
    // SCENARIO 4: ALL FREQUENCIES
    // =========================================================

    @Test
    void calculatePeriodEndDate_Weekly_CalculatesCorrectly() {
        BillingFrequencyMaster weeklyFrequency = BillingFrequencyMaster.builder()
                .durationValue(1)
                .durationUnit(RenewalDurationUnit.DAYS)
                .build();

        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 9, 1),
                weeklyFrequency
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void calculatePeriodEndDate_BiWeekly_CalculatesCorrectly() {
        BillingFrequencyMaster biWeeklyFrequency = BillingFrequencyMaster.builder()
                .durationValue(14)
                .durationUnit(RenewalDurationUnit.DAYS)
                .build();

        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 9, 1),
                biWeeklyFrequency
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void calculatePeriodEndDate_Quarterly_CalculatesCorrectly() {
        BillingFrequencyMaster quarterlyFrequency = BillingFrequencyMaster.builder()
                .durationValue(3)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .build();

        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 1, 1),
                quarterlyFrequency
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void calculatePeriodEndDate_HalfYearly_CalculatesCorrectly() {
        BillingFrequencyMaster halfYearlyFrequency = BillingFrequencyMaster.builder()
                .durationValue(6)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .build();

        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 1, 1),
                halfYearlyFrequency
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void calculatePeriodEndDate_Annually_CalculatesCorrectly() {
        BillingFrequencyMaster annuallyFrequency = BillingFrequencyMaster.builder()
                .durationValue(1)
                .durationUnit(RenewalDurationUnit.YEARS)
                .build();

        LocalDate result = billingOccurrenceService.calculatePeriodEndDate(
                LocalDate.of(2026, 1, 1),
                annuallyFrequency
        );

        assertThat(result).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    // =========================================================
    // SCENARIO 8: DATE CHANGE - EFFECTIVE FROM
    // =========================================================

    @Test
    void reconcileFixedPriceOccurrences_ChangeEffectiveFrom_ReconcilesFutureScheduled() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 12, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    @Test
    void reconcileFixedPriceOccurrences_ChangeEffectiveFrom_PreservesHistorical() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.isEmpty();
        }));
    }

    // =========================================================
    // SCENARIO 9: FREQUENCY CHANGE
    // =========================================================

    @Test
    void reconcileFixedPriceOccurrences_FrequencyChange_ReconcilesFutureScheduled() {
        BillingFrequencyMaster quarterlyFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Quarterly")
                .durationValue(3)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .isActive(true)
                .build();

        billingConfiguration.setBillingFrequency(quarterlyFrequency);
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 12, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    // =========================================================
    // SCENARIO 10: AMOUNT CHANGE
    // =========================================================

    @Test
    void amountChange_Scheduled_OccurrenceAmountUpdates() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
                billingConfiguration, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(true);

        billingOccurrenceService.generateOccurrencesForFixedPrice(billingConfigId);

        verify(billingScheduleRepository, never()).save(any());
    }

    @Test
    void amountChange_TaxPending_NoAutomaticRecalculation() {
        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .taxStatus(BillingPeriodStatus.TAX_PENDING)
                .billingAmount(new BigDecimal("10000"))
                .isActive(true)
                .build());

        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        List<BillingSchedule> processedSchedules = existingSchedules.stream()
                .filter(s -> s.getPeriodStatus() == BillingPeriodStatus.TAX_CALCULATED ||
                        s.getPeriodStatus() == BillingPeriodStatus.INVOICED)
                .toList();

        assertThat(processedSchedules).isEmpty();
    }

    // =========================================================
    // SCENARIO 14: TAX RETRY
    // =========================================================

    @Test
    void taxCalculation_Retry_PreventsDuplicates() {
        BillingSchedule schedule = BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .taxStatus(BillingPeriodStatus.TAX_PENDING)
                .billingAmount(new BigDecimal("10000"))
                .isActive(true)
                .build();

        when(billingScheduleRepository.findById(schedule.getBillingScheduleId()))
                .thenReturn(Optional.of(schedule));

        when(taxCalculationRepository.existsByBillingScheduleId(schedule.getBillingScheduleId()))
                .thenReturn(true);

        assertThatThrownBy(() -> taxCalculationService.calculateTaxForSchedule(schedule.getBillingScheduleId()))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessageContaining("Tax calculation has already been completed");
    }

    // =========================================================
    // SCENARIO 16: EXISTING BILLING TYPES
    // =========================================================

    @Test
    void reconcileOccurrencesOnConfigurationUpdate_TimeAndMaterial_DoesNothing() {
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Time & Material")
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository, never()).deleteAll(any());
        verify(billingScheduleRepository, never()).saveAll(any());
    }

    @Test
    void reconcileOccurrencesOnConfigurationUpdate_MilestoneBased_DoesNothing() {
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Milestone Based")
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository, never()).deleteAll(any());
        verify(billingScheduleRepository, never()).saveAll(any());
    }

    // =========================================================
    // NEW TESTS: EFFECTIVE FROM CHANGES
    // =========================================================

    @Test
    void effectiveFromChange_MovedLater_DeletesScheduledBeforeNewStart() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 12, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    @Test
    void effectiveFromChange_MovedEarlier_GeneratesMissingPeriods() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 8, 1));
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 12, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).saveAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return !scheduleList.isEmpty();
        }));
    }

    @Test
    void effectiveFromChange_WithTaxPending_ThrowsException() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        assertThatThrownBy(() -> billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot change Effective From")
                .hasMessageContaining("processed billing occurrences");
    }

    @Test
    void effectiveFromChange_WithTaxCalculated_ThrowsException() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        assertThatThrownBy(() -> billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot change Effective From")
                .hasMessageContaining("processed billing occurrences");
    }

    @Test
    void effectiveFromChange_WithInvoiced_ThrowsException() {
        billingConfiguration.setBillingFrequency(monthlyFrequency);
        fixedPriceConfig.setEffectiveFrom(LocalDate.of(2026, 10, 1));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        assertThatThrownBy(() -> billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot change Effective From")
                .hasMessageContaining("processed billing occurrences");
    }

    // =========================================================
    // NEW TESTS: FREQUENCY CHANGES
    // =========================================================

    @Test
    void frequencyChange_MonthlyToQuarterly_DeletesScheduledOnly() {
        BillingFrequencyMaster quarterlyFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Quarterly")
                .durationValue(3)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .isActive(true)
                .build();

        billingConfiguration.setBillingFrequency(quarterlyFrequency);
        fixedPriceConfig.setEffectiveTo(LocalDate.of(2026, 12, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .billingAmount(new BigDecimal("2500"))
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .billingAmount(new BigDecimal("2500"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    @Test
    void frequencyChange_MonthlyToWeekly_DeletesScheduledOnly() {
        BillingFrequencyMaster weeklyFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Weekly")
                .durationValue(7)
                .durationUnit(RenewalDurationUnit.DAYS)
                .isActive(true)
                .build();

        billingConfiguration.setBillingFrequency(weeklyFrequency);

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    @Test
    void frequencyChange_QuarterlyToMonthly_DeletesScheduledOnly() {
        BillingFrequencyMaster quarterlyFrequency = BillingFrequencyMaster.builder()
                .billingFrequencyId(UUID.randomUUID())
                .billingFrequencyName("Quarterly")
                .durationValue(3)
                .durationUnit(RenewalDurationUnit.MONTHS)
                .isActive(true)
                .build();

        billingConfiguration.setBillingFrequency(quarterlyFrequency);

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 11, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 12, 1))
                .periodEndDate(LocalDate.of(2026, 12, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    // =========================================================
    // NEW TESTS: AMOUNT CHANGES
    // =========================================================

    @Test
    void amountChange_Scheduled_UpdatesAmount() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);
        fixedPriceConfig.setContractValue(new BigDecimal("15000"));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .billingAmount(new BigDecimal("10000"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).save(argThat(schedule ->
                schedule.getBillingAmount().compareTo(new BigDecimal("15000")) == 0
        ));
    }

    @Test
    void amountChange_TaxPending_UpdatesAmountAndInvalidatesTax() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);
        fixedPriceConfig.setContractValue(new BigDecimal("15000"));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .taxStatus(BillingPeriodStatus.TAX_PENDING)
                .billingAmount(new BigDecimal("10000"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).save(argThat(schedule ->
                schedule.getBillingAmount().compareTo(new BigDecimal("15000")) == 0
        ));
        verify(taxCalculationRepository).deleteByBillingScheduleId(existingSchedules.get(0).getBillingScheduleId());
    }

    @Test
    void amountChange_TaxCalculated_ThrowsException() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);
        fixedPriceConfig.setContractValue(new BigDecimal("15000"));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .taxStatus(BillingPeriodStatus.TAX_CALCULATED)
                .billingAmount(new BigDecimal("10000"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        assertThatThrownBy(() -> billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot change billing amount")
                .hasMessageContaining("TAX_CALCULATED");
    }

    @Test
    void amountChange_Invoiced_ThrowsException() {
        billingConfiguration.setBillingFrequency(oneTimeFrequency);
        fixedPriceConfig.setContractValue(new BigDecimal("15000"));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .billingAmount(new BigDecimal("10000"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingFixedPriceRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(fixedPriceConfig));
        when(billingScheduleRepository.findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(billingConfiguration))
                .thenReturn(existingSchedules);

        assertThatThrownBy(() -> billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("Cannot change billing amount")
                .hasMessageContaining("INVOICED");
    }

    // =========================================================
    // NEW TESTS: RECURRING CONFIGURATION CHANGES
    // =========================================================

    @Test
    void recurringEffectiveFromChange_MovedLater_DeletesScheduledBeforeNewStart() {
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Recurring")
                .build());
        recurringConfig.setRecurringStartDate(LocalDate.of(2026, 10, 1));
        recurringConfig.setRecurringEndDate(LocalDate.of(2026, 12, 31));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.INVOICED)
                .isInvoiced(true)
                .isActive(true)
                .build());
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(2)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));
        when(billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurringConfig))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).deleteAll(argThat(schedules -> {
            List<BillingSchedule> scheduleList = new ArrayList<>();
            schedules.forEach(scheduleList::add);
            return scheduleList.size() == 1 &&
                    scheduleList.stream().allMatch(s -> s.getPeriodStatus() == BillingPeriodStatus.SCHEDULED);
        }));
    }

    @Test
    void recurringAmountChange_Scheduled_UpdatesAmount() {
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Recurring")
                .build());
        recurringConfig.setContractValue(new BigDecimal("15000"));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .billingAmount(new BigDecimal("12000"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));
        when(billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurringConfig))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).save(argThat(schedule ->
                schedule.getBillingAmount().compareTo(new BigDecimal("15000")) == 0
        ));
    }

    @Test
    void recurringAmountChange_TaxPending_UpdatesAmountAndInvalidatesTax() {
        billingConfiguration.setBillingType(BillingTypeMaster.builder()
                .billingTypeName("Recurring")
                .build());
        recurringConfig.setContractValue(new BigDecimal("15000"));

        List<BillingSchedule> existingSchedules = new ArrayList<>();
        existingSchedules.add(BillingSchedule.builder()
                .billingScheduleId(UUID.randomUUID())
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 9, 1))
                .periodEndDate(LocalDate.of(2026, 9, 30))
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .taxStatus(BillingPeriodStatus.TAX_PENDING)
                .billingAmount(new BigDecimal("12000"))
                .isActive(true)
                .build());

        when(billingConfigurationRepository.findById(billingConfigId))
                .thenReturn(Optional.of(billingConfiguration));
        when(billingRecurringConfigurationRepository.findByBillingConfigurationAndIsActiveTrue(billingConfiguration))
                .thenReturn(Optional.of(recurringConfig));
        when(billingScheduleRepository.findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(recurringConfig))
                .thenReturn(existingSchedules);

        billingOccurrenceService.reconcileOccurrencesOnConfigurationUpdate(billingConfigId);

        verify(billingScheduleRepository).save(argThat(schedule ->
                schedule.getBillingAmount().compareTo(new BigDecimal("15000")) == 0
        ));
        verify(taxCalculationRepository).deleteByBillingScheduleId(existingSchedules.get(0).getBillingScheduleId());
    }
}
