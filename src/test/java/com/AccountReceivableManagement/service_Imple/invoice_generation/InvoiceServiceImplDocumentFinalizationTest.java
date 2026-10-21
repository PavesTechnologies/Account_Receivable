package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRun;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDocument;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceItem;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceTaxComponent;
import com.AccountReceivableManagement.entity_enums.automatic_billing.AutomaticBillingRunStatus;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.automatic_billing.AutomaticBillingRunRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceApprovalHistoryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDocumentRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunService;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 10: approval, then document finalization (APPROVED -> READY_TO_SEND)
 * through the existing PDF renderer, with the PDF stored in the database.
 * Uses the real {@link InvoiceDocumentServiceImpl}, so the stored document's
 * content is checked, not just its presence.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceImplDocumentFinalizationTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceApprovalHistoryRepository historyRepository;
    @Mock private RecordActionLockService lockService;
    @Mock private InvoiceDocumentRepository documentRepository;
    @Mock private AutomaticBillingRunRepository runRepository;
    @Mock private AutomaticBillingRunService runService;

    private final UUID invoiceId = UUID.randomUUID();
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoice = invoice(InvoiceStatus.APPROVED);
        when(invoiceRepository.findById(invoiceId)).thenAnswer(i -> Optional.of(invoice));
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenAnswer(i -> Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.saveAndFlush(any(InvoiceDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Permits every action, attributed to an approver who is not the (system) maker. */
    private static com.AccountReceivableManagement.security.FinancialActionAuthorizer permittingAuthorizer() {
        com.AccountReceivableManagement.security.FinancialActionAuthorizer authorizer = org.mockito.Mockito.mock(com.AccountReceivableManagement.security.FinancialActionAuthorizer.class);
        org.mockito.Mockito.lenient().when(authorizer.requirePermission(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.AccountReceivableManagement.security.Actor(2L, "approver@example.com"));
        org.mockito.Mockito.lenient().when(authorizer.currentActor())
                .thenReturn(new com.AccountReceivableManagement.security.Actor(2L, "approver@example.com"));
        return authorizer;
    }

    private InvoiceServiceImpl service(InvoiceDocumentService renderer) {
        return new InvoiceServiceImpl(invoiceRepository, historyRepository, null, null, null, null, null, null,
                lockService, null, null, renderer, documentRepository, runRepository, runService,
                permittingAuthorizer(), null);
    }

    private InvoiceServiceImpl service() {
        return service(new InvoiceDocumentServiceImpl());
    }

    /** A T&amp;M invoice: Developer 8h x 1500 + Senior Developer 5h x 2000, 9% CGST + 9% SGST. */
    private Invoice invoice(InvoiceStatus status) {
        Invoice built = Invoice.builder()
                .invoiceId(invoiceId)
                .invoiceNumber("INV-20261101093000")
                .status(status)
                .clientName("Acme Retail Pvt Ltd")
                .email("ap@acme.example")
                .projectId(42L)
                .projectName("Store Modernisation")
                .billingPeriodStart(LocalDate.of(2026, 10, 1))
                .billingPeriodEnd(LocalDate.of(2026, 10, 31))
                .currencyCode("INR")
                .invoiceDate(LocalDate.of(2026, 11, 1))
                .dueDate(LocalDate.of(2026, 12, 1))
                .sellerLegalName("Paves Technologies Pvt Ltd")
                .subtotal(new BigDecimal("22000.00"))
                .totalTaxAmount(new BigDecimal("3960.00"))
                .grandTotal(new BigDecimal("25960.00"))
                .generatedAt(LocalDateTime.of(2026, 11, 1, 9, 30))
                .items(new ArrayList<>())
                .taxComponents(new ArrayList<>())
                .build();
        built.getItems().add(line(built, "Asha Rao", "Developer", "8", "1500.00", "12000.00"));
        built.getItems().add(line(built, "Ravi Kumar", "Senior Developer", "5", "2000.00", "10000.00"));
        built.getTaxComponents().add(tax(built, "CGST"));
        built.getTaxComponents().add(tax(built, "SGST"));
        return built;
    }

    private static InvoiceItem line(Invoice invoice, String name, String role, String hours, String rate,
            String amount) {
        return InvoiceItem.builder()
                .invoiceItemId(UUID.randomUUID())
                .invoice(invoice)
                .itemType(BillingItemType.TIME_ENTRY)
                .itemName(name)
                .role(role)
                .sourceReferenceId("TS-" + name.hashCode())
                .quantity(new BigDecimal(hours))
                .rate(new BigDecimal(rate))
                .amount(new BigDecimal(amount))
                .workDate(LocalDate.of(2026, 10, 15))
                .build();
    }

    private static InvoiceTaxComponent tax(Invoice invoice, String code) {
        return InvoiceTaxComponent.builder()
                .invoiceTaxComponentId(UUID.randomUUID())
                .invoice(invoice)
                .taxTypeId(UUID.randomUUID())
                .taxTypeCode(code)
                .taxTypeName(code)
                .appliedRate(new BigDecimal("9.00"))
                .taxAmount(new BigDecimal("1980.00"))
                .build();
    }

    private static String pdfText(byte[] pdf) throws java.io.IOException {
        try (com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf)) {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                    new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    // ================================================================ finalization

    @Test
    void approvedInvoice_isFinalizedToReadyToSend_withItsDocumentStored() throws Exception {
        InvoiceResponseDto result = service().finalizeInvoiceDocument(invoiceId);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);
        assertThat(result.isDocumentAvailable()).isTrue();
        assertThat(result.getDocumentUrl()).isEqualTo("/api/v1/invoices/" + invoiceId + "/document");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);

        ArgumentCaptor<InvoiceDocument> stored = ArgumentCaptor.forClass(InvoiceDocument.class);
        verify(documentRepository).saveAndFlush(stored.capture());
        InvoiceDocument document = stored.getValue();
        assertThat(document.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(document.getFileName()).isEqualTo("invoice-INV-20261101093000.pdf");
        assertThat(document.getContentType()).isEqualTo("application/pdf");
        assertThat(document.getSizeBytes()).isEqualTo(document.getContent().length);
        assertThat(document.getChecksumSha256()).hasSize(64);

        // The stored document carries the invoice's own lines, tax breakdown and totals.
        String text = pdfText(document.getContent());
        assertThat(text).contains("INV-20261101093000", "Acme Retail Pvt Ltd",
                "Asha Rao", "Ravi Kumar", "CGST", "SGST", "12000.00", "10000.00", "22000.00", "1980.00", "3960.00", "25960.00");
    }

    @Test
    void alreadyFinalizedInvoice_isReturnedWithoutGeneratingAnotherDocument() {
        invoice.setStatus(InvoiceStatus.READY_TO_SEND);
        when(documentRepository.existsByInvoiceId(invoiceId)).thenReturn(true);
        InvoiceDocumentService renderer = mock(InvoiceDocumentService.class);

        InvoiceResponseDto result = service(renderer).finalizeInvoiceDocument(invoiceId);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);
        verifyNoInteractions(renderer);
        verify(documentRepository, never()).saveAndFlush(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void documentGenerationFailure_leavesTheInvoiceApprovedWithoutADocument() {
        InvoiceDocumentService renderer = mock(InvoiceDocumentService.class);
        when(renderer.generateInvoicePdf(any()))
                .thenThrow(new PdfGenerationException("Rendering failed.", new RuntimeException("font")));

        assertThatThrownBy(() -> service(renderer).finalizeInvoiceDocument(invoiceId))
                .isInstanceOf(PdfGenerationException.class);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        verify(documentRepository, never()).saveAndFlush(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void concurrentFinalizationThatStoredFirst_isReportedAndStatusIsLeftAlone() {
        when(documentRepository.saveAndFlush(any(InvoiceDocument.class)))
                .thenThrow(new DataIntegrityViolationException("uk_invoice_document_invoice"));

        assertThatThrownBy(() -> service().finalizeInvoiceDocument(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.DuplicateResourceException.class);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void invoiceThatIsNotApproved_cannotBeFinalized() {
        for (InvoiceStatus status : List.of(InvoiceStatus.REJECTED, InvoiceStatus.PENDING_APPROVAL,
                InvoiceStatus.GENERATED)) {
            invoice.setStatus(status);

            assertThatThrownBy(() -> service().finalizeInvoiceDocument(invoiceId))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessageContaining("Only an approved invoice");
            assertThat(invoice.getStatus()).isEqualTo(status);
        }
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void incompleteInvoice_isNotFinalized() {
        invoice.setCurrencyCode(null);
        invoice.getItems().clear();

        assertThatThrownBy(() -> service().finalizeInvoiceDocument(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("currency")
                .hasMessageContaining("line items");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    void totalsThatDoNotReconcile_areNotFinalized() {
        invoice.getItems().get(0).setAmount(new BigDecimal("13000.00"));

        assertThatThrownBy(() -> service().finalizeInvoiceDocument(invoiceId))
                .hasMessageContaining("line items total 23000.00 does not match the subtotal 22000.00");

        invoice.getItems().get(0).setAmount(new BigDecimal("12000.00"));
        invoice.getTaxComponents().get(0).setTaxAmount(new BigDecimal("1000.00"));
        assertThatThrownBy(() -> service().finalizeInvoiceDocument(invoiceId))
                .hasMessageContaining("tax breakdown does not add up");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }

    // ================================================================ document access

    @Test
    void document_isOnlyServedOnceFinalized() {
        assertThatThrownBy(() -> service().getInvoiceDocument(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);

        invoice.setStatus(InvoiceStatus.READY_TO_SEND);
        InvoiceDocument stored = InvoiceDocument.builder().invoiceId(invoiceId).content(new byte[]{1}).build();
        when(documentRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.of(stored));

        assertThat(service().getInvoiceDocument(invoiceId)).isSameAs(stored);
    }

    @Test
    void approvedInvoice_reportsNoDocumentYet() {
        InvoiceResponseDto approved = service().getInvoiceById(invoiceId);

        assertThat(approved.isDocumentAvailable()).isFalse();
        assertThat(approved.getDocumentUrl()).isNull();
    }

    // ================================================================ approval

    @Test
    void approval_requiresTheApprovalLock_andChangesNothingWithoutIt() {
        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);
        doThrow(new IllegalStateException("Invoice is locked by another user."))
                .when(lockService).validateLockOwner(LockResourceType.INVOICE, invoiceId,com.AccountReceivableManagement.entity_enums.common.LockActionType.APPROVAL);

        assertThatThrownBy(() -> service().approveInvoice(invoiceId))
                .hasMessageContaining("locked by another user");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void rejectedInvoice_cannotBeApproved() {
        invoice.setStatus(InvoiceStatus.REJECTED);

        assertThatThrownBy(() -> service().approveInvoice(invoiceId))
                .hasMessage("Only invoices pending approval can be approved.");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.REJECTED);
    }

    @Test
    void approval_onlyApproves_andCompletesTheAutomaticRunOnceCommitted() {
        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);
        UUID runId = UUID.randomUUID();
        when(runRepository.findAllByInvoiceId(invoiceId)).thenReturn(List.of(AutomaticBillingRun.builder()
                .automaticBillingRunId(runId)
                .invoiceId(invoiceId)
                .status(AutomaticBillingRunStatus.PENDING_APPROVAL)
                .build()));
        TransactionSynchronizationManager.initSynchronization();
        InvoiceDocumentService renderer = mock(InvoiceDocumentService.class);

        InvoiceResponseDto result = service(renderer).approveInvoice(invoiceId);

        // Approval itself never generates or sends anything.
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        verifyNoInteractions(renderer);
        verify(documentRepository, never()).saveAndFlush(any());
        // The run is completed only after the approval has committed.
        verifyNoInteractions(runService);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(runService).completeAfterApproval(runId);
    }

    @Test
    void approval_runCompletionFailure_doesNotAffectTheApproval() {
        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);
        when(runRepository.findAllByInvoiceId(invoiceId)).thenThrow(new RuntimeException("database unavailable"));
        TransactionSynchronizationManager.initSynchronization();

        InvoiceResponseDto result = service().approveInvoice(invoiceId);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }
}
