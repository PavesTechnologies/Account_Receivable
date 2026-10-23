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
                        delivery.setCreatedAt(LocalDateTime.now());
                    }
                    deliveries.put(delivery.getInvoiceDeliveryId(), delivery);
                    return delivery;
                });
        lenient().when(invoiceDeliveryRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(deliveries.get(invocation.<UUID>getArgument(0))));
        lenient().when(invoiceDeliveryRepository.existsByInvoiceIdAndStatus(any(UUID.class), any()))
                .thenAnswer(invocation -> deliveries.values().stream().anyMatch(d ->
                        d.getInvoiceId().equals(invocation.getArgument(0))
                                && d.getStatus() == invocation.getArgument(1)));
        lenient().when(invoiceDeliveryRepository.existsByInvoiceIdAndStatusIn(any(UUID.class), any()))
                .thenAnswer(invocation -> deliveries.values().stream().anyMatch(d ->
                        d.getInvoiceId().equals(invocation.getArgument(0))
                                && invocation.<java.util.Collection<?>>getArgument(1).contains(d.getStatus())));
    }

    private final java.util.Map<UUID, InvoiceDelivery> deliveries = new java.util.LinkedHashMap<>();

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
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        lenient().when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
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

        // A SENT outcome (recorded in its own transaction) creates the invoice's receivable.
        verify(receivableService).createReceivableFromInvoice(invoiceId);
        assertThat(result.getRequestedBy()).isEqualTo("approver@example.com");

        ArgumentCaptor<InvoiceDelivery> captor = ArgumentCaptor.forClass(InvoiceDelivery.class);
        verify(invoiceDeliveryRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(captor.getValue().getProviderMessageId()).isEqualTo("<message-id@gmail.com>");
    }

    @Test
    void sendInvoice_invoiceNotFound_throwsResourceNotFoundException() {
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.empty());

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
            when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

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
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("document has not been generated yet");

        verifyNoInteractions(invoiceDeliveryRepository, emailService, receivableService);
    }

    @Test
    void sendInvoice_readyToSendWithoutStoredDocument_isNotSent() {
        Invoice invoice = readyToSendInvoice();
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceDocumentRepository.existsByInvoiceId(invoiceId)).thenReturn(false);

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .hasMessageContaining("document has not been generated yet");

        verifyNoInteractions(invoiceDeliveryRepository, emailService);
    }

    @Test
    void sendInvoice_missingClientEmail_throwsValidationException() {
        Invoice invoice = readyToSendInvoice();
        invoice.setEmail(null);
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
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
        verifyNoInteractions(receivableService);
    }

    // G-19: an invoice is delivered once. A second ordinary send is refused before
    // anything is sent - no second email, no second delivery row, one receivable creation.
    @Test
    void sendInvoice_secondOrdinarySend_isRefused_noSecondEmail() {
        storedAndFinalized();
        emailSucceeds();

        invoiceDeliveryService.sendInvoice(invoiceId);
        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("already been sent");

        verify(emailService, times(1)).sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString());
        verify(receivableService, times(1)).createReceivableFromInvoice(invoiceId);
        assertThat(deliveries).hasSize(1);
    }

    // G-19: after a definite failure the invoice may be sent again.
    @Test
    void sendInvoice_afterAFailedAttempt_canBeSentAgain() {
        storedAndFinalized();
        when(emailService.sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString()))
                .thenThrow(new EmailDeliveryException("Failed to send invoice email through the configured mail provider.",
                        new java.net.ConnectException("Connection refused")))
                .thenReturn("<message-id@gmail.com>");

        assertThat(invoiceDeliveryService.sendInvoice(invoiceId).getDeliveryStatus())
                .isEqualTo(InvoiceDeliveryStatus.FAILED);
        assertThat(invoiceDeliveryService.sendInvoice(invoiceId).getDeliveryStatus())
                .isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(deliveries.values()).extracting(InvoiceDelivery::getStatus)
                .containsExactly(InvoiceDeliveryStatus.FAILED, InvoiceDeliveryStatus.SENT);
    }

    // G-19/G-20: a read timeout after the message was handed over may mean it was
    // delivered - recorded UNCERTAIN, no receivable, and no further ordinary send.
    @Test
    void sendInvoice_smtpReadTimeout_isUncertain_andBlocksAnotherSend() {
        storedAndFinalized();
        when(emailService.sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString()))
                .thenThrow(new EmailDeliveryException("Failed to send invoice email through the configured mail provider.",
                        new RuntimeException(new java.net.SocketTimeoutException("Read timed out"))));

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.UNCERTAIN);
        verifyNoInteractions(receivableService);
        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("outcome is unknown");
        verify(emailService, times(1)).sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString());
    }

    // Phase 4A (AUD-4A-03): JavaMailSenderImpl reports a read timeout after the
    // message body as MailSendException(failedMessages) with NO cause - the
    // timeout is only inside getFailedMessages() (verified against Spring 6.1.6
    // with a loopback SMTP server). It must still be UNCERTAIN, never resendable.
    @Test
    void sendInvoice_smtpReadTimeoutAsSpringReportsIt_isUncertain_andBlocksAnotherSend() {
        storedAndFinalized();
        java.util.Map<Object, Exception> failedMessages = new java.util.LinkedHashMap<>();
        failedMessages.put("message", new jakarta.mail.MessagingException("Exception reading response",
                new java.net.SocketTimeoutException("Read timed out")));
        when(emailService.sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString()))
                .thenThrow(new EmailDeliveryException("Failed to send invoice email through the configured mail provider.",
                        new org.springframework.mail.MailSendException(failedMessages)));

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.UNCERTAIN);
        verifyNoInteractions(receivableService);
        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("outcome is unknown");
    }

    // A connection that was never made sent nothing, however Spring wraps it.
    @Test
    void sendInvoice_connectTimeoutInsideFailedMessages_isFailed() {
        storedAndFinalized();
        java.util.Map<Object, Exception> failedMessages = new java.util.LinkedHashMap<>();
        failedMessages.put("message", new jakarta.mail.MessagingException("Couldn't connect to host",
                new java.net.SocketTimeoutException("Connect timed out")));
        when(emailService.sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString()))
                .thenThrow(new EmailDeliveryException("Failed to send invoice email through the configured mail provider.",
                        new org.springframework.mail.MailSendException(failedMessages)));

        assertThat(invoiceDeliveryService.sendInvoice(invoiceId).getDeliveryStatus())
                .isEqualTo(InvoiceDeliveryStatus.FAILED);
    }

    // G-20: the email left but recording SENT failed - the delivery stays PENDING
    // (an unknown outcome), the caller is told not to resend, and a resend is refused.
    @Test
    void sendInvoice_outcomeCannotBeRecorded_staysPendingAndIsNotResent() {
        storedAndFinalized();
        emailSucceeds();
        when(invoiceDeliveryRepository.save(any(InvoiceDelivery.class)))
                .thenAnswer(invocation -> {
                    InvoiceDelivery delivery = invocation.getArgument(0);
                    if (delivery.getInvoiceDeliveryId() != null) {
                        throw new org.springframework.dao.DataAccessResourceFailureException("database down");
                    }
                    delivery.setInvoiceDeliveryId(UUID.randomUUID());
                    delivery.setCreatedAt(LocalDateTime.now());
                    deliveries.put(delivery.getInvoiceDeliveryId(),
                            InvoiceDelivery.builder().invoiceDeliveryId(delivery.getInvoiceDeliveryId())
                                    .invoiceId(delivery.getInvoiceId()).recipientEmail(delivery.getRecipientEmail())
                                    .status(delivery.getStatus()).createdAt(delivery.getCreatedAt()).build());
                    return delivery;
                });
        // Like the database: a read returns a copy, so a failed save leaves the row as it was.
        when(invoiceDeliveryRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(deliveries.get(invocation.<UUID>getArgument(0))).map(row ->
                        InvoiceDelivery.builder().invoiceDeliveryId(row.getInvoiceDeliveryId())
                                .invoiceId(row.getInvoiceId()).recipientEmail(row.getRecipientEmail())
                                .status(row.getStatus()).createdAt(row.getCreatedAt()).build()));

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Do not send it again");

        assertThat(deliveries.values()).extracting(InvoiceDelivery::getStatus)
                .containsExactly(InvoiceDeliveryStatus.PENDING);
        verifyNoInteractions(receivableService);
        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .hasMessageContaining("outcome is unknown");
        verify(emailService, times(1)).sendEmailWithAttachment(
                anyString(), anyString(), anyString(), any(byte[].class), anyString(), anyString());
    }

    // G-20: never inside a caller's transaction - the email would leave before its outcome is known.
    @Test
    void sendInvoice_insideATransaction_isRefusedBeforeAnythingHappens() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(invoiceDeliveryRepository, emailService);
    }

    // Uncertain outcome resolved by an operator as SENT: the receivable is created, no email is sent.
    @Test
    void resolveDelivery_uncertainResolvedAsSent_createsTheReceivable_withoutSending() {
        storedAndFinalized();
        InvoiceDelivery uncertain = invoiceDeliveryRepository.save(InvoiceDelivery.builder()
                .invoiceId(invoiceId).recipientEmail("client@example.com")
                .status(InvoiceDeliveryStatus.UNCERTAIN).build());

        InvoiceDeliveryResponseDto resolved = invoiceDeliveryService.resolveDelivery(
                uncertain.getInvoiceDeliveryId(), InvoiceDeliveryStatus.SENT, "Found in the Sent folder");

        assertThat(resolved.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(resolved.getResolvedBy()).isEqualTo("approver@example.com");
        assertThat(resolved.getResolutionReason()).isEqualTo("Found in the Sent folder");
        verify(receivableService).createReceivableFromInvoice(invoiceId);
        verifyNoInteractions(emailService);
    }

    // A PENDING delivery that may still be in flight cannot be resolved.
    @Test
    void resolveDelivery_recentPending_isRefused() {
        storedAndFinalized();
        InvoiceDelivery pending = invoiceDeliveryRepository.save(InvoiceDelivery.builder()
                .invoiceId(invoiceId).recipientEmail("client@example.com")
                .status(InvoiceDeliveryStatus.PENDING).build());

        assertThatThrownBy(() -> invoiceDeliveryService.resolveDelivery(
                pending.getInvoiceDeliveryId(), InvoiceDeliveryStatus.FAILED, "not received"))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessageContaining("may still be in progress");
        assertThat(deliveries.get(pending.getInvoiceDeliveryId()).getStatus())
                .isEqualTo(InvoiceDeliveryStatus.PENDING);
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
