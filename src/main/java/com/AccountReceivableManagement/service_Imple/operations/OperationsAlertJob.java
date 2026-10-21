package com.AccountReceivableManagement.service_Imple.operations;

import com.AccountReceivableManagement.config.properties.OperationsMonitoringProperties;
import com.AccountReceivableManagement.dto.operations.OperationsAlertDto;
import com.AccountReceivableManagement.service_interface.operations.OperationsReportService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically evaluates the configured operations alert thresholds
 * ({@code ar.operations.alerts.*}; off by default). A raised alert is one
 * WARN line {@code [OperationsAlert] condition=… value=… threshold=…} and an
 * increment of {@code ar.operations.alerts} (tag {@code condition}) - routing
 * to people is left to the log/metrics platform. Read-only: evaluation only
 * counts persisted records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationsAlertJob {

    private final OperationsReportService reportService;
    private final OperationsMonitoringProperties properties;
    private final MeterRegistry registry;

    @Scheduled(cron = "${ar.operations.alerts.cron:0 0 * * * *}")
    public void scheduledEvaluation() {
        if (properties.getAlerts().isEnabled()) {
            evaluate();
        }
    }

    /** @return the raised alerts (empty when nothing exceeds its threshold or nothing is configured) */
    public List<OperationsAlertDto> evaluate() {
        List<OperationsAlertDto> evaluated;
        try {
            evaluated = reportService.evaluateAlerts();
        } catch (RuntimeException ex) {
            // Database unavailable or similar: the evaluation itself failing is worth an alert line.
            log.error("[OperationsAlert] evaluation failed: {}", ex.getClass().getSimpleName());
            registry.counter("ar.operations.alerts", "condition", "EVALUATION_FAILED").increment();
            return List.of();
        }
        List<OperationsAlertDto> raised = evaluated.stream().filter(OperationsAlertDto::raised).toList();
        for (OperationsAlertDto alert : raised) {
            log.warn("[OperationsAlert] condition={} value={} threshold={}",
                    alert.condition(), alert.value(), alert.threshold());
            registry.counter("ar.operations.alerts", "condition", alert.condition().name()).increment();
        }
        log.info("[OperationsAlertCheck] conditions evaluated={} raised={}", evaluated.size(), raised.size());
        return raised;
    }
}
