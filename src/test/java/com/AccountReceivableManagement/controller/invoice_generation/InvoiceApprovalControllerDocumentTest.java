package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.entity.invoice_generation.InvoiceDocument;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.service_Imple.invoice_generation.PdfGenerationException;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceFinancialCorrectionService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The approve endpoint: approval first (its own transaction), then document
 * finalization. A document failure is reported but never undoes or hides the
 * approval, and nothing is ever sent.
 */
class InvoiceApprovalControllerDocumentTest {

    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final InvoiceApprovalController controller =
            new InvoiceApprovalController(invoiceService, mock(InvoiceFinancialCorrectionService.class));
    private final UUID invoiceId = UUID.randomUUID();

    private InvoiceResponseDto invoice(InvoiceStatus status) {
        return InvoiceResponseDto.builder()
                .invoiceId(invoiceId)
                .status(status)
                .documentAvailable(status == InvoiceStatus.READY_TO_SEND)
                .build();
    }

    @Test
    void approve_approvesThenFinalizes_andReportsReadyToSend() {
        when(invoiceService.approveInvoice(invoiceId)).thenReturn(invoice(InvoiceStatus.APPROVED));
        when(invoiceService.finalizeInvoiceDocument(invoiceId)).thenReturn(invoice(InvoiceStatus.READY_TO_SEND));

        ApiResponse<InvoiceResponseDto> body = controller.approve(invoiceId).getBody();

        InOrder order = inOrder(invoiceService);
        order.verify(invoiceService).approveInvoice(invoiceId);
        order.verify(invoiceService).finalizeInvoiceDocument(invoiceId);
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData().getStatus()).isEqualTo(InvoiceStatus.READY_TO_SEND);
        assertThat(body.getData().isDocumentAvailable()).isTrue();
        assertThat(body.getMessage()).contains("ready to send");
    }

    @Test
    void approve_documentValidationFailure_keepsTheApproval_andSaysWhatIsMissing() {
        when(invoiceService.approveInvoice(invoiceId)).thenReturn(invoice(InvoiceStatus.APPROVED));
        when(invoiceService.finalizeInvoiceDocument(invoiceId)).thenThrow(new GlobalExceptionHandler.ValidationException(
                "The invoice document cannot be generated because the invoice has no currency."));

        ApiResponse<InvoiceResponseDto> body = controller.approve(invoiceId).getBody();

        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData().getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(body.getData().isDocumentAvailable()).isFalse();
        assertThat(body.getMessage()).contains("approved successfully", "has no currency",
                "Generate the document again");
    }

    @Test
    void approve_renderingFailure_keepsTheApproval_withoutExposingInternals() {
        when(invoiceService.approveInvoice(invoiceId)).thenReturn(invoice(InvoiceStatus.APPROVED));
        when(invoiceService.finalizeInvoiceDocument(invoiceId))
                .thenThrow(new PdfGenerationException("com.lowagie internal font error", null));

        ApiResponse<InvoiceResponseDto> body = controller.approve(invoiceId).getBody();

        assertThat(body.getData().getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(body.getMessage()).doesNotContain("lowagie").contains("could not be generated");
    }

    @Test
    void approve_rejectedApproval_neverFinalizes() {
        when(invoiceService.approveInvoice(invoiceId)).thenThrow(new GlobalExceptionHandler.ValidationException(
                "Only invoices pending approval can be approved."));

        assertThatThrownBy(() -> controller.approve(invoiceId))
                .hasMessage("Only invoices pending approval can be approved.");
        org.mockito.Mockito.verify(invoiceService, org.mockito.Mockito.never()).finalizeInvoiceDocument(invoiceId);
    }

    @Test
    void downloadDocument_returnsTheStoredPdf() {
        byte[] pdf = {37, 80, 68, 70};
        when(invoiceService.getInvoiceDocument(invoiceId)).thenReturn(InvoiceDocument.builder()
                .invoiceId(invoiceId).fileName("invoice-INV-1.pdf").content(pdf).build());

        ResponseEntity<byte[]> response = controller.downloadDocument(invoiceId);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("invoice-INV-1.pdf");
        assertThat(response.getBody()).isEqualTo(pdf);
    }
}
