package com.AccountReceivableManagement.dto.operations;

import com.AccountReceivableManagement.entity_enums.operations.ReconciliationCheck;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of one reconciliation pass. Findings are derived from persisted
 * records on every request and are never stored: a finding is OPEN while
 * the discrepancy exists and disappears once it has been corrected through
 * the authorized process. Track the investigation in the existing incident
 * tracker using {@code check} + {@code recordId}.
 */
public record ReconciliationReportDto(
        LocalDateTime generatedAt,
        int checksExecuted,
        long totalFindings,
        /** Findings of HIGH or CRITICAL severity. */
        long highOrCriticalFindings,
        List<CheckResult> checks) {

    public record CheckResult(
            ReconciliationCheck check,
            ReconciliationCheck.Severity severity,
            ReconciliationCheck.Owner owner,
            String businessImpact,
            String correctiveAction,
            /** Complete count of findings for this check. */
            long findingCount,
            /** Of those, findings of HIGH or CRITICAL severity. */
            long highOrCriticalCount,
            /** At most {@code ar.operations.reconciliation.max-findings-per-check}. */
            boolean truncated,
            List<Finding> findings) {
    }

    /**
     * One discrepancy. {@code severity} may be lower than the check's default
     * (e.g. a difference of a paisa per line - the known pre-UAT-D2 rounding).
     */
    public record Finding(
            ReconciliationCheck.RecordType recordType,
            UUID recordId,
            /** Human reference (invoice number, snapshot number, billing date). */
            String reference,
            UUID relatedId,
            String expected,
            String actual,
            ReconciliationCheck.Severity severity,
            String detail) {
    }
}
