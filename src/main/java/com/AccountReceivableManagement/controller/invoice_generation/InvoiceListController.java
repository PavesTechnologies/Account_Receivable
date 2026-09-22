package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceSummaryResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only Invoice Generation workspace listing. Separate from
 * {@link InvoiceController} because it is not nested under a single
 * billing snapshot - it lists every already-generated invoice.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceListController {

    private final InvoiceService invoiceService;

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<InvoiceSummaryResponseDto>>
            > getAllInvoices() {

        return ResponseEntity.ok(
                ApiResponse
                        .<List<InvoiceSummaryResponseDto>>builder()
                        .success(true)
                        .message(
                                "Invoices retrieved successfully."
                        )
                        .data(invoiceService.getAllInvoices())
                        .build()
        );
    }

    /**
     * The same fully-populated invoice (header, items, tax components,
     * totals) as {@link InvoiceController#getInvoice(UUID)}, keyed by
     * invoice id instead of billing snapshot id - the authoritative source
     * for an Invoice Preview screen when only the invoice id is in hand
     * (e.g. from the approval workspace).
     */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > getInvoiceById(
            @PathVariable UUID invoiceId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message(
                                "Invoice retrieved successfully."
                        )
                        .data(
                                invoiceService.getInvoiceById(invoiceId)
                        )
                        .build()
        );
    }
}
