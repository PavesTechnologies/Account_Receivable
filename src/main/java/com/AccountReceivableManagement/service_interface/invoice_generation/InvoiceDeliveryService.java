package com.AccountReceivableManagement.service_interface.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;

import java.util.List;
import java.util.UUID;

public interface InvoiceDeliveryService {

    /**
     * Sends a READY_TO_SEND invoice (approved, document stored) to its client
     * by email, attaching its stored PDF. Requires INVOICE_DELIVERY.
     * <p>
     * An invoice is delivered at most once by this ordinary send (G-19): it
     * is refused when a delivery of the invoice is SENT, PENDING or UNCERTAIN;
     * allowed again only after every earlier attempt FAILED. The check and
     * the PENDING intent are committed under the invoice's row lock, so of
     * concurrent requests exactly one proceeds.
     * <p>
     * The email is sent outside any database transaction (G-20): the PENDING
     * intent commits first, the SMTP call follows, and the outcome is
     * recorded in its own transaction - SENT, FAILED (definitely not
     * delivered) or UNCERTAIN (the server may have accepted it). A row left
     * PENDING (outcome could not be recorded) is also treated as uncertain.
     * Neither is ever re-sent automatically. Once SENT is recorded the
     * invoice's single receivable is created; a failure there leaves SENT
     * intact for reconciliation (G-21). Invoice status is never modified.
     *
     * @throws IllegalStateException if called inside a transaction
     */
    InvoiceDeliveryResponseDto sendInvoice(UUID invoiceId);

    /**
     * Every delivery attempt for one invoice, newest first. The first
     * entry is the current delivery state.
     */
    List<InvoiceDeliveryResponseDto> getDeliveryHistory(UUID invoiceId);

    /**
     * Operator resolution of a delivery whose outcome is unknown (UNCERTAIN,
     * or PENDING for longer than the in-flight timeout) after checking the
     * sending mailbox: {@code SENT} (the receivable is then created) or
     * {@code FAILED} (a new ordinary send becomes possible). Requires
     * OPERATIONS_RECOVERY; recorded with actor and reason. Never sends email.
     */
    InvoiceDeliveryResponseDto resolveDelivery(UUID deliveryId,
            com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus outcome,
            String reason);
}
