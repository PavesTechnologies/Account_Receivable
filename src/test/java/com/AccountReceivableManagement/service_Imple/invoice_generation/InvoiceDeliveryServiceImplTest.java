package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDocument;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDocumentRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.service_Imple.email.EmailDeliveryException;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.receivable.ReceivableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * "Send to Client" - still an explicit, manual action. Only a READY_TO_SEND
 * invoice (approved, document finalized) can be sent, and the PDF attached
 * is its stored document, exactly as reviewed - never re-rendered.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceDeliveryServiceImplTest {

    private static final byte[] STORED_PDF = {37, 80, 68, 70, 1, 2, 3};

    @Mock
    private com.AccountReceivableManagement.security.FinancialActionAuthorizer financialActionAuthorizer;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private InvoiceDeliveryRepository invoiceDeliveryRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceDocumentRepository invoiceDocumentRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ReceivableService receivableService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private InvoiceDeliveryServiceImpl invoiceDeliveryService;

    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(financialActionAuthorizer.requirePermission(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.AccountReceivableManagement.security.Actor(2L, "approver@example.com"));
        org.mockito.Mockito.lenient().when(financialActionAuthorizer.currentActor())
                .thenReturn(new com.AccountReceivableManagement.security.Actor(2L, "approver@example.com"));

        // sendInvoice registers an after-commit callback; stand in for the
        // @Transactional boundary that is absent in a plain unit test.
        TransactionSynchronizationManager.initSynchronization();
        invoiceId = UUID.randomUUID();
        lenient().when(invoiceDeliveryRepository.save(any(InvoiceDelivery.class)))
                .thenAnswer(invocation -> {
                    InvoiceDelivery delivery = invocation.getArgument(0);
                    if (delivery.getInvoiceDeliveryId() == null) {
                        delivery.setInvoiceDeliveryId(UUID.randomUUID());
                    }
                    return delivery;
                });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Invoice readyToSendInvoice() {
        return Invoice.builder()
                .invoiceId(invoiceId)
                .invoiceNumber("INV-20260908164549")
                .status(InvoiceStatus.READY_TO_SEND)
                .invoiceDate(LocalDate.of(2026, 9, 8))
                .dueDate(LocalDate.of(2026, 10, 8))
                .clientName("Account Management")
                .email("client@example.com")
                .currencyCode("USD")
                .subtotal(new BigDecimal("5500.00"))
                .totalTaxAmount(new BigDecimal("990.00"))
                .grandTotal(new BigDecimal("6490.00"))
                // Point-in-time seller snapshot, as frozen onto the Invoice
                // at generation time - delivery must use this, never a live
                // Company Profile lookup.
                .sellerLegalName("Example Global Infotech Private Limited")
                .sellerEmail("billing@example.com")
                .sellerPhone("+91 40 1234 5678")
                .build();
    }

    private InvoiceResponseDto invoiceResponse(Invoice invoice) {
        return InvoiceResponseDto.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .clientName(invoice.getClientName())
                .email(invoice.getEmail())
                .currencyCode(invoice.getCurrencyCode())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .totalTaxAmount(invoice.getTotalTaxAmount())
                .grandTotal(invoice.getGrandTotal())
                .sellerLegalName(invoice.getSellerLegalName())
                .sellerEmail(invoice.getSellerEmail())
                .sellerPhone(invoice.getSellerPhone())
                .items(List.of())
                .taxComponents(List.of())
                .build();
    }

    /** The invoice exists, is READY_TO_SEND and its finalized document is stored. */
    private Invoice storedAndFinalized() {
        Invoice invoice = readyToSendInvoice();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        lenient().when(invoiceService.getInvoiceById(invoiceId)).thenReturn(invoiceResponse(invoice));
        lenient().when(invoiceDocumentRepository.existsByInvoiceId(invoiceId)).thenReturn(true);
        lenient().when(invoiceDocumentRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.of(
                InvoiceDocument.builder()
                        .invoiceDocumentId(UUID.randomUUID())
                        .invoiceId(invoiceId)
                        .fileName("invoice-INV-20260908164549.pdf")
                        .contentType("application/pdf")
                        .content(STORED_PDF)
                        .sizeBytes((long) STORED_PDF.length)
                        .checksumSha256("x")
                        .generatedAt(LocalDateTime.now())
                        .build()));
        return invoice;
    }

    private void emailSucceeds() {
        when(emailService.sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString()
        )).thenReturn("<message-id@gmail.com>");
    }

    @Test
    void sendInvoice_readyToSendInvoice_sendsItsStoredDocumentAndMarksSent() {
        Invoice invoice = storedAndFinalized();
        emailSucceeds();

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(result.getRecipientEmail()).isEqualTo("client@example.com");
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getFailureReason()).isNull();
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-20260908164549");

        // The attachment is the stored, reviewed document.
        ArgumentCaptor<byte[]> attachment = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailWithAttachment(eq("client@example.com"), subject.capture(), anyString(),
                attachment.capture(), eq("invoice-INV-20260908164549.pdf"), eq("application/pdf"));
        assertThat(attachment.getValue()).isEqualTo(STORED_PDF);
        // Subject built from the invoice's own seller snapshot - no Company Profile lookup.
        assertThat(subject.getValue()).contains("Example Global Infotech Private Limited");

        // Invoice status itself is never touched by delivery: READY_TO_SEND is not "sent".
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);
        verify(invoiceRepository, never()).save(any());

        // A SENT outcome creates the invoice's receivable - only once the SENT delivery has committed.
        verifyNoInteractions(receivableService);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(receivableService).createReceivableFromInvoice(invoiceId);

        ArgumentCaptor<InvoiceDelivery> captor = ArgumentCaptor.forClass(InvoiceDelivery.class);
        verify(invoiceDeliveryRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(captor.getValue().getProviderMessageId()).isEqualTo("<message-id@gmail.com>");
    }

    @Test
    void sendInvoice_invoiceNotFound_throwsResourceNotFoundException() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);

        verifyNoInteractions(invoiceDeliveryRepository, emailService, invoiceDocumentRepository);
    }

    @Test
    void sendInvoice_legacyInvoice_resolvesEmailFromClient() {
        UUID clientId = UUID.randomUUID();
        Invoice invoice = storedAndFinalized();
        invoice.setClientId(clientId);
        invoice.setEmail(null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(
                Client.builder().clientId(clientId).email("synced-client@example.com").build()
        ));
        emailSucceeds();

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(result.getRecipientEmail()).isEqualTo("synced-client@example.com");
    }

    @Test
    void sendInvoice_invoiceNotApproved_throwsValidationException() {
        for (InvoiceStatus status : List.of(InvoiceStatus.GENERATED, InvoiceStatus.PENDING_APPROVAL,
                InvoiceStatus.REJECTED)) {
            Invoice invoice = readyToSendInvoice();
            invoice.setStatus(status);
            when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

            assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                    .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                    .hasMessage("Invoice must be approved before it can be sent to the client.");
        }

        verifyNoInteractions(invoiceDeliveryRepository, emailService, receivableService);
    }

    @Test
    void sendInvoice_approvedButDocumentNotGenerated_isNotSent() {
        Invoice invoice = readyToSendInvoice();
        invoice.setStatus(InvoiceStatus.APPROVED);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("document has not been generated yet");

        verifyNoInteractions(invoiceDeliveryRepository, emailService, receivableService);
    }

    @Test
    void sendInvoice_readyToSendWithoutStoredDocument_isNotSent() {
        Invoice invoice = readyToSendInvoice();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceDocumentRepository.existsByInvoiceId(invoiceId)).thenReturn(false);

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .hasMessageContaining("document has not been generated yet");

        verifyNoInteractions(invoiceDeliveryRepository, emailService);
    }

    @Test
    void sendInvoice_missingClientEmail_throwsValidationException() {
        Invoice invoice = readyToSendInvoice();
        invoice.setEmail(null);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceDocumentRepository.existsByInvoiceId(invoiceId)).thenReturn(true);

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Client email is not configured for this invoice.");

        verifyNoInteractions(invoiceDeliveryRepository, emailService);
    }

    @Test
    void sendInvoice_storedDocumentUnreadable_marksDeliveryFailedWithoutEmail() {
        storedAndFinalized();
        when(invoiceDocumentRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.FAILED);
        assertThat(result.getFailureReason()).contains("finalized invoice document could not be found");
        verifyNoInteractions(emailService, receivableService);
    }

    @Test
    void sendInvoice_emailServiceFails_marksDeliveryFailedNotSent() {
        storedAndFinalized();
        when(emailService.sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString()
        )).thenThrow(new EmailDeliveryException(
                "Failed to send invoice email through the configured mail provider.",
                new RuntimeException("SMTP auth failed")
        ));

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.FAILED);
        assertThat(result.getFailureReason())
                .isEqualTo("Failed to send invoice email through the configured mail provider.");
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getSentAt()).isNull();

        // A FAILED delivery never creates a receivable.
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verifyNoInteractions(receivableService);
    }

    @Test
    void sendInvoice_repeated_reusesTheSameReceivableCreation() {
        storedAndFinalized();
        emailSucceeds();

        invoiceDeliveryService.sendInvoice(invoiceId);
        invoiceDeliveryService.sendInvoice(invoiceId);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        // Both go through the single, idempotent per-invoice receivable creation
        // (ReceivableServiceImpl returns the existing receivable on a resend).
        verify(receivableService, times(2)).createReceivableFromInvoice(invoiceId);
        verify(receivableService, never()).createReceivableFromInvoice(argThat(id -> !invoiceId.equals(id)));
    }

    @Test
    void getDeliveryHistory_returnsAttemptsNewestFirst() {
        Invoice invoice = readyToSendInvoice();

        InvoiceDelivery latest = InvoiceDelivery.builder()
                .invoiceDeliveryId(UUID.randomUUID())
                .invoiceId(invoiceId)
                .recipientEmail("client@example.com")
                .status(InvoiceDeliveryStatus.SENT)
                .build();

        InvoiceDelivery earlier = InvoiceDelivery.builder()
                .invoiceDeliveryId(UUID.randomUUID())
                .invoiceId(invoiceId)
                .recipientEmail("client@example.com")
                .status(InvoiceDeliveryStatus.FAILED)
                .failureReason("SMTP timeout")
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceDeliveryRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId))
                .thenReturn(List.of(latest, earlier));

        List<InvoiceDeliveryResponseDto> history = invoiceDeliveryService.getDeliveryHistory(invoiceId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(history.get(1).getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.FAILED);
        assertThat(history.get(1).getFailureReason()).isEqualTo("SMTP timeout");
        assertThat(history).allSatisfy(d -> assertThat(d.getInvoiceNumber()).isEqualTo("INV-20260908164549"));
    }

    @Test
    void getDeliveryHistory_invoiceNotFound_throwsResourceNotFoundException() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceDeliveryService.getDeliveryHistory(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }
}
