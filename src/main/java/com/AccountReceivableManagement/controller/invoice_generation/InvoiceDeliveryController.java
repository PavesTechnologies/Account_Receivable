package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The real "Send to Client" workflow - genuinely sends the invoice by
 * email through the configured Gmail SMTP provider, with the invoice PDF
 * attached. Separate from {@link InvoiceApprovalController} because
 * delivery is not an approval-workflow action: {@code Invoice.status} is
 * never touched by anything in this controller.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceDeliveryController {

    private final InvoiceDeliveryService invoiceDeliveryService;

    /**
     * Sends the invoice to its client. Always returns 200 with the delivery
     * outcome in the body - {@code deliveryStatus} tells the caller whether
     * it actually succeeded ({@code SENT}) or not ({@code FAILED}); a
     * precondition failure (invoice not approved, no recipient email, no
     * active company profile, invoice not found) is reported as an error
     * response instead, before any delivery record is created.
     */
    @PostMapping("/{invoiceId}/send")
    public ResponseEntity<ApiResponse<InvoiceDeliveryResponseDto>> sendInvoice(
            @PathVariable UUID invoiceId
    ) {

        InvoiceDeliveryResponseDto response = invoiceDeliveryService.sendInvoice(invoiceId);

        boolean delivered = response.getDeliveryStatus() == InvoiceDeliveryStatus.SENT;

        return ResponseEntity.ok(
                ApiResponse.<InvoiceDeliveryResponseDto>builder()
                        .success(delivered)
                        .message(
                                delivered
                                        ? "Invoice sent to client successfully."
                                        : "Invoice delivery failed: " + response.getFailureReason()
                        )
                        .data(response)
                        .build()
        );
    }

    /**
     * Every delivery attempt for this invoice, newest first - the current
     * delivery state is the first entry. Lets the frontend stop relying on
     * localStorage as the source of truth.
     */
    @GetMapping("/{invoiceId}/deliveries")
    public ResponseEntity<ApiResponse<List<InvoiceDeliveryResponseDto>>> getDeliveryHistory(
            @PathVariable UUID invoiceId
    ) {

        List<InvoiceDeliveryResponseDto> response =
                invoiceDeliveryService.getDeliveryHistory(invoiceId);

        return ResponseEntity.ok(
                ApiResponse.<List<InvoiceDeliveryResponseDto>>builder()
                        .success(true)
                        .message("Delivery history retrieved successfully.")
                        .data(response)
                        .build()
        );
    }
}
