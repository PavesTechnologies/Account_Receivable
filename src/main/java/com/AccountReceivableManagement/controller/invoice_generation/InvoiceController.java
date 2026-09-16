package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing-snapshots/{snapshotId}/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > generateInvoice(
            @PathVariable UUID snapshotId
    ) {

        InvoiceResponseDto response =
                invoiceService.generateInvoice(
                        snapshotId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse
                                .<InvoiceResponseDto>builder()
                                .success(true)
                                .message(
                                        "Invoice generated successfully."
                                )
                                .data(response)
                                .build()
                );
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > getInvoice(
            @PathVariable UUID snapshotId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message(
                                "Invoice retrieved successfully."
                        )
                        .data(
                                invoiceService
                                        .getInvoiceByBillingSnapshotId(
                                                snapshotId
                                        )
                        )
                        .build()
        );
    }
}
