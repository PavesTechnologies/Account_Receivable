package com.AccountReceivableManagement.service_interface.invoice_generation;

import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;

public interface InvoiceDocumentService {

    /**
     * Renders the invoice PDF purely from already-computed, authoritative
     * data - every figure on the page, including the seller ("From") block,
     * is read directly from {@code invoice}, never recalculated and never
     * looked up live. The seller block uses {@code invoice}'s own
     * {@code seller*} snapshot fields (frozen at generation time from the
     * Company Profile that was active then), so a historical invoice's PDF
     * never changes just because the Company Profile is edited afterwards.
     * Throws
     * {@link com.AccountReceivableManagement.service_Imple.invoice_generation.PdfGenerationException}
     * if rendering fails.
     */
    byte[] generateInvoicePdf(InvoiceResponseDto invoice);
}
