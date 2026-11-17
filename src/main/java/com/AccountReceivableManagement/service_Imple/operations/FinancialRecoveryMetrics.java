package com.AccountReceivableManagement.service_Imple.operations;

import com.AccountReceivableManagement.repo.operations.OperationsReportRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Gauges for the states the recovery paths (G-18..G-21) resolve - each should
 * normally be zero. No labels: counts only, never invoice, client or email
 * identifiers. Read from the database on scrape; "unknown" (NaN) if it is
 * unavailable. Delivery and receivable counters are recorded where they
 * happen: ar.invoice.delivery.outcomes{outcome}, ar.receivable.reconciliation{outcome},
 * ar.receivable.creation.failures.
 */
@Slf4j
@Component
public class FinancialRecoveryMetrics {

    private final MeterRegistry registry;
    private final OperationsReportRepository queries;

    @Value("${ar.invoice-delivery.pending-timeout:PT10M}")
    private Duration pendingTimeout = Duration.ofMinutes(10);

    public FinancialRecoveryMetrics(MeterRegistry registry, OperationsReportRepository queries) {
        this.registry = registry;
        this.queries = queries;
    }

    @PostConstruct
    void registerGauges() {
        gauge("ar.invoice.deliveries.unknown_outcome",
                "Deliveries UNCERTAIN or PENDING past the in-flight timeout - need operator resolution",
                () -> queries.countDeliveriesWithUnknownOutcome(LocalDateTime.now().minus(pendingTimeout)));
        gauge("ar.receivables.sent_without_receivable",
                "Invoices with a SENT delivery but no receivable - reconciliation should clear these",
                () -> queries.countSentInvoices(false));
        gauge("ar.automatic_billing.runs.approved_not_completed",
                "PENDING_APPROVAL runs whose invoice is already approved - reconciliation should clear these",
                queries::countRunsAwaitingCompletionOfApprovedInvoice);
    }

    private void gauge(String name, String description, Supplier<Long> count) {
        Gauge.builder(name, () -> {
                    try {
                        return count.get();
                    } catch (RuntimeException ex) {
                        log.debug("[FinancialRecoveryMetrics] {} unavailable: {}", name, ex.getClass().getSimpleName());
                        return Double.NaN;
                    }
                })
                .description(description)
                .register(registry);
    }
}
