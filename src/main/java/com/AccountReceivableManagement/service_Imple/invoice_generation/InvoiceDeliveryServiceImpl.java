package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.security.Actor;
import com.AccountReceivableManagement.security.FinancialAction;
import com.AccountReceivableManagement.security.FinancialActionAuthorizer;
import com.AccountReceivableManagement.service_Imple.email.EmailDeliveryException;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDeliveryService;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDocument;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDocumentRepository;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import com.AccountReceivableManagement.service_interface.receivable.ReceivableService;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates one "Send to Client" attempt in three steps, never holding a
 * database transaction across the SMTP call (G-20):
 * <ol>
 *   <li>claim - under the invoice's row lock: validate (READY_TO_SEND,
 *       document stored, data, recipient), refuse if a delivery is SENT,
 *       PENDING or UNCERTAIN (G-19), commit a PENDING delivery intent;</li>
 *   <li>send - load the stored PDF and email it, outside any transaction;</li>
 *   <li>record - in its own transaction: SENT, FAILED, or UNCERTAIN when the
 *       mail server may have accepted the message.</li>
 * </ol>
 * The PDF attached is the one stored when the invoice was finalized - exactly
 * what was reviewed - never re-rendered here. SMTP gives no idempotency key,
 * so exactly-once delivery cannot be guaranteed: an outcome that is not
 * known (UNCERTAIN, or PENDING left behind) blocks further ordinary sends
 * until an operator resolves it, rather than risking a duplicate email.
 * Never marks {@link Invoice#getStatus()} as sent - delivery state lives on
 * {@link InvoiceDelivery}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceDeliveryServiceImpl implements InvoiceDeliveryService {

    /** A delivery in one of these blocks another ordinary send of the invoice. */
    static final Set<InvoiceDeliveryStatus> BLOCKING_STATUSES =
            EnumSet.of(InvoiceDeliveryStatus.PENDING, InvoiceDeliveryStatus.SENT, InvoiceDeliveryStatus.UNCERTAIN);

    private final InvoiceRepository invoiceRepository;

    private final ClientRepository clientRepository;

    private final InvoiceDeliveryRepository invoiceDeliveryRepository;

    private final InvoiceService invoiceService;

    private final InvoiceDocumentRepository invoiceDocumentRepository;

    private final EmailService emailService;

    private final ReceivableService receivableService;

    private final PlatformTransactionManager transactionManager;

    private final FinancialActionAuthorizer financialActionAuthorizer;

    /**
     * How long a PENDING delivery is considered in flight. Longer than every
     * SMTP timeout together; after it, a PENDING row is an unknown outcome an
     * operator may resolve.
     */
    @Value("${ar.invoice-delivery.pending-timeout:PT10M}")
    private Duration pendingTimeout = Duration.ofMinutes(10);

    private record Claim(UUID deliveryId, String recipientEmail, String invoiceNumber) {
    }

    @Override
    public InvoiceDeliveryResponseDto sendInvoice(UUID invoiceId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Invoice delivery must not run inside a database transaction: "
                    + "the email would be sent before the transaction's outcome is known.");
        }

        Actor actor = financialActionAuthorizer.requirePermission(FinancialAction.INVOICE_DELIVERY);

        // 1. Claim: committed before any email is sent. A database failure here sends nothing.
        Claim claim = transaction().execute(status -> {
            Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Invoice could not be found."));
            String recipientEmail = resolveRecipientEmail(invoice);
            validateInvoiceReadyForDelivery(invoice, recipientEmail);
            requireNoOpenOrCompletedDelivery(invoiceId);

            InvoiceDelivery delivery = invoiceDeliveryRepository.save(InvoiceDelivery.builder()
                    .invoiceId(invoice.getInvoiceId())
                    .recipientEmail(recipientEmail)
                    .status(InvoiceDeliveryStatus.PENDING)
                    .retryCount(0)
                    .requestedBy(actor.label())
                    .requestedByUserId(actor.userId())
                    .build());
            return new Claim(delivery.getInvoiceDeliveryId(), recipientEmail, invoice.getInvoiceNumber());
        });

        // 2. Send: outside any transaction.
        InvoiceResponseDto invoiceResponse = invoiceService.getInvoiceById(invoiceId);
        InvoiceDocument document = invoiceDocumentRepository.findByInvoiceId(invoiceId).orElse(null);
        if (document == null || document.getContent() == null || document.getContent().length == 0) {
            return recordOutcome(claim, InvoiceDeliveryStatus.FAILED, null,
                    "The finalized invoice document could not be found.");
        }

        String messageId;
        try {
            messageId = emailService.sendEmailWithAttachment(
                    claim.recipientEmail(),
                    buildSubject(invoiceResponse),
                    buildBody(invoiceResponse),
                    document.getContent(),
                    document.getFileName(),
                    "application/pdf"
            );
        } catch (EmailDeliveryException ex) {
            if (mayHaveBeenAccepted(ex)) {
                return recordOutcome(claim, InvoiceDeliveryStatus.UNCERTAIN, null,
                        "The mail server did not confirm the email in time; it may have been delivered. "
                                + "Check the sending mailbox and resolve this delivery - do not send again.");
            }
            return recordOutcome(claim, InvoiceDeliveryStatus.FAILED, null, ex.getMessage());
        }

        // 3. Record SENT, then the receivable (its own transaction).
        InvoiceDeliveryResponseDto sent = recordOutcome(claim, InvoiceDeliveryStatus.SENT, messageId, null);
        createReceivable(invoiceId, claim.invoiceNumber());
        return sent;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDeliveryResponseDto> getDeliveryHistory(UUID invoiceId) {

        Invoice invoice =
                invoiceRepository.findById(invoiceId)
                        .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                                "Invoice could not be found."
                        ));

        return invoiceDeliveryRepository
                .findByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .stream()
                .map(delivery -> mapToResponse(delivery, invoice.getInvoiceNumber()))
                .toList();
    }

    @Override
    public InvoiceDeliveryResponseDto resolveDelivery(UUID deliveryId, InvoiceDeliveryStatus outcome, String reason) {

        Actor actor = financialActionAuthorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY);
        if (outcome != InvoiceDeliveryStatus.SENT && outcome != InvoiceDeliveryStatus.FAILED) {
            throw new GlobalExceptionHandler.ValidationException("A delivery can only be resolved to SENT or FAILED.");
        }
        if (reason == null || reason.isBlank()) {
            throw new GlobalExceptionHandler.ValidationException("A reason is required to resolve a delivery.");
        }
        String why = reason.trim().length() > 500 ? reason.trim().substring(0, 500) : reason.trim();

        InvoiceDelivery resolved = transaction().execute(status -> {
            InvoiceDelivery current = invoiceDeliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Invoice delivery could not be found."));
            // Same lock as sendInvoice: no send can claim while the outcome is being resolved.
            invoiceRepository.findByIdForUpdate(current.getInvoiceId())
                    .orElseThrow(() -> new GlobalExceptionHandler.ResourceNotFoundException(
                            "Invoice could not be found."));
            InvoiceDelivery delivery = invoiceDeliveryRepository.findById(deliveryId).orElseThrow();

            LocalDateTime now = LocalDateTime.now();
            boolean unknownOutcome = delivery.getStatus() == InvoiceDeliveryStatus.UNCERTAIN
                    || (delivery.getStatus() == InvoiceDeliveryStatus.PENDING
                    && delivery.getCreatedAt() != null
                    && delivery.getCreatedAt().plus(pendingTimeout).isBefore(now));
            if (!unknownOutcome) {
                throw new GlobalExceptionHandler.ValidationException(
                        delivery.getStatus() == InvoiceDeliveryStatus.PENDING
                                ? "This delivery may still be in progress; it can be resolved once it has been "
                                + "pending for " + pendingTimeout.toMinutes() + " minutes."
                                : "Only a delivery whose outcome is unknown can be resolved; this one is "
                                + delivery.getStatus() + ".");
            }

            delivery.setStatus(outcome);
            if (outcome == InvoiceDeliveryStatus.SENT) {
                // The attempt time: when the email left, as far as is known.
                delivery.setSentAt(delivery.getCreatedAt());
            } else {
                delivery.setFailedAt(now);
                delivery.setFailureReason("Resolved as not delivered: " + why);
            }
            delivery.setResolvedAt(now);
            delivery.setResolvedBy(actor.label());
            delivery.setResolvedByUserId(actor.userId());
            delivery.setResolutionReason(why);
            return invoiceDeliveryRepository.save(delivery);
        });

        log.warn("[InvoiceDeliveryResolved] delivery={} invoice={} outcome={} by={}", deliveryId,
                resolved.getInvoiceId(), outcome, actor.label());

        Invoice invoice = invoiceRepository.findById(resolved.getInvoiceId()).orElse(null);
        String invoiceNumber = invoice != null ? invoice.getInvoiceNumber() : null;
        if (outcome == InvoiceDeliveryStatus.SENT) {
            createReceivable(resolved.getInvoiceId(), invoiceNumber);
        }
        return mapToResponse(resolved, invoiceNumber);
    }

    // ================================================================ steps

    /**
     * G-19: one ordinary delivery per invoice. SENT means already delivered;
     * PENDING/UNCERTAIN mean a send is in progress or may have succeeded.
     * Only when every earlier attempt FAILED may the invoice be sent again.
     */
    private void requireNoOpenOrCompletedDelivery(UUID invoiceId) {
        if (invoiceDeliveryRepository.existsByInvoiceIdAndStatus(invoiceId, InvoiceDeliveryStatus.SENT)) {
            throw new GlobalExceptionHandler.ValidationException(
                    "This invoice has already been sent to the client; it is not sent again.");
        }
        if (invoiceDeliveryRepository.existsByInvoiceIdAndStatusIn(invoiceId,
                EnumSet.of(InvoiceDeliveryStatus.PENDING, InvoiceDeliveryStatus.UNCERTAIN))) {
            throw new GlobalExceptionHandler.ValidationException(
                    "A delivery of this invoice is in progress or its outcome is unknown; it must be resolved "
                            + "before the invoice can be sent again.");
        }
    }

    /**
     * Records the attempt's outcome in its own transaction. If that fails
     * after the email was handed to the server, the row stays PENDING - an
     * unknown outcome that blocks another send until it is resolved.
     */
    private InvoiceDeliveryResponseDto recordOutcome(Claim claim, InvoiceDeliveryStatus outcome, String messageId,
            String failureReason) {
        InvoiceDelivery saved;
        try {
            saved = transaction().execute(status -> {
                InvoiceDelivery delivery = invoiceDeliveryRepository.findById(claim.deliveryId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Delivery " + claim.deliveryId() + " disappeared."));
                LocalDateTime now = LocalDateTime.now();
                delivery.setStatus(outcome);
                if (outcome == InvoiceDeliveryStatus.SENT) {
                    delivery.setSentAt(now);
                    delivery.setProviderMessageId(messageId);
                } else {
                    delivery.setFailedAt(now);
                    delivery.setFailureReason(truncate(failureReason));
                }
                return invoiceDeliveryRepository.save(delivery);
            });
        } catch (RuntimeException ex) {
            log.error("[InvoiceDeliveryOutcomeNotRecorded] delivery={} outcome={}: the outcome could not be "
                    + "recorded; the delivery stays PENDING and must be resolved - do not send again.",
                    claim.deliveryId(), outcome, ex);
            Metrics.counter("ar.invoice.delivery.outcomes", "outcome", "UNRECORDED").increment();
            throw new IllegalStateException("The invoice email " + (outcome == InvoiceDeliveryStatus.SENT
                    ? "was sent" : "may have been sent") + ", but its outcome could not be recorded. "
                    + "Do not send it again; the delivery must be resolved by operations.", ex);
        }

        Metrics.counter("ar.invoice.delivery.outcomes", "outcome", outcome.name()).increment();
        if (outcome == InvoiceDeliveryStatus.FAILED) {
            Metrics.counter("ar.invoice.delivery.failures").increment();
            log.warn("[InvoiceDeliveryFailed] delivery={} invoice={}", claim.deliveryId(), saved.getInvoiceId());
        } else if (outcome == InvoiceDeliveryStatus.UNCERTAIN) {
            log.warn("[InvoiceDeliveryUncertain] delivery={} invoice={} - resolve before sending again",
                    claim.deliveryId(), saved.getInvoiceId());
        }
        return mapToResponse(saved, claim.invoiceNumber());
    }

    /**
     * Creates the invoice's receivable once SENT is committed, in its own
     * transaction (none is active here). The email has already left, so a
     * failure must not undo SENT: the receivable reconciliation creates it
     * later without sending the invoice again (G-21).
     */
    private void createReceivable(UUID invoiceId, String invoiceNumber) {
        try {
            receivableService.createReceivableFromInvoice(invoiceId);
        } catch (RuntimeException ex) {
            Metrics.counter("ar.receivable.creation.failures").increment();
            log.error("[ReceivableCreationFailed] invoice={} ({}) was sent but its receivable could not be "
                    + "created; receivable reconciliation will retry - do not resend", invoiceId, invoiceNumber, ex);
        }
    }

    /**
     * Whether the mail server may have accepted the message although the
     * send failed: a read timeout while waiting for its reply. A connection
     * that was never established (connect timeout, refused, authentication)
     * definitely sent nothing.
     * <p>
     * JavaMailSenderImpl reports a failure while sending (as opposed to while
     * connecting) as a {@code MailSendException} without a cause: the
     * underlying exception is only in {@code getFailedMessages()}, so those
     * are inspected too.
     */
    static boolean mayHaveBeenAccepted(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException timeout) {
                String message = timeout.getMessage();
                return message == null || !message.toLowerCase().contains("connect");
            }
            if (cause instanceof java.net.ConnectException
                    || cause instanceof org.springframework.mail.MailAuthenticationException) {
                return false;
            }
            if (cause instanceof org.springframework.mail.MailSendException sendFailure
                    && sendFailure.getFailedMessages().values().stream()
                            .anyMatch(InvoiceDeliveryServiceImpl::mayHaveBeenAccepted)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }

    private String resolveRecipientEmail(Invoice invoice) {
        if (invoice.getEmail() != null && !invoice.getEmail().isBlank()) {
            return invoice.getEmail();
        }

        if (invoice.getClientId() == null || clientRepository == null) {
            return null;
        }

        return clientRepository.findById(invoice.getClientId())
                .map(client -> client.getEmail())
                .orElse(null);
    }

    private void validateInvoiceReadyForDelivery(
            Invoice invoice,
            String recipientEmail
    ) {

        if (invoice.getStatus() == InvoiceStatus.APPROVED) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The invoice document has not been generated yet; generate it before sending the invoice."
            );
        }

        if (invoice.getStatus() != InvoiceStatus.READY_TO_SEND) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Invoice must be approved before it can be sent to the client."
            );
        }

        if (!invoiceDocumentRepository.existsByInvoiceId(invoice.getInvoiceId())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "The invoice document has not been generated yet; generate it before sending the invoice."
            );
        }

        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()
                || invoice.getInvoiceDate() == null
                || invoice.getSubtotal() == null
                || invoice.getTotalTaxAmount() == null
                || invoice.getGrandTotal() == null
                || invoice.getGrandTotal().signum() < 0) {

            throw new GlobalExceptionHandler.ValidationException(
                    "Invoice does not have valid invoice data required for delivery."
            );
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Client email is not configured for this invoice."
            );
        }
    }

    private String buildSubject(InvoiceResponseDto invoice) {
        return "Invoice " + invoice.getInvoiceNumber() + " - " + invoice.getSellerLegalName();
    }

    private String buildBody(InvoiceResponseDto invoice) {

        String clientGreeting =
                invoice.getClientName() != null && !invoice.getClientName().isBlank()
                        ? invoice.getClientName()
                        : "Customer";

        String amount =
                (invoice.getCurrencyCode() != null ? invoice.getCurrencyCode() + " " : "")
                        + invoice.getGrandTotal();

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(clientGreeting).append(",\n\n");
        body.append("Please find attached invoice ").append(invoice.getInvoiceNumber())
                .append(" for ").append(amount).append(".\n\n");
        body.append("Invoice date: ").append(invoice.getInvoiceDate()).append("\n");
        if (invoice.getDueDate() != null) {
            body.append("Due date: ").append(invoice.getDueDate()).append("\n");
        }
        body.append("\nThe invoice document is attached to this email as a PDF.\n\n");
        body.append("Regards,\n");
        body.append(invoice.getSellerLegalName());

        return body.toString();
    }

    private InvoiceDeliveryResponseDto mapToResponse(InvoiceDelivery delivery, String invoiceNumber) {

        return InvoiceDeliveryResponseDto.builder()
                .deliveryId(delivery.getInvoiceDeliveryId())
                .invoiceId(delivery.getInvoiceId())
                .invoiceNumber(invoiceNumber)
                .recipientEmail(delivery.getRecipientEmail())
                .deliveryStatus(delivery.getStatus())
                .sentAt(delivery.getSentAt())
                .failedAt(delivery.getFailedAt())
                .failureReason(delivery.getFailureReason())
                .createdAt(delivery.getCreatedAt())
                .requestedBy(delivery.getRequestedBy())
                .resolvedAt(delivery.getResolvedAt())
                .resolvedBy(delivery.getResolvedBy())
                .resolutionReason(delivery.getResolutionReason())
                .build();
    }
}
