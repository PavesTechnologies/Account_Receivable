package com.AccountReceivableManagement.service_Imple.receivable;

import com.AccountReceivableManagement.dto.receivable.ReceivableResponseDto;
import com.AccountReceivableManagement.dto.recovery.RecoveryResultDto;
import com.AccountReceivableManagement.entity_enums.invoice_generation.InvoiceDeliveryStatus;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceDeliveryRepository;
import com.AccountReceivableManagement.repo.invoice_generation.InvoiceRepository;
import com.AccountReceivableManagement.repo.receivable.ReceivableRepository;
import com.AccountReceivableManagement.security.Actor;
import com.AccountReceivableManagement.security.FinancialAction;
import com.AccountReceivableManagement.security.FinancialActionAuthorizer;
import com.AccountReceivableManagement.service_interface.receivable.ReceivableRecoveryService;
import com.AccountReceivableManagement.service_interface.receivable.ReceivableService;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableRecoveryServiceImpl implements ReceivableRecoveryService {

    static final String RECORD_TYPE = "RECEIVABLE";

    private final ReceivableService receivableService;
    private final ReceivableRepository receivableRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDeliveryRepository invoiceDeliveryRepository;
    private final FinancialActionAuthorizer authorizer;

    @Override
    public RecoveryResultDto recoverReceivable(UUID invoiceId, String reason) {
        Actor actor = authorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY);
        if (reason == null || reason.isBlank()) {
            throw new GlobalExceptionHandler.ValidationException("A reason is required for a recovery action.");
        }
        if (!invoiceRepository.existsById(invoiceId)) {
            throw new GlobalExceptionHandler.ResourceNotFoundException("Invoice could not be found.");
        }
        var existing = receivableRepository.findByInvoiceId(invoiceId);
        if (existing.isPresent()) {
            return result(invoiceId, false, existing.get().getStatus().name(),
                    "The invoice already has its receivable " + existing.get().getReceivableId() + "; nothing created.");
        }
        try {
            ReceivableResponseDto created = receivableService.createReceivableFromInvoice(invoiceId);
            log.warn("[ReceivableRecovered] invoice={} receivable={} by={}", invoiceId,
                    created.getReceivableId(), actor.label());
            Metrics.counter("ar.receivable.reconciliation", "outcome", "created").increment();
            return result(invoiceId, true, String.valueOf(created.getStatus()),
                    "Receivable " + created.getReceivableId() + " created for the sent invoice.");
        } catch (GlobalExceptionHandler.ValidationException ineligible) {
            // Not eligible under the receivable rules (not sent, not approved, invalid data).
            return result(invoiceId, false, null, ineligible.getMessage());
        }
    }

    @Override
    public int reconcileSentInvoicesWithoutReceivable(int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<UUID> invoiceIds = invoiceDeliveryRepository.findSentInvoiceIdsWithoutReceivable(
                InvoiceDeliveryStatus.SENT, PageRequest.of(0, limit));
        int created = 0;
        for (UUID invoiceId : invoiceIds) {
            try {
                receivableService.createReceivableFromInvoice(invoiceId);
                created++;
                Metrics.counter("ar.receivable.reconciliation", "outcome", "created").increment();
                log.warn("[ReceivableReconciled] invoice={} had a SENT delivery without a receivable; created.",
                        invoiceId);
            } catch (RuntimeException ex) {
                Metrics.counter("ar.receivable.reconciliation", "outcome", "failed").increment();
                log.error("[ReceivableReconciliationFailed] invoice={}: {}", invoiceId,
                        ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        return created;
    }

    private static RecoveryResultDto result(UUID invoiceId, boolean accepted, String state, String message) {
        return RecoveryResultDto.builder()
                .recordId(invoiceId)
                .recordType(RECORD_TYPE)
                .accepted(accepted)
                .state(state)
                .message(message)
                .build();
    }
}
