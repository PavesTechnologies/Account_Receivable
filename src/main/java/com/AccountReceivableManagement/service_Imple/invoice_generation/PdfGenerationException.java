package com.AccountReceivableManagement.service_Imple.invoice_generation;

/**
 * Thrown by {@link InvoiceDocumentServiceImpl} when the invoice PDF cannot
 * be rendered. Caught by the delivery orchestration layer to record a
 * {@code FAILED} {@code InvoiceDelivery} row before any email is attempted.
 */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
