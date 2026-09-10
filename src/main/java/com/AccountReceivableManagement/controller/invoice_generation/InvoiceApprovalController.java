package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalHistoryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalWorkspaceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Invoice Approval, Phase 1: {@code GENERATED -> PENDING_APPROVAL -> APPROVED}.
 * Normal AR access, same as the existing Billing Approval endpoints - no
 * Finance-Manager-specific restriction exists yet, and none is added here.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceApprovalController {

    private final InvoiceService invoiceService;

    @PostMapping("/{invoiceId}/submit-for-approval")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > submitForApproval(
            @PathVariable UUID invoiceId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message(
                                "Invoice submitted for approval successfully."
                        )
                        .data(
                                invoiceService.submitForApproval(
                                        invoiceId
                                )
                        )
                        .build()
        );
    }

    @PostMapping("/{invoiceId}/approve")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > approve(
            @PathVariable UUID invoiceId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message("Invoice approved successfully.")
                        .data(
                                invoiceService.approveInvoice(
                                        invoiceId
                                )
                        )
                        .build()
        );
    }

    @GetMapping("/{invoiceId}/approval-history")
    public ResponseEntity<
            ApiResponse<List<InvoiceApprovalHistoryResponseDto>>
            > getApprovalHistory(
            @PathVariable UUID invoiceId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<List<InvoiceApprovalHistoryResponseDto>>builder()
                        .success(true)
                        .message(
                                "Invoice approval history retrieved successfully."
                        )
                        .data(
                                invoiceService.getApprovalHistory(
                                        invoiceId
                                )
                        )
                        .build()
        );
    }

    @GetMapping("/pending-approval")
    public ResponseEntity<
            ApiResponse<List<InvoiceApprovalSummaryResponseDto>>
            > getPendingApprovalInvoices() {

        return ResponseEntity.ok(
                ApiResponse
                        .<List<InvoiceApprovalSummaryResponseDto>>builder()
                        .success(true)
                        .message(
                                "Pending approval invoices retrieved successfully."
                        )
                        .data(
                                invoiceService
                                        .getPendingApprovalInvoices()
                        )
                        .build()
        );
    }

    @GetMapping("/approval-workspace")
    public ResponseEntity<
            ApiResponse<List<InvoiceApprovalWorkspaceResponseDto>>
            > getApprovalWorkspaceInvoices() {

        return ResponseEntity.ok(
                ApiResponse
                        .<List<InvoiceApprovalWorkspaceResponseDto>>builder()
                        .success(true)
                        .message(
                                "Invoice approval workspace retrieved successfully."
                        )
                        .data(
                                invoiceService
                                        .getApprovalWorkspaceInvoices()
                        )
                        .build()
        );
    }
}
