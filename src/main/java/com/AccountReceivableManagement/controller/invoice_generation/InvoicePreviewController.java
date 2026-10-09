package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only pre-generation invoice preview. Separate from
 * {@link InvoiceController}, whose POST generates the invoice - this never
 * persists anything.
 */
@RestController
@RequestMapping("/api/v1/billing-snapshots/{snapshotId}/invoice-preview")
@RequiredArgsConstructor
public class InvoicePreviewController {

    private final InvoiceService invoiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<InvoiceResponseDto>> previewInvoice(
            @PathVariable UUID snapshotId
    ) {

        return ResponseEntity.ok(
                ApiResponse.<InvoiceResponseDto>builder()
                        .success(true)
                        .message("Invoice preview retrieved successfully.")
                        .data(invoiceService.previewInvoice(snapshotId))
                        .build());
    }
}
