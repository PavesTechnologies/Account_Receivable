package com.AccountReceivableManagement.controller.recovery;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.recovery.AutomaticBillingRunEventDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryRequestDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;
import com.AccountReceivableManagement.service_interface.automatic_billing.AutomaticBillingRunRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Operational recovery of automatic billing runs (G-17/G-18) - replaces the
 * manual SQL of the runbook. Authorization (OPERATIONS_RECOVERY) is enforced
 * by the service. An ineligible action changes nothing and returns 409 with
 * the reason.
 */
@RestController
@RequestMapping("/api/v1/recovery/automatic-billing-runs")
@RequiredArgsConstructor
public class AutomaticBillingRunRecoveryController {

    private final AutomaticBillingRunRecoveryService recoveryService;

    /** Re-queue a FAILED (or interrupted) run for another attempt. */
    @PostMapping("/{runId}/requeue")
    public ResponseEntity<ApiResponse<RecoveryResultDto>> requeue(@PathVariable UUID runId,
            @Valid @RequestBody RecoveryRequestDto request) {
        return respond(recoveryService.requeue(runId, request.getReason()));
    }

    /** Complete a PENDING_APPROVAL run whose invoice is already approved. */
    @PostMapping("/{runId}/reconcile-approval")
    public ResponseEntity<ApiResponse<RecoveryResultDto>> reconcileApproval(@PathVariable UUID runId) {
        return respond(recoveryService.reconcileApproval(runId));
    }

    /** The run's recovery history: each requeue/reconciliation with the failure it replaced. */
    @GetMapping("/{runId}/events")
    public ResponseEntity<ApiResponse<List<AutomaticBillingRunEventDto>>> events(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.<List<AutomaticBillingRunEventDto>>builder()
                .success(true)
                .message("Automatic billing run history retrieved successfully.")
                .data(recoveryService.getEvents(runId))
                .build());
    }

    static ResponseEntity<ApiResponse<RecoveryResultDto>> respond(RecoveryResultDto result) {
        return ResponseEntity.status(result.isAccepted() ? HttpStatus.OK : HttpStatus.CONFLICT)
                .body(ApiResponse.<RecoveryResultDto>builder()
                        .success(result.isAccepted())
                        .message(result.getMessage())
                        .data(result)
                        .build());
    }
}
