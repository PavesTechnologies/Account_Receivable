package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxRegionMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1 (automation readiness): repeating a tax calculation for the same
 * Billing Snapshot or Billing Occurrence reuses the existing record and
 * never creates a second one.
 */
@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceImplIdempotencyTest {

    @Mock private TaxCalculationRepository taxCalculationRepository;
    @Mock private BillingSnapshotRepository billingSnapshotRepository;
    @Mock private BillingScheduleRepository billingScheduleRepository;
    @Mock private TaxConfigurationRepository taxConfigurationRepository;
    @Mock private TaxRegionMasterRepository taxRegionMasterRepository;
    @Mock private BillingConfigurationRepository billingConfigurationRepository;

    @InjectMocks
    private TaxCalculationServiceImpl taxCalculationService;

    private TaxCalculation existingCalculation(TaxCalculationStatus status) {
        return TaxCalculation.builder()
                .taxCalculationId(UUID.randomUUID())
                .taxRegionId(UUID.randomUUID())
                .taxConfigurationId(UUID.randomUUID())
                .taxableAmount(new BigDecimal("1000.00"))
                .totalTaxAmount(new BigDecimal("180.00"))
                .grandTotal(new BigDecimal("1180.00"))
                .status(status)
                .calculatedAt(LocalDateTime.now())
                .components(new ArrayList<>())
                .build();
    }

    @Test
    void findOrCalculateTax_existingCalculation_isReusedNotDuplicated() {
        UUID snapshotId = UUID.randomUUID();
        UUID configurationId = UUID.randomUUID();
        TaxCalculation existing = existingCalculation(TaxCalculationStatus.CALCULATED);
        existing.setBillingSnapshotId(snapshotId);
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(existing));
        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(BillingSnapshot.builder()
                .id(snapshotId).billingConfigurationId(configurationId)
                .status(BillingSnapshotStatus.TAX_COMPLETED).build()));
        when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(
                BillingConfiguration.builder().billingConfigurationId(configurationId).build()));

        TaxCalculationResponseDto first = taxCalculationService.findOrCalculateTax(snapshotId);
        TaxCalculationResponseDto second = taxCalculationService.findOrCalculateTax(snapshotId);

        assertThat(first.getTaxCalculationId()).isEqualTo(existing.getTaxCalculationId());
        assertThat(second.getTaxCalculationId()).isEqualTo(existing.getTaxCalculationId());
        verify(taxCalculationRepository, never()).save(any());
    }

    @Test
    void findOrCalculateTax_existingCalculationNotCompleted_isReportedNotReused() {
        UUID snapshotId = UUID.randomUUID();
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId))
                .thenReturn(Optional.of(existingCalculation(TaxCalculationStatus.FAILED)));

        assertThatThrownBy(() -> taxCalculationService.findOrCalculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("has status FAILED");
        verify(taxCalculationRepository, never()).save(any());
    }

    @Test
    void findOrCalculateTaxForSchedule_existingCalculation_isReusedNotDuplicated() {
        UUID scheduleId = UUID.randomUUID();
        UUID configurationId = UUID.randomUUID();
        TaxCalculation existing = existingCalculation(TaxCalculationStatus.CALCULATED);
        existing.setBillingScheduleId(scheduleId);
        BillingConfiguration configuration = BillingConfiguration.builder()
                .billingConfigurationId(configurationId).build();
        when(taxCalculationRepository.findByBillingScheduleId(scheduleId)).thenReturn(Optional.of(existing));
        when(billingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(BillingSchedule.builder()
                .billingScheduleId(scheduleId)
                .billingConfiguration(configuration)
                .periodNumber(1)
                .periodStartDate(LocalDate.of(2026, 10, 1))
                .periodEndDate(LocalDate.of(2026, 10, 31))
                .billingAmount(new BigDecimal("1000.00"))
                .periodStatus(BillingPeriodStatus.TAX_CALCULATED)
                .taxStatus(BillingPeriodStatus.TAX_CALCULATED)
                .build()));
        when(billingConfigurationRepository.findById(configurationId)).thenReturn(Optional.of(configuration));

        TaxCalculationResponseDto response = taxCalculationService.findOrCalculateTaxForSchedule(scheduleId);

        assertThat(response.getTaxCalculationId()).isEqualTo(existing.getTaxCalculationId());
        verify(taxCalculationRepository, never()).save(any());
        verify(billingScheduleRepository, never()).save(any());
    }

    @Test
    void findOrCalculateTaxForSchedule_noCalculationYet_delegatesToTheExistingCalculation() {
        UUID scheduleId = UUID.randomUUID();
        when(taxCalculationRepository.findByBillingScheduleId(scheduleId)).thenReturn(Optional.empty());
        when(billingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(BillingSchedule.builder()
                .billingScheduleId(scheduleId)
                .periodStatus(BillingPeriodStatus.SCHEDULED)
                .isActive(true)
                .build()));

        // calculateTaxForSchedule's own rules apply unchanged.
        assertThatThrownBy(() -> taxCalculationService.findOrCalculateTaxForSchedule(scheduleId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("not in TAX_PENDING status");
        verify(taxCalculationRepository, never()).save(any());
    }

    @Test
    void calculateTaxForSchedule_missingPrerequisite_failsWithoutPersistingOrAdvancingOccurrence() {
        UUID scheduleId = UUID.randomUUID();
        when(taxCalculationRepository.existsByBillingScheduleId(scheduleId)).thenReturn(false);
        BillingSchedule schedule = BillingSchedule.builder()
                .billingScheduleId(scheduleId)
                .periodStatus(BillingPeriodStatus.TAX_PENDING)
                .taxStatus(BillingPeriodStatus.TAX_PENDING)
                .isActive(true)
                .build();
        when(billingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        // No billing configuration / tax region: fails before any insert, so no partial tax state.
        assertThatThrownBy(() -> taxCalculationService.calculateTaxForSchedule(scheduleId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class);
        assertThat(schedule.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING);
        verify(taxCalculationRepository, never()).save(any());
    }

    @Test
    void calculateTaxForSchedule_missingOrNegativeAmount_isNotTaxedAsZero() {
        for (BigDecimal amount : new BigDecimal[] {null, new BigDecimal("-1.00")}) {
            UUID scheduleId = UUID.randomUUID();
            when(taxCalculationRepository.existsByBillingScheduleId(scheduleId)).thenReturn(false);
            BillingSchedule schedule = BillingSchedule.builder()
                    .billingScheduleId(scheduleId)
                    .billingAmount(amount)
                    .periodStatus(BillingPeriodStatus.TAX_PENDING)
                    .taxStatus(BillingPeriodStatus.TAX_PENDING)
                    .isActive(true)
                    .build();
            when(billingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

            assertThatThrownBy(() -> taxCalculationService.calculateTaxForSchedule(scheduleId))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("no valid billing amount");
            assertThat(schedule.getPeriodStatus()).isEqualTo(BillingPeriodStatus.TAX_PENDING);
        }
        verify(taxCalculationRepository, never()).save(any());
    }
}
