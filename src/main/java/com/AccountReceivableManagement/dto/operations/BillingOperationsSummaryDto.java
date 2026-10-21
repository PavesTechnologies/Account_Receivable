package com.AccountReceivableManagement.dto.operations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Operational summary of automatic billing, read from persisted records at
 * {@code generatedAt}. Sections are kept apart on purpose:
 * {@code automaticBillingRuns} is <em>execution</em> status (what automation
 * did), {@code invoices} and {@code receivables} are <em>business</em> status
 * (where Finance's work stands). A run's PENDING_APPROVAL does not say the
 * invoice is still pending - read the invoice section for that.
 */
public record BillingOperationsSummaryDto(
        LocalDateTime generatedAt,
        RunSection automaticBillingRuns,
        OccurrenceSection occurrences,
        InvoiceSection invoices,
        ReceivableSection receivables) {

    /** Execution status of automatic billing runs. */
    public record RunSection(
            long total,
            Map<String, Long> byStatus,
            List<TypeStatusCount> byBillingTypeAndStatus,
            /** Runs created per day within the requested window, by current status. */
            int windowDays,
            List<DateStatusCount> byCreatedDateAndStatus,
            /** Current FAILED runs that need a person, by the step that failed. */
            Map<String, Long> needingInterventionByFailureStep,
            /** Current FAILED runs waiting for an automatic retry, by step. */
            Map<String, Long> retryScheduledByFailureStep,
            long needingIntervention,
            long retryScheduled,
            /** FAILED, no retry left, after the configured maximum attempts. */
            long retriesExhausted,
            /** Runs that needed more than one attempt (any status). */
            long retried,
            /** Runs that failed at least once and later reached PENDING_APPROVAL/COMPLETED. */
            long recovered,
            /** PROCESSING with an expired claim. */
            long stuck) {
    }

    public record OccurrenceSection(
            /** Due (billing date today or earlier) occurrences of automatic configurations with no run yet. */
            long dueWithoutRun,
            /** Of those, past the configured grace period. */
            long overdueWithoutRun,
            int overdueGraceDays,
            LocalDate oldestOverdueBillingDate) {
    }

    /** Business status of invoices (manual and automatic). */
    public record InvoiceSection(
            long total,
            Map<String, Long> byStatus,
            List<TypeStatusCount> byBillingTypeAndStatus,
            /** Invoices created by automatic billing runs, by status. */
            Map<String, Long> automaticByStatus,
            long awaitingApproval,
            long approvedWithoutDocument,
            /** READY_TO_SEND and not yet successfully sent - the manual delivery queue. */
            long readyForDelivery,
            long sent,
            /** At least one failed delivery attempt and no successful one. */
            long deliveryFailedNotSent,
            long failedDeliveryAttempts) {
    }

    public record ReceivableSection(
            long total,
            Map<String, Long> byStatus,
            /** Sent invoices that have their receivable. */
            long sentWithReceivable,
            long sentWithoutReceivable) {
    }

    public record TypeStatusCount(String billingType, String status, long count) {
    }

    public record DateStatusCount(LocalDate date, String status, long count) {
    }
}
