package com.AccountReceivableManagement.config.properties;

import com.AccountReceivableManagement.entity_enums.operations.OperationsAlertCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ar.operations.*}: operational reporting, financial reconciliation
 * and alerting over the persisted billing records. Nothing here changes
 * billing behaviour, and no value is a business SLA: thresholds are unset
 * (not evaluated) until agreed with Finance and configured per environment.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ar.operations")
public class OperationsMonitoringProperties {

    /**
     * JWT authorities (UMS roles as {@code ROLE_<role>}, or permissions) that
     * may read the operations reports. Empty = nobody: the reports expose
     * client and financial data, so access must be granted explicitly.
     */
    @NotNull
    private List<String> reportAuthorities = new ArrayList<>();

    /**
     * Days past its billing date after which a due occurrence of an
     * automatic configuration that still has no run is reported as overdue.
     * A reporting cut-off, not an SLA.
     */
    @Min(0)
    @Max(365)
    private int overdueGraceDays = 1;

    @Valid
    @NotNull
    private Reconciliation reconciliation = new Reconciliation();

    @Valid
    @NotNull
    private Alerts alerts = new Alerts();

    @Getter
    @Setter
    public static class Reconciliation {

        /** Findings listed per check; the count is always complete, the list is bounded. */
        @Min(1)
        @Max(1000)
        private int maxFindingsPerCheck = 100;
    }

    /**
     * {@code ar.operations.alerts.*}: periodic evaluation of the configured
     * thresholds. An alert is raised when a condition's current value is
     * greater than its threshold; conditions without a threshold are never
     * evaluated. Alerts are logged ({@code [OperationsAlert]}, WARN) and
     * counted ({@code ar.operations.alerts}) for the log/metrics platform to
     * route - no notification channel is assumed.
     */
    @Getter
    @Setter
    public static class Alerts {

        /** Off unless explicitly enabled. */
        private boolean enabled = false;

        /** Spring 6-field cron of the evaluation. */
        @NotBlank
        private String cron = "0 0 * * * *";

        /** Condition → threshold (raise when value &gt; threshold). Unset = not evaluated. */
        @NotNull
        private Map<OperationsAlertCondition, Long> thresholds = new EnumMap<>(OperationsAlertCondition.class);

        @AssertTrue(message = "ar.operations.alerts.cron must be a valid 6-field Spring cron expression")
        public boolean isCronValid() {
            return cron != null && CronExpression.isValidExpression(cron);
        }

        @AssertTrue(message = "ar.operations.alerts.thresholds must not be negative")
        public boolean isThresholdsValid() {
            return thresholds.values().stream().allMatch(value -> value == null || value >= 0);
        }
    }
}
