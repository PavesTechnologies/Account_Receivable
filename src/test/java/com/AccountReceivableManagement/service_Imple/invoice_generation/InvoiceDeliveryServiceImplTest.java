package com.AccountReceivableManagement.service_Imple.invoice_generation;

import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.invoice_generation.Invoice;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDelivery;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.service_Imple.email.EmailDeliveryException;
import com.AccountReceivableManagement.service_interface.company_profile.CompanyProfileService;
import com.AccountReceivableManagement.service_interface.email.EmailService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDocumentService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceDeliveryServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceDeliveryRepository invoiceDeliveryRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private CompanyProfileService companyProfileService;

    @Mock
    private InvoiceDocumentService invoiceDocumentService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private InvoiceDeliveryServiceImpl invoiceDeliveryService;

    private UUID invoiceId;

    @BeforeEach
    void setUp() {
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

    private Invoice approvedInvoice() {
        return Invoice.builder()
                .invoiceId(invoiceId)
                .invoiceNumber("INV-20260908164549")
                .status(InvoiceStatus.APPROVED)
                .invoiceDate(LocalDate.of(2026, 9, 8))
                .dueDate(LocalDate.of(2026, 10, 8))
                .clientName("Account Management")
                .email("client@example.com")
                .currencyCode("USD")
                .subtotal(new BigDecimal("5500.00"))
                .totalTaxAmount(new BigDecimal("990.00"))
                .grandTotal(new BigDecimal("6490.00"))
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
                .items(List.of())
                .taxComponents(List.of())
                .build();
    }

    private CompanyProfileResponseDto activeCompanyProfile() {
        return CompanyProfileResponseDto.builder()
                .companyProfileId(UUID.randomUUID())
                .legalName("Example Global Infotech Private Limited")
                .isActive(true)
                .build();
    }

    // 1. Successful email delivery -> 8. Delivery status becomes SENT after successful email.
    @Test
    void sendInvoice_approvedInvoiceWithEmail_sendsAndMarksSent() {
        Invoice invoice = approvedInvoice();
        InvoiceResponseDto response = invoiceResponse(invoice);
        CompanyProfileResponseDto companyProfile = activeCompanyProfile();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(companyProfileService.getActive()).thenReturn(companyProfile);
        when(invoiceService.getInvoiceById(invoiceId)).thenReturn(response);
        when(invoiceDocumentService.generateInvoicePdf(response, companyProfile))
                .thenReturn(new byte[]{1, 2, 3});
        when(emailService.sendEmailWithAttachment(
                eq("client@example.com"), anyString(), anyString(), any(byte[].class), anyString(), anyString()
        )).thenReturn("<message-id@gmail.com>");

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(result.getRecipientEmail()).isEqualTo("client@example.com");
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getFailureReason()).isNull();
        assertThat(result.getInvoiceNumber()).isEqualTo("INV-20260908164549");

        // Invoice status itself is never touched by delivery.
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        verify(invoiceRepository, never()).save(any());

        ArgumentCaptor<InvoiceDelivery> captor = ArgumentCaptor.forClass(InvoiceDelivery.class);
        verify(invoiceDeliveryRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(captor.getValue().getProviderMessageId()).isEqualTo("<message-id@gmail.com>");
    }

    // 2. Invoice not found.
    @Test
    void sendInvoice_invoiceNotFound_throwsResourceNotFoundException() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);

        verifyNoInteractions(invoiceDeliveryRepository, emailService, invoiceDocumentService);
    }

    // 3. Invoice not APPROVED.
    @Test
    void sendInvoice_invoiceNotApproved_throwsValidationException() {
        Invoice invoice = approvedInvoice();
        invoice.setStatus(InvoiceStatus.GENERATED);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Invoice must be approved before it can be sent to the client.");

        verifyNoInteractions(invoiceDeliveryRepository, emailService, invoiceDocumentService, companyProfileService);
    }

    // 4. Missing client email.
    @Test
    void sendInvoice_missingClientEmail_throwsValidationException() {
        Invoice invoice = approvedInvoice();
        invoice.setEmail(null);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ValidationException.class)
                .hasMessage("Client email is not configured for this invoice.");

        verifyNoInteractions(invoiceDeliveryRepository, emailService, invoiceDocumentService, companyProfileService);
    }

    // 5. Missing CompanyProfile.
    @Test
    void sendInvoice_noActiveCompanyProfile_throwsResourceNotFoundException() {
        Invoice invoice = approvedInvoice();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(companyProfileService.getActive())
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException(
                        "No active company profile has been configured."
                ));

        assertThatThrownBy(() -> invoiceDeliveryService.sendInvoice(invoiceId))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("No active company profile has been configured.");

        verifyNoInteractions(invoiceDeliveryRepository, emailService, invoiceDocumentService);
    }

    // 6. PDF generation failure -> 9. Delivery status becomes FAILED after email failure (PDF variant).
    @Test
    void sendInvoice_pdfGenerationFails_marksDeliveryFailed() {
        Invoice invoice = approvedInvoice();
        InvoiceResponseDto response = invoiceResponse(invoice);
        CompanyProfileResponseDto companyProfile = activeCompanyProfile();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(companyProfileService.getActive()).thenReturn(companyProfile);
        when(invoiceService.getInvoiceById(invoiceId)).thenReturn(response);
        when(invoiceDocumentService.generateInvoicePdf(response, companyProfile))
                .thenThrow(new PdfGenerationException("Rendering failed.", new RuntimeException("boom")));

        InvoiceDeliveryResponseDto result = invoiceDeliveryService.sendInvoice(invoiceId);

        assertThat(result.getDeliveryStatus()).isEqualTo(InvoiceDeliveryStatus.FAILED);
        assertThat(result.getFailureReason()).contains("PDF generation failed");
        assertThat(result.getFailedAt()).isNotNull();
        verifyNoInteractions(emailService);

        ArgumentCaptor<InvoiceDelivery> captor = ArgumentCaptor.forClass(InvoiceDelivery.class);
        verify(invoiceDeliveryRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InvoiceDeliveryStatus.FAILED);
    }

    // 7. Gmail/email service failure -> 9. Delivery status becomes FAILED after email failure.
    @Test
    void sendInvoice_emailServiceFails_marksDeliveryFailedNotSent() {
        Invoice invoice = approvedInvoice();
        InvoiceResponseDto response = invoiceResponse(invoice);
        CompanyProfileResponseDto companyProfile = activeCompanyProfile();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(companyProfileService.getActive()).thenReturn(companyProfile);
        when(invoiceService.getInvoiceById(invoiceId)).thenReturn(response);
        when(invoiceDocumentService.generateInvoicePdf(response, companyProfile))
                .thenReturn(new byte[]{1, 2, 3});
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

        // Never silently marked SENT.
        assertThat(result.getDeliveryStatus()).isNotEqualTo(InvoiceDeliveryStatus.SENT);
        assertThat(result.getSentAt()).isNull();
    }

    // 10. Delivery history retrieval.
    @Test
    void getDeliveryHistory_returnsAttemptsNewestFirst() {
        Invoice invoice = approvedInvoice();

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
