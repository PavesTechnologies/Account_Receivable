package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceGenerationWorkspaceResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Invoice Generation workspace: invoice candidates (TAX_COMPLETED
 * snapshots without an invoice) plus existing invoices. Generates nothing -
 * invoice creation stays on {@code POST /api/v1/billing-snapshots/{id}/invoice}.
 */
@RestController
@RequestMapping("/api/v1/invoice-generation")
@RequiredArgsConstructor
public class InvoiceGenerationWorkspaceController {

    private final InvoiceService invoiceService;

    @GetMapping("/workspace")
    public ResponseEntity<ApiResponse<InvoiceGenerationWorkspaceResponseDto>> getWorkspace() {

        return ResponseEntity.ok(
                ApiResponse.<InvoiceGenerationWorkspaceResponseDto>builder()
                        .success(true)
                        .message("Invoice generation workspace retrieved successfully.")
                        .data(invoiceService.getInvoiceGenerationWorkspace())
                        .build());
    }
}
