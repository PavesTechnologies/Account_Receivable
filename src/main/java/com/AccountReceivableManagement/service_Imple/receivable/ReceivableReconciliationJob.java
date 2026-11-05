package com.AccountReceivableManagement.service_Imple.receivable;

import com.AccountReceivableManagement.service_interface.receivable.ReceivableRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * G-21: periodically creates the missing receivable of every invoice whose
 * delivery is SENT - the same system step that normally follows a send, so
 * it is independent of automatic billing and of the retry policy. Off with
 * {@code ar.receivable-reconciliation.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ar.receivable-reconciliation", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ReceivableReconciliationJob {

    private final ReceivableRecoveryService receivableRecoveryService;

    @Value("${ar.receivable-reconciliation.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(cron = "${ar.receivable-reconciliation.cron:0 */15 * * * *}")
    public void reconcile() {
        try {
            int created = receivableRecoveryService.reconcileSentInvoicesWithoutReceivable(batchSize);
            if (created > 0) {
                log.warn("[ReceivableReconciliation] created {} missing receivable(s) of sent invoices.", created);
            }
        } catch (RuntimeException ex) {
            log.error("[ReceivableReconciliation] run aborted: {}", ex.getClass().getSimpleName());
        }
    }
}
