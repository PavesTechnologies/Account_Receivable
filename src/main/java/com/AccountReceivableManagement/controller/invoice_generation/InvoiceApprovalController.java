package com.AccountReceivableManagement.controller.invoice_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalHistoryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalSummaryResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceApprovalWorkspaceResponseDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceNonFinancialCorrectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceRejectionRequestDto;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceFinancialCorrectionService;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Invoice Approval: {@code GENERATED -> PENDING_APPROVAL -> APPROVED}, with
 * {@code PENDING_APPROVAL -> REJECTED -> (correction refresh) ->
 * PENDING_APPROVAL} for the rejection/resubmission cycle.
 * {@code refresh-after-correction} is not a generic invoice editor - it only
 * re-copies a rejected invoice's financial snapshot from its authoritative,
 * already-persisted BillingSnapshot/TaxCalculation; the invoice stays
 * {@code REJECTED} until explicitly resubmitted. Normal AR access, same as
 * the existing Billing Approval endpoints - no Finance-Manager-specific
 * restriction exists yet, and none is added here.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceApprovalController {

    private final InvoiceService invoiceService;

    private final InvoiceFinancialCorrectionService invoiceFinancialCorrectionService;

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

    @PostMapping("/{invoiceId}/reject")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > reject(
            @PathVariable UUID invoiceId,
            @RequestBody InvoiceRejectionRequestDto request
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message("Invoice rejected successfully.")
                        .data(
                                invoiceService.rejectInvoice(
                                        invoiceId,
                                        request
                                )
                        )
                        .build()
        );
    }

    @PostMapping("/{invoiceId}/refresh-after-correction")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > refreshAfterCorrection(
            @PathVariable UUID invoiceId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message(
                                "Invoice refreshed from corrected billing/tax data successfully."
                        )
                        .data(
                                invoiceService.refreshAfterCorrection(
                                        invoiceId
                                )
                        )
                        .build()
        );
    }

    /**
     * Phase 2C - corrects only {@code clientName}/{@code projectName} on a
     * {@code REJECTED} invoice. Not a generic invoice editor: no other field
     * is accepted, and the invoice stays {@code REJECTED} until explicitly
     * resubmitted via {@link #submitForApproval(UUID)}.
     */
    @PatchMapping("/{invoiceId}/non-financial-correction")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > correctNonFinancialFields(
            @PathVariable UUID invoiceId,
            @RequestBody InvoiceNonFinancialCorrectionRequestDto request
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message(
                                "Invoice non-financial correction completed successfully."
                        )
                        .data(
                                invoiceService.correctNonFinancialFields(
                                        invoiceId,
                                        request
                                )
                        )
                        .build()
        );
    }

    /**
     * Phase 2B financial correction - the "first mile" ahead of
     * {@link #refreshAfterCorrection(UUID)}. Re-acquires authoritative
     * source data (TMS, for Time &amp; Material), rebuilds the invoice's
     * existing BillingSnapshot in place, recalculates its TaxCalculation,
     * then calls the existing {@code refreshAfterCorrection(UUID)} to
     * propagate the corrected figures onto the invoice. No request body:
     * every financial value originates from re-acquired authoritative
     * source data, never from the caller. The invoice stays
     * {@code REJECTED} - the caller must still explicitly resubmit via
     * {@link #submitForApproval(UUID)}. Separate from, and does not modify,
     * Phase 2C's {@link #correctNonFinancialFields(UUID,
     * InvoiceNonFinancialCorrectionRequestDto)}.
     */
    @PostMapping("/{invoiceId}/financial-correction/reacquire")
    public ResponseEntity<
            ApiResponse<InvoiceResponseDto>
            > reacquireForFinancialCorrection(
            @PathVariable UUID invoiceId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<InvoiceResponseDto>builder()
                        .success(true)
                        .message(
                                "Invoice financial correction completed successfully."
                        )
                        .data(
                                invoiceFinancialCorrectionService
                                        .reacquireForFinancialCorrection(
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
