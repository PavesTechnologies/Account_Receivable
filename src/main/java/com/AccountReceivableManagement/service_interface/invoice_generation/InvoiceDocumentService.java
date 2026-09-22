package com.AccountReceivableManagement.service_interface.invoice_generation;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;

public interface InvoiceDocumentService {

    /**
     * Renders the invoice PDF purely from already-computed, authoritative
     * data - every figure on the page is read directly from
     * {@code invoice}, never recalculated. Throws
     * {@link com.AccountReceivableManagement.service_Imple.invoice_generation.PdfGenerationException}
     * if rendering fails.
     */
    byte[] generateInvoicePdf(
            InvoiceResponseDto invoice,
            CompanyProfileResponseDto companyProfile
    );
}
