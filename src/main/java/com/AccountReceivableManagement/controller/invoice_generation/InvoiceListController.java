package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceSummaryResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
