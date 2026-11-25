package com.AccountReceivableManagement.cdc.scheduler;

import com.AccountReceivableManagement.cdc.service.CdcInboxService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports CDC inbox events that have exhausted their retries.
 *
 * <p>A dead-lettered event means a change PMS or RMS made was never applied to the AR mirror: the row stays as it was,
 * so a milestone can sit at its old execution status or planned due date and never become READY_FOR_TAX, holding its
 * billing occurrence in SCHEDULED indefinitely. Nothing about that is visible from AR's own data, which is why it is
 * reported here rather than left to whoever notices the invoice is missing.</p>
 *
 * <p>The one ERROR line written when an event is abandoned is easy to miss and is gone after a restart, so this job
 * re-states the outstanding total every run: a WARN line per (connector, entity type) and the gauge
 * {@code ar.cdc.inbox.dead_letter}, which a dashboard can alert on. Read-only - it never changes an event. Clearing the
 * backlog is a deliberate act: fix the cause, then call {@code CdcInboxService.replayDeadLetters()}.</p>
 */
@Slf4j
@Component
// Follows the inbox processor: an instance that does not process the inbox does not report on it either.
@ConditionalOnProperty(name = "ar.cdc.inbox-processor.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CdcInboxDeadLetterMonitor {

    private final CdcInboxService cdcInboxService;
    private final MeterRegistry registry;

    /** Last observed count, published as a gauge so it is visible between runs and after a restart. */
    private final AtomicLong deadLetterCount = new AtomicLong();

    @Autowired
    public void registerGauge(MeterRegistry meterRegistry) {
        meterRegistry.gauge("ar.cdc.inbox.dead_letter", deadLetterCount);
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void reportDeadLetters() {
        try {
            long count = cdcInboxService.getDeadLetterCount();
            deadLetterCount.set(count);
            if (count == 0) {
                return;
            }

            List<Object[]> summary = cdcInboxService.summariseDeadLetters();
            for (Object[] row : summary) {
                String connectorName = (String) row[0];
                String entityType = (String) row[1];
                long rowCount = ((Number) row[2]).longValue();
                LocalDateTime oldest = (LocalDateTime) row[3];
                log.warn("[CDC-INBOX] {} dead-lettered event(s) connector={} entityType={} oldest={}. "
                                + "These changes are NOT in the AR mirror and will not be retried; "
                                + "fix the cause, then replay them.",
                        rowCount, connectorName, entityType, oldest);
                registry.counter("ar.cdc.inbox.dead_letter.reported",
                        "connector", String.valueOf(connectorName),
                        "entityType", String.valueOf(entityType)).increment();
            }
        } catch (Exception e) {
            // Never let reporting break the scheduler; the next run tries again.
            log.error("[CDC-INBOX] Dead-letter reporting failed", e);
        }
    }
}
