package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceItemResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceTaxComponentResponseDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingItemType;
import com.AccountReceivableManagement.entity_enums.tax_calculation.TaxApplicabilityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceDocumentServiceImplTest {

    private final InvoiceDocumentServiceImpl service = new InvoiceDocumentServiceImpl();

    private InvoiceResponseDto fullInvoice() {
        return InvoiceResponseDto.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceNumber("INV-20260908164549")
                .clientName("Account Management")
                .billingAddress("221B Baker Street, London")
                .gstinOrTaxId("GB123456789")
                .email("client@example.com")
                .phone("+44 20 7946 0958")
                .currencyCode("USD")
                .paymentTermName("Net 30")
                .invoiceDate(LocalDate.of(2026, 9, 8))
                .dueDate(LocalDate.of(2026, 10, 8))
                .billingPeriodStart(LocalDate.of(2026, 6, 1))
                .billingPeriodEnd(LocalDate.of(2026, 8, 30))
                .items(List.of(
                        InvoiceItemResponseDto.builder()
                                .itemType(BillingItemType.TIME_ENTRY)
                                .itemName("Backend Development")
                                .resourceName("Jane Doe")
                                .quantity(new BigDecimal("55.00"))
                                .rate(new BigDecimal("100.00"))
                                .amount(new BigDecimal("5500.00"))
                                .build()
                ))
                .taxComponents(List.of(
                        InvoiceTaxComponentResponseDto.builder()
                                .taxTypeCode("CGST")
                                .taxTypeName("Central GST")
                                .appliedRate(new BigDecimal("9.0000"))
                                .taxAmount(new BigDecimal("495.00"))
                                .applicabilityType(TaxApplicabilityType.ALL)
                                .build()
                ))
                .subtotal(new BigDecimal("5500.00"))
                .totalTaxAmount(new BigDecimal("495.00"))
                .grandTotal(new BigDecimal("5995.00"))
                .build();
    }

    private CompanyProfileResponseDto companyProfile() {
        return CompanyProfileResponseDto.builder()
                .legalName("Example Global Infotech Private Limited")
                .addressLine1("Tower B, Tech Park")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500081")
                .country("India")
                .gstin("36AAAAA0000A1Z5")
                .email("billing@example.com")
                .phone("+91 40 1234 5678")
                .build();
    }

    @Test
    void generateInvoicePdf_fullInvoice_producesValidPdfBytes() {
        byte[] pdf = service.generateInvoicePdf(fullInvoice(), companyProfile());

        assertThat(pdf).isNotEmpty();
        // Every PDF file starts with this magic header - confirms a real
        // document was rendered, not a placeholder/empty byte array.
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void generateInvoicePdf_emptyItemsAndTax_stillProducesValidPdf() {
        InvoiceResponseDto invoice = fullInvoice();
        invoice.setItems(List.of());
        invoice.setTaxComponents(List.of());

        byte[] pdf = service.generateInvoicePdf(invoice, companyProfile());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
