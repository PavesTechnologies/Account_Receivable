package com.AccountReceivableManagement.dto.operations;

import java.time.LocalDateTime;

/**
 * One group of CDC inbox events that exhausted their retries: how many are outstanding for a source and entity type,
 * and when the oldest of them arrived. The age matters more than the count - a change that has been unapplied for days
 * means the AR mirror has been wrong for days.
 *
 * @param connectorName the Debezium connector the events came from (e.g. {@code project-cdc})
 * @param entityType    what they would have updated (e.g. {@code PMS-PROJECT_MILESTONES})
 * @param count         how many are outstanding
 * @param oldestCreatedAt when the oldest of them was written to the inbox
 */
public record CdcDeadLetterGroupDto(
        String connectorName,
        String entityType,
        long count,
        LocalDateTime oldestCreatedAt) {
}
