package com.AccountReceivableManagement.dto.recovery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One entry of a run's operational history (requeue, approval reconciliation). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomaticBillingRunEventDto {
    private UUID eventId;
    private UUID runId;
    private String eventType;
    private String statusBefore;
    private String statusAfter;
    private String failureStep;
    private String failureReason;
    private LocalDateTime failedAt;
    private Integer attemptCount;
    private String actor;
    private Long actorUserId;
    private String reason;
    private LocalDateTime occurredAt;
}
