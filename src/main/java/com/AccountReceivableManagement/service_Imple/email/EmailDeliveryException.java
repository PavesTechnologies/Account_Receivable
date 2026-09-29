package com.AccountReceivableManagement.service_Imple.email;

/**
 * Thrown by {@link EmailServiceImpl} when a message could not be sent
 * through the configured SMTP provider (authentication, connection, or
 * transport failure). Caught by the delivery orchestration layer to record
 * a {@code FAILED} {@code InvoiceDelivery} row - never allowed to result in
 * an invoice being silently treated as sent.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
