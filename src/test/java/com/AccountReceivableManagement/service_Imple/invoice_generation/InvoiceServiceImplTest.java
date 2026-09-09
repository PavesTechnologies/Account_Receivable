package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceSummaryResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshotItem;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculation;
import com.AccountReceivableManagement.entity.tax_calculation.TaxCalculationComponent;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingSnapshotStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxCalculationStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.billing_data_acquisition.BillingSnapshotRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.PaymentTermsMasterRepository;
import com.AccountReceivableManagement.repo.tax_calculation.TaxCalculationRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private BillingSnapshotRepository billingSnapshotRepository;

    @Mock
    private TaxCalculationRepository taxCalculationRepository;

    @Mock
    private BillingConfigurationService billingConfigurationService;

    @Mock
    private PaymentTermsMasterRepository paymentTermsMasterRepository;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private UUID snapshotId;
    private UUID clientId;
    private UUID paymentTermId;

    @BeforeEach
    void setUp() {
        snapshotId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        paymentTermId = UUID.randomUUID();
    }

    private BillingSnapshot taxCompletedSnapshot() {
        BillingSnapshot snapshot = BillingSnapshot.builder()
                .id(snapshotId)
                .snapshotNumber("BS-20260908164549")
                .clientId(clientId)
                .projectId(23L)
                .billingConfigurationId(UUID.randomUUID())
                .currencyCode("USD")
                .paymentTermId(paymentTermId)
                .paymentTermCode("NET_30")
                .billingPeriodStart(LocalDate.of(2026, 6, 1))
                .billingPeriodEnd(LocalDate.of(2026, 8, 30))
                .status(BillingSnapshotStatus.TAX_COMPLETED)
                .subtotal(new BigDecimal("5500.00"))
                .expenseAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("5500.00"))
                .build();

        snapshot.getItems().add(
                BillingSnapshotItem.builder()
                        .billingSnapshotItemId(UUID.randomUUID())
                        .billingSnapshot(snapshot)
                        .itemType(BillingItemType.TIME_ENTRY)
                        .itemName("Backend Development")
                        .sourceReferenceId("TMS-1001")
                        .quantity(new BigDecimal("55.00"))
                        .rate(new BigDecimal("100.00"))
                        .amount(new BigDecimal("5500.00"))
                        .workDate(LocalDate.of(2026, 7, 15))
                        .role("Developer")
                        .build()
        );

        return snapshot;
    }

    private TaxCalculation completedTaxCalculation() {
        TaxCalculation calculation = TaxCalculation.builder()
                .taxCalculationId(UUID.randomUUID())
                .billingSnapshotId(snapshotId)
                .taxRegionId(UUID.randomUUID())
                .taxConfigurationId(UUID.randomUUID())
                .taxableAmount(new BigDecimal("5500.00"))
                .totalTaxAmount(new BigDecimal("990.00"))
                .grandTotal(new BigDecimal("6490.00"))
                .status(TaxCalculationStatus.CALCULATED)
                .calculatedAt(LocalDateTime.now())
                .components(new ArrayList<>())
                .build();

        TaxCalculationComponent cgst = TaxCalculationComponent.builder()
                .taxCalculationComponentId(UUID.randomUUID())
                .taxCalculation(calculation)
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("CGST")
                .taxTypeName("Central GST")
                .appliedRate(new BigDecimal("9.0000"))
                .taxAmount(new BigDecimal("495.00"))
                .applicabilityType(TaxApplicabilityType.ALL)
                .build();

        TaxCalculationComponent sgst = TaxCalculationComponent.builder()
                .taxCalculationComponentId(UUID.randomUUID())
                .taxCalculation(calculation)
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode("SGST")
                .taxTypeName("State GST")
                .appliedRate(new BigDecimal("9.0000"))
                .taxAmount(new BigDecimal("495.00"))
                .applicabilityType(TaxApplicabilityType.ALL)
                .build();

        calculation.getComponents().add(cgst);
        calculation.getComponents().add(sgst);

        return calculation;
    }

    private BillingConfigurationResponseDto configuration() {
        return BillingConfigurationResponseDto.builder()
                .projectName("Website Redesign")
                .clientName("Account Management")
                .build();
    }

    private PaymentTermsMaster paymentTerms() {
        return PaymentTermsMaster.builder()
                .paymentTermId(paymentTermId)
                .paymentTermName("Net 30")
                .paymentDays(30)
                .isActive(true)
                .build();
    }

    // CASE 1/2/3/4/5/6/7/8/9/10 — Happy path: full invoice generation from the verified example.
    @Test
    void generateInvoice_taxCompletedSnapshot_generatesInvoiceAndCompletesTransition() {
        BillingSnapshot snapshot = taxCompletedSnapshot();
        TaxCalculation taxCalculation = completedTaxCalculation();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(taxCalculation));
        when(invoiceRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(billingConfigurationService.getBillingConfiguration(any())).thenReturn(configuration());
        when(paymentTermsMasterRepository.findById(paymentTermId)).thenReturn(Optional.of(paymentTerms()));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingSnapshotRepository.save(any(BillingSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceResponseDto response = invoiceService.generateInvoice(snapshotId);

        // Invoice number generated by the backend.
        assertThat(response.getInvoiceNumber()).isNotBlank();
        assertThat(response.getInvoiceNumber()).startsWith("INV-");
        assertThat(response.getInvoiceNumber()).isNotEqualTo(snapshot.getSnapshotNumber());

        // Human-readable billing snapshot number exposed alongside the id, never fabricated.
        assertThat(response.getBillingSnapshotId()).isEqualTo(snapshotId);
        assertThat(response.getBillingSnapshotNumber()).isEqualTo("BS-20260908164549");

        // Client / project information copied.
        assertThat(response.getClientId()).isEqualTo(clientId);
        assertThat(response.getClientName()).isEqualTo("Account Management");
        assertThat(response.getProjectId()).isEqualTo(23L);
        assertThat(response.getProjectName()).isEqualTo("Website Redesign");

        // Billing period copied.
        assertThat(response.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 8, 30));

        // Payment terms copied and due date derived from them.
        assertThat(response.getPaymentTermCode()).isEqualTo("NET_30");
        assertThat(response.getDueDate()).isEqualTo(response.getInvoiceDate().plusDays(30));

        // BillingSnapshotItems -> InvoiceItems.
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemName()).isEqualTo("Backend Development");
        assertThat(response.getItems().get(0).getAmount()).isEqualByComparingTo("5500.00");

        // TaxCalculationComponents -> InvoiceTaxComponents.
        assertThat(response.getTaxComponents()).hasSize(2);
        assertThat(response.getTaxComponents())
                .anySatisfy(c -> {
                    assertThat(c.getTaxTypeCode()).isEqualTo("CGST");
                    assertThat(c.getTaxAmount()).isEqualByComparingTo("495.00");
                })
                .anySatisfy(c -> {
                    assertThat(c.getTaxTypeCode()).isEqualTo("SGST");
                    assertThat(c.getTaxAmount()).isEqualByComparingTo("495.00");
                });

        // Financial totals copied as-is from TaxCalculation - never recalculated.
        assertThat(response.getSubtotal()).isEqualByComparingTo(taxCalculation.getTaxableAmount());
        assertThat(response.getTotalTaxAmount()).isEqualByComparingTo(taxCalculation.getTotalTaxAmount());
        assertThat(response.getGrandTotal()).isEqualByComparingTo(taxCalculation.getGrandTotal());
        assertThat(response.getSubtotal()).isEqualByComparingTo("5500.00");
        assertThat(response.getTotalTaxAmount()).isEqualByComparingTo("990.00");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("6490.00");

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.GENERATED);

        // BillingSnapshot transitions TAX_COMPLETED -> INVOICED, on the same instance
        // (no new BillingSnapshot is created).
        assertThat(snapshot.getStatus()).isEqualTo(BillingSnapshotStatus.INVOICED);
        verify(billingSnapshotRepository).save(snapshot);
        verify(billingSnapshotRepository, never()).save(argThat(s -> s != snapshot));

        // No tax recalculation occurs - the tax calculation is only ever read, never saved.
        verify(taxCalculationRepository, never()).save(any());
    }

    // CASE 11 — READY_FOR_TAX snapshot cannot generate an invoice.
    @Test
    void generateInvoice_snapshotNotTaxCompleted_throwsValidationException() {
        BillingSnapshot snapshot = taxCompletedSnapshot();
        snapshot.setStatus(BillingSnapshotStatus.READY_FOR_TAX);

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Invoice cannot be generated because this billing snapshot has not completed tax calculation.");

        verify(invoiceRepository, never()).save(any());
        verify(billingSnapshotRepository, never()).save(any());
    }

    // CASE 12 — Missing BillingSnapshot fails correctly.
    @Test
    void generateInvoice_snapshotNotFound_throwsResourceNotFoundException() {
        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Billing snapshot could not be found.");

        verify(invoiceRepository, never()).save(any());
    }

    // CASE 13 — Missing TaxCalculation fails correctly.
    @Test
    void generateInvoice_taxCalculationMissing_throwsResourceNotFoundException() {
        BillingSnapshot snapshot = taxCompletedSnapshot();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("No tax calculation has been completed for this billing snapshot. Invoice cannot be generated.");

        verify(invoiceRepository, never()).save(any());
    }

    // CASE 14 — Existing invoice cannot be generated again (application-level check).
    @Test
    void generateInvoice_invoiceAlreadyExists_throwsDuplicateResourceException() {
        BillingSnapshot snapshot = taxCompletedSnapshot();
        TaxCalculation taxCalculation = completedTaxCalculation();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(taxCalculation));
        when(invoiceRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessage("An invoice has already been generated for this billing snapshot.");

        verify(invoiceRepository, never()).save(any());
        verify(billingSnapshotRepository, never()).save(any());
    }

    // CASE 15 — Database uniqueness backstops the duplicate check under concurrent requests.
    @Test
    void generateInvoice_concurrentDuplicateAtSave_throwsDuplicateResourceException() {
        BillingSnapshot snapshot = taxCompletedSnapshot();
        TaxCalculation taxCalculation = completedTaxCalculation();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(taxCalculation));
        when(invoiceRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(billingConfigurationService.getBillingConfiguration(any())).thenReturn(configuration());
        when(paymentTermsMasterRepository.findById(paymentTermId)).thenReturn(Optional.of(paymentTerms()));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class)
                .hasMessage("An invoice has already been generated for this billing snapshot.");

        // Snapshot must not flip to INVOICED when invoice persistence fails.
        verify(billingSnapshotRepository, never()).save(any());
        assertThat(snapshot.getStatus()).isNotEqualTo(BillingSnapshotStatus.INVOICED);
    }

    // CASE 16 — Invoice generation rolls back on unexpected persistence failure.
    @Test
    void generateInvoice_persistenceFails_snapshotNotMarkedInvoiced() {
        BillingSnapshot snapshot = taxCompletedSnapshot();
        TaxCalculation taxCalculation = completedTaxCalculation();

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(taxCalculation));
        when(invoiceRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);
        when(billingConfigurationService.getBillingConfiguration(any())).thenReturn(configuration());
        when(paymentTermsMasterRepository.findById(paymentTermId)).thenReturn(Optional.of(paymentTerms()));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenThrow(new RuntimeException("unexpected database error"));

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(RuntimeException.class);

        // The @Transactional boundary ensures the in-memory INVOICED mutation, if any,
        // is rolled back at the database level; here we assert it was never even reached.
        verify(billingSnapshotRepository, never()).save(any());
        assertThat(snapshot.getStatus()).isNotEqualTo(BillingSnapshotStatus.INVOICED);
    }

    // CASE 17 — Tax calculation totals inconsistency is detected instead of silently corrected.
    @Test
    void generateInvoice_taxCalculationTotalsInconsistent_throwsValidationException() {
        BillingSnapshot snapshot = taxCompletedSnapshot();
        TaxCalculation taxCalculation = completedTaxCalculation();
        taxCalculation.setGrandTotal(new BigDecimal("9999.00")); // deliberately wrong

        when(billingSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(taxCalculationRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(taxCalculation));
        when(invoiceRepository.existsByBillingSnapshotId(snapshotId)).thenReturn(false);

        assertThatThrownBy(() -> invoiceService.generateInvoice(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Tax calculation totals are inconsistent for this billing snapshot. Invoice cannot be generated.");

        verify(invoiceRepository, never()).save(any());
    }

    // CASE 18 — GET returns the persisted invoice without recalculating anything.
    @Test
    void getInvoiceByBillingSnapshotId_existingInvoice_returnsIt() {
        Invoice invoice = Invoice.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceNumber("INV-20260908170000")
                .billingSnapshotId(snapshotId)
                .billingSnapshotNumber("BS-20260908164549")
                .taxCalculationId(UUID.randomUUID())
                .clientId(clientId)
                .clientName("Account Management")
                .projectId(23L)
                .projectName("Website Redesign")
                .billingPeriodStart(LocalDate.of(2026, 6, 1))
                .billingPeriodEnd(LocalDate.of(2026, 8, 30))
                .currencyCode("USD")
                .paymentTermCode("NET_30")
                .subtotal(new BigDecimal("5500.00"))
                .totalTaxAmount(new BigDecimal("990.00"))
                .grandTotal(new BigDecimal("6490.00"))
                .invoiceDate(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .status(InvoiceStatus.GENERATED)
                .items(new ArrayList<>())
                .taxComponents(new ArrayList<>())
                .build();

        when(billingSnapshotRepository.existsById(snapshotId)).thenReturn(true);
        when(invoiceRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.of(invoice));

        InvoiceResponseDto response = invoiceService.getInvoiceByBillingSnapshotId(snapshotId);

        assertThat(response.getInvoiceNumber()).isEqualTo("INV-20260908170000");
        assertThat(response.getBillingSnapshotNumber()).isEqualTo("BS-20260908164549");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("6490.00");

        verifyNoInteractions(billingConfigurationService);
        verify(taxCalculationRepository, never()).findByBillingSnapshotId(any());
    }

    // CASE 19 — GET fails correctly when no invoice exists yet.
    @Test
    void getInvoiceByBillingSnapshotId_noInvoice_throwsResourceNotFoundException() {
        when(billingSnapshotRepository.existsById(snapshotId)).thenReturn(true);
        when(invoiceRepository.findByBillingSnapshotId(snapshotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoiceByBillingSnapshotId(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("No invoice has been generated for this billing snapshot.");
    }

    // CASE 20 — GET fails correctly when the billing snapshot itself does not exist.
    @Test
    void getInvoiceByBillingSnapshotId_snapshotNotFound_throwsResourceNotFoundException() {
        when(billingSnapshotRepository.existsById(snapshotId)).thenReturn(false);

        assertThatThrownBy(() -> invoiceService.getInvoiceByBillingSnapshotId(snapshotId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Billing snapshot could not be found.");
    }

    private Invoice persistedInvoice(
            String invoiceNumber,
            String snapshotNumber,
            UUID billingSnapshotId,
            String clientName,
            String projectName
    ) {
        return Invoice.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceNumber(invoiceNumber)
                .billingSnapshotId(billingSnapshotId)
                .billingSnapshotNumber(snapshotNumber)
                .taxCalculationId(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .clientName(clientName)
                .projectId(23L)
                .projectName(projectName)
                .billingPeriodStart(LocalDate.of(2026, 6, 1))
                .billingPeriodEnd(LocalDate.of(2026, 8, 30))
                .currencyCode("USD")
                .paymentTermCode("NET_30")
                .subtotal(new BigDecimal("5500.00"))
                .totalTaxAmount(new BigDecimal("990.00"))
                .grandTotal(new BigDecimal("6490.00"))
                .invoiceDate(LocalDate.of(2026, 9, 9))
                .dueDate(LocalDate.of(2026, 10, 9))
                .generatedAt(LocalDateTime.now())
                .status(InvoiceStatus.GENERATED)
                .items(new ArrayList<>())
                .taxComponents(new ArrayList<>())
                .build();
    }

    // LIST CASE 1 — Empty invoice list.
    @Test
    void getAllInvoices_noInvoicesGenerated_returnsEmptyList() {
        when(invoiceRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of());

        List<InvoiceSummaryResponseDto> response = invoiceService.getAllInvoices();

        assertThat(response).isEmpty();
    }

    // LIST CASE 2 — Multiple invoices, every summary field correctly mapped from the persisted Invoice.
    @Test
    void getAllInvoices_multipleInvoices_returnsAllWithCorrectFields() {
        UUID secondSnapshotId = UUID.randomUUID();

        Invoice first = persistedInvoice(
                "INV-20260908170000", "BS-20260908164549", snapshotId,
                "Account Management", "Website Redesign");
        Invoice second = persistedInvoice(
                "INV-20260909091500", "BS-20260909080000", secondSnapshotId,
                "Globex Corp", "Data Migration");

        when(invoiceRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of(first, second));

        List<InvoiceSummaryResponseDto> response = invoiceService.getAllInvoices();

        assertThat(response).hasSize(2);

        InvoiceSummaryResponseDto firstDto = response.get(0);
        assertThat(firstDto.getInvoiceId()).isEqualTo(first.getInvoiceId());
        assertThat(firstDto.getInvoiceNumber()).isEqualTo("INV-20260908170000");
        assertThat(firstDto.getStatus()).isEqualTo(InvoiceStatus.GENERATED);
        assertThat(firstDto.getBillingSnapshotId()).isEqualTo(snapshotId);
        assertThat(firstDto.getBillingSnapshotNumber()).isEqualTo("BS-20260908164549");
        assertThat(firstDto.getClientName()).isEqualTo("Account Management");
        assertThat(firstDto.getProjectName()).isEqualTo("Website Redesign");
        assertThat(firstDto.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(firstDto.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(firstDto.getInvoiceDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(firstDto.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(firstDto.getCurrencyCode()).isEqualTo("USD");
        assertThat(firstDto.getPaymentTermCode()).isEqualTo("NET_30");
        assertThat(firstDto.getSubtotal()).isEqualByComparingTo("5500.00");
        assertThat(firstDto.getTotalTaxAmount()).isEqualByComparingTo("990.00");
        assertThat(firstDto.getGrandTotal()).isEqualByComparingTo("6490.00");

        InvoiceSummaryResponseDto secondDto = response.get(1);
        assertThat(secondDto.getInvoiceNumber()).isEqualTo("INV-20260909091500");
        assertThat(secondDto.getBillingSnapshotId()).isEqualTo(secondSnapshotId);
        assertThat(secondDto.getBillingSnapshotNumber()).isEqualTo("BS-20260909080000");
        assertThat(secondDto.getClientName()).isEqualTo("Globex Corp");
        assertThat(secondDto.getProjectName()).isEqualTo("Data Migration");
    }

    // LIST CASE 3 — Listing is strictly read-only: no invoice is created and no BillingSnapshot is touched.
    @Test
    void getAllInvoices_isReadOnly_neverPersistsOrTouchesBillingSnapshot() {
        when(invoiceRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(
                List.of(persistedInvoice(
                        "INV-20260908170000", "BS-20260908164549", snapshotId,
                        "Account Management", "Website Redesign"))
        );

        invoiceService.getAllInvoices();

        verify(invoiceRepository, never()).save(any());
        verifyNoInteractions(billingSnapshotRepository);
        verifyNoInteractions(taxCalculationRepository);
        verifyNoInteractions(billingConfigurationService);
    }
}
