package com.AccountReceivableManagement.service_Imple.tax_calculation;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.projectbilling_config.TaxRateConfiguration;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.TaxConfigurationRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceImplTest {

    @Mock
    private TaxCalculationRepository taxCalculationRepository;

    @Mock
    private BillingSnapshotRepository billingSnapshotRepository;

    @Mock
    private TaxConfigurationRepository taxRateConfigurationRepository;

    @Mock
    private BillingConfigurationService billingConfigurationService;

    @InjectMocks
    private TaxCalculationServiceImpl taxCalculationService;

    private UUID snapshotId;
    private UUID taxRegionId;
    private LocalDate billingPeriodStart;

    @BeforeEach
    void setUp() {
        snapshotId = UUID.randomUUID();
        taxRegionId = UUID.randomUUID();
        billingPeriodStart = LocalDate.of(2026, 7, 1);
    }

    private BillingSnapshot readySnapshot(BigDecimal totalAmount) {
        return BillingSnapshot.builder()
                .id(snapshotId)
                .snapshotNumber("BS-20260701120000")
                .taxRegionId(taxRegionId)
                .taxRegionCode("IN-KA")
                .billingPeriodStart(billingPeriodStart)
                .billingPeriodEnd(LocalDate.of(2026, 7, 31))
                .status(BillingSnapshotStatus.READY_TO_TAX)
                .subtotal(totalAmount)
                .expenseAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .build();
    }

    private BillingConfigurationResponseDto snapshotConfiguration() {
        return BillingConfigurationResponseDto.builder()
                .projectName("Website Redesign")
                .clientName("Acme Corp")
                .currencyCode("USD")
                .taxRegionName("Domestic (GST 18%)")
                .taxRegionCode("DOM")
                .build();
    }

    private TaxRateConfiguration cgstSgstConfiguration() {
        return TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .sgstRate(new BigDecimal("9.0000"))
                .igstRate(null)
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();
    }

    private TaxRateConfiguration igstConfiguration() {
        return TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxType("GST")
                .cgstRate(null)
                .sgstRate(null)
                .igstRate(new BigDecimal("18.0000"))
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();
    }

    // CASE 1 — Valid CGST + SGST
    @Test
    void calculateTax_validCgstSgst_calculatesAndCompletesSnapshot() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(cgstSgstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingSnapshotRepository.save(any(BillingSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(snapshotConfiguration());

        TaxCalculationResponseDto response = taxCalculationService.calculateTax(snapshotId);

        assertThat(response.getTaxableAmount()).isEqualByComparingTo("168000.00");
        assertThat(response.getCgstAmount()).isEqualByComparingTo("15120.00");
        assertThat(response.getSgstAmount()).isEqualByComparingTo("15120.00");
        assertThat(response.getIgstAmount()).isNull();
        assertThat(response.getTotalTaxAmount()).isEqualByComparingTo("30240.00");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("198240.00");
        assertThat(response.getStatus()).isEqualTo(TaxCalculationStatus.CALCULATED);

        assertThat(response.getProjectName()).isEqualTo("Website Redesign");
        assertThat(response.getClientName()).isEqualTo("Acme Corp");
        assertThat(response.getCurrencyCode()).isEqualTo("USD");
        assertThat(response.getTaxRegionName()).isEqualTo("Domestic (GST 18%)");
        assertThat(response.getTaxRegionCode()).isEqualTo("DOM");
        assertThat(response.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(response.getSnapshotStatus()).isEqualTo(BillingSnapshotStatus.TAX_COMPLETED);

        assertThat(snapshot.getStatus()).isEqualTo(BillingSnapshotStatus.TAX_COMPLETED);
        verify(billingSnapshotRepository).save(snapshot);
    }

    // CASE 2 — Valid IGST
    @Test
    void calculateTax_validIgst_calculatesCorrectly() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(igstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingSnapshotRepository.save(any(BillingSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(snapshotConfiguration());

        TaxCalculationResponseDto response = taxCalculationService.calculateTax(snapshotId);

        assertThat(response.getCgstAmount()).isNull();
        assertThat(response.getSgstAmount()).isNull();
        assertThat(response.getIgstAmount()).isEqualByComparingTo("30240.00");
        assertThat(response.getTotalTaxAmount()).isEqualByComparingTo("30240.00");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("198240.00");
    }

    // CASE 3 — Snapshot not found
    @Test
    void calculateTax_snapshotNotFound_throwsResourceNotFoundException() {
        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Billing snapshot could not be found.");

        verify(taxCalculationRepository, never()).save(any());
    }

    // CASE 4 — Snapshot not READY_TO_TAX
    @Test
    void calculateTax_snapshotNotReadyForTax_throwsValidationException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));
        snapshot.setStatus(BillingSnapshotStatus.IN_PROGRESS);

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Tax calculation cannot be started because this billing snapshot is not ready for tax calculation.");

        verify(taxCalculationRepository, never()).save(any());
    }

    // CASE 5 — Missing tax region
    @Test
    void calculateTax_missingTaxRegion_throwsValidationException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));
        snapshot.setTaxRegionId(null);

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Tax calculation cannot proceed because no tax region is configured for this billing snapshot.");

        verify(taxCalculationRepository, never()).save(any());
    }

    // CASE 6 — No applicable configuration
    @Test
    void calculateTax_noApplicableConfiguration_throwsValidationException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of());

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("No active tax configuration is available for the selected tax region and billing period.");

        verify(taxCalculationRepository, never()).save(any());
        verify(billingSnapshotRepository, never()).save(any());
    }

    // CASE 7 — Existing completed calculation
    @Test
    void calculateTax_existingCalculation_throwsDuplicateResourceException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(true);

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessage("Tax calculation has already been completed for this billing snapshot.");

        verify(taxCalculationRepository, never()).save(any());
        verify(taxRateConfigurationRepository, never()).findApplicableConfigurations(any(), any());
    }

    // CASE 7b — Concurrent duplicate caught at the DB unique-constraint level
    @Test
    void calculateTax_concurrentDuplicateAtSave_throwsDuplicateResourceException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(cgstSgstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessage("Tax calculation has already been completed for this billing snapshot.");
    }

    // CASE 8 — Effective date: must use billingPeriodStart, not "current date"
    @Test
    void calculateTax_resolvesConfigurationUsingBillingPeriodStart() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));
        snapshot.setBillingPeriodStart(LocalDate.of(2020, 1, 1)); // far from "today" to prove it's not LocalDate.now()

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(any(), any()))
                .thenReturn(List.of(cgstSgstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingSnapshotRepository.save(any(BillingSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(snapshotConfiguration());

        taxCalculationService.calculateTax(snapshotId);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(taxRateConfigurationRepository).findApplicableConfigurations(eq(taxRegionId), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    // CASE 9 — Tax amount rounding
    @Test
    void calculateTax_roundsMonetaryAmountsToTwoDecimalPlacesHalfUp() {
        // 333.33 * 9 / 100 = 29.9997 -> rounds HALF_UP to 30.00
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("333.33"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(cgstSgstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingSnapshotRepository.save(any(BillingSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(snapshotConfiguration());

        TaxCalculationResponseDto response = taxCalculationService.calculateTax(snapshotId);

        assertThat(response.getCgstAmount()).isEqualByComparingTo("30.00");
        assertThat(response.getCgstAmount().scale()).isEqualTo(2);
        assertThat(response.getSgstAmount()).isEqualByComparingTo("30.00");
        assertThat(response.getTotalTaxAmount()).isEqualByComparingTo("60.00");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("393.33");
    }

    // CASE 10 — Snapshot remains pre-tax
    @Test
    void calculateTax_billingSnapshotTotalAmountRemainsUnchanged() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(cgstSgstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingSnapshotRepository.save(any(BillingSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(snapshotConfiguration());

        TaxCalculationResponseDto response = taxCalculationService.calculateTax(snapshotId);

        assertThat(snapshot.getTotalAmount()).isEqualByComparingTo("168000.00");
        assertThat(snapshot.getSubtotal()).isEqualByComparingTo("168000.00");
        assertThat(snapshot.getExpenseAmount()).isEqualByComparingTo("0");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("198240.00");
        assertThat(response.getGrandTotal()).isNotEqualByComparingTo(snapshot.getTotalAmount());
    }

    // CASE 11 — Transaction failure: snapshot must not become TAX_COMPLETED if persistence fails
    @Test
    void calculateTax_taxCalculationPersistenceFails_snapshotNotMarkedCompleted() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(cgstSgstConfiguration()));
        when(taxCalculationRepository.save(any(TaxCalculation.class)))
                .thenThrow(new RuntimeException("unexpected database error"));

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(RuntimeException.class);

        // The snapshot's status is never persisted as TAX_COMPLETED when TaxCalculation
        // fails to save; the @Transactional boundary on the service method additionally
        // ensures the in-memory IN_TAX mutation is rolled back at the database level.
        verify(billingSnapshotRepository, never()).save(any());
        assertThat(snapshot.getStatus()).isNotEqualTo(BillingSnapshotStatus.TAX_COMPLETED);
    }

    // Additional coverage — invalid tax component combination
    @Test
    void calculateTax_cgstOnlyWithoutSgst_throwsValidationException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        TaxRateConfiguration invalidConfiguration = TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxType("GST")
                .cgstRate(new BigDecimal("9.0000"))
                .sgstRate(null)
                .igstRate(null)
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(invalidConfiguration));

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("invalid combination");

        verify(taxCalculationRepository, never()).save(any());
    }

    // Additional coverage — an explicit zero rate is treated as "not applicable",
    // same as a blank/null rate, not as a valid CGST+SGST pairing
    @Test
    void calculateTax_zeroCgstRateWithSgst_throwsValidationException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        TaxRateConfiguration zeroCgstConfiguration = TaxRateConfiguration.builder()
                .taxRateConfigurationId(UUID.randomUUID())
                .taxType("GST")
                .cgstRate(BigDecimal.ZERO)
                .sgstRate(new BigDecimal("9.0000"))
                .igstRate(null)
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .isActive(true)
                .build();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(taxRateConfigurationRepository.findApplicableConfigurations(taxRegionId, billingPeriodStart))
                .thenReturn(List.of(zeroCgstConfiguration));

        assertThatThrownBy(() -> taxCalculationService.calculateTax(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("invalid combination");

        verify(taxCalculationRepository, never()).save(any());
    }

    // GET — existing calculation retrieved
    @Test
    void getTaxCalculationBySnapshotId_existingCalculation_returnsIt() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));
        snapshot.setStatus(BillingSnapshotStatus.TAX_COMPLETED);

        TaxCalculation taxCalculation = TaxCalculation.builder()
                .taxCalculationId(UUID.randomUUID())
                .billingSnapshotId(snapshotId)
                .taxRegionId(taxRegionId)
                .taxRateConfigurationId(UUID.randomUUID())
                .taxableAmount(new BigDecimal("168000.00"))
                .cgstRate(new BigDecimal("9.0000"))
                .cgstAmount(new BigDecimal("15120.00"))
                .sgstRate(new BigDecimal("9.0000"))
                .sgstAmount(new BigDecimal("15120.00"))
                .totalTaxAmount(new BigDecimal("30240.00"))
                .grandTotal(new BigDecimal("198240.00"))
                .status(TaxCalculationStatus.CALCULATED)
                .calculatedAt(LocalDateTime.now())
                .build();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(taxCalculation));
        when(billingConfigurationService.getBillingConfiguration(any()))
                .thenReturn(snapshotConfiguration());

        TaxCalculationResponseDto response = taxCalculationService.getTaxCalculationBySnapshotId(snapshotId);

        assertThat(response.getSnapshotNumber()).isEqualTo("BS-20260701120000");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("198240.00");
        assertThat(response.getProjectName()).isEqualTo("Website Redesign");
        assertThat(response.getTaxRegionName()).isEqualTo("Domestic (GST 18%)");
    }

    // GET — no calculation exists yet
    @Test
    void getTaxCalculationBySnapshotId_noCalculation_throwsResourceNotFoundException() {
        BillingSnapshot snapshot = readySnapshot(new BigDecimal("168000.00"));

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxCalculationService.getTaxCalculationBySnapshotId(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("No tax calculation has been completed for this billing snapshot.");
    }
}
