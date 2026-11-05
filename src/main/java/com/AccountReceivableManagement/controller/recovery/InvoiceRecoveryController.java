package com.AccountReceivableManagement.controller.recovery;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_generation.InvoiceDeliveryResponseDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryRequestDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.service_interface.invoice_generation.InvoiceDeliveryService;
import com.AccountReceivableManagement.service_interface.receivable.ReceivableRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operational recovery of invoice deliveries and receivables (G-19/G-20/G-21).
 * Authorization (OPERATIONS_RECOVERY) is enforced by the services. Nothing
 * here sends an email.
 */
@RestController
@RequestMapping("/api/v1/recovery")
@RequiredArgsConstructor
public class InvoiceRecoveryController {

    private final InvoiceDeliveryService invoiceDeliveryService;
    private final ReceivableRecoveryService receivableRecoveryService;
    private final com.AccountReceivableManagement.service_Imple.tax_calculation.TaxRecalculationRecoveryService
            taxRecalculationRecoveryService;

    /**
     * BD-07: recalculate the tax of an un-invoiced occurrence whose
     * configuration currency or tax region changed after tax was calculated
     * (invoicing is refused until then). The old calculation is kept as
     * superseded history.
     */
    @PostMapping("/billing-occurrences/{occurrenceId}/recalculate-tax")
    public ResponseEntity<ApiResponse<RecoveryResultDto>> recalculateStaleTax(@PathVariable UUID occurrenceId,
            @Valid @RequestBody RecoveryRequestDto request) {
        return AutomaticBillingRunRecoveryController.respond(
                taxRecalculationRecoveryService.recalculateStaleTax(occurrenceId, request.getReason()));
    }

    /** Create the missing receivable of a sent invoice (never re-sends it). */
    @PostMapping("/invoices/{invoiceId}/receivable")
    public ResponseEntity<ApiResponse<RecoveryResultDto>> recoverReceivable(@PathVariable UUID invoiceId,
            @Valid @RequestBody RecoveryRequestDto request) {
        return AutomaticBillingRunRecoveryController.respond(
                receivableRecoveryService.recoverReceivable(invoiceId, request.getReason()));
    }

    /**
     * Resolve a delivery whose outcome is unknown (UNCERTAIN, or PENDING past
     * its in-flight timeout) after checking the sending mailbox.
     */
    @PostMapping("/invoice-deliveries/{deliveryId}/resolve")
    public ResponseEntity<ApiResponse<InvoiceDeliveryResponseDto>> resolveDelivery(@PathVariable UUID deliveryId,
            @Valid @RequestBody ResolveDeliveryRequest request) {
        InvoiceDeliveryResponseDto resolved =
                invoiceDeliveryService.resolveDelivery(deliveryId, request.getOutcome(), request.getReason());
        return ResponseEntity.ok(ApiResponse.<InvoiceDeliveryResponseDto>builder()
                .success(true)
                .message("Invoice delivery resolved as " + resolved.getDeliveryStatus() + ".")
                .data(resolved)
                .build());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ResolveDeliveryRequest extends RecoveryRequestDto {

        /** SENT (found in the sending mailbox) or FAILED (not sent). */
        @NotNull(message = "The outcome (SENT or FAILED) is required.")
        private InvoiceDeliveryStatus outcome;
    }
}
