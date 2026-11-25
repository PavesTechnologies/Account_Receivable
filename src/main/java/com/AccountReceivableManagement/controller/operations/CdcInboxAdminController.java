package com.AccountReceivableManagement.controller.operations;

import com.AccountReceivableManagement.cdc.service.CdcInboxService;
import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.operations.CdcDeadLetterGroupDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inspecting and replaying CDC inbox events that exhausted their retries.
 *
 * <p>Lives under {@code /api/v1/operations}, so it is covered by the same {@code OperationsReportAccess} authority
 * check as the reports, but is a controller of its own because {@link OperationsReportController} is documented as
 * read-only and the replay here writes.</p>
 *
 * <p>Why it exists: a dead-lettered event is a change from PMS or RMS that never reached the AR mirror, so the mirror
 * is quietly wrong - a milestone can keep an old execution status or planned due date and so never become
 * READY_FOR_TAX, leaving its billing occurrence in SCHEDULED for good. Until now the only thing that recovered such an
 * event was restarting the application, and then only for milestones (the startup re-read of PMS). Replay makes the
 * repair deliberate and available without a restart.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/operations/cdc-inbox")
@RequiredArgsConstructor
public class CdcInboxAdminController {

    private final CdcInboxService cdcInboxService;

    /** What is currently abandoned, grouped by source and entity type. Read-only. */
    @GetMapping("/dead-letters")
    public ResponseEntity<ApiResponse<List<CdcDeadLetterGroupDto>>> getDeadLetters() {
        List<CdcDeadLetterGroupDto> groups = cdcInboxService.summariseDeadLetters().stream()
                .map(row -> new CdcDeadLetterGroupDto(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (LocalDateTime) row[3]))
                .toList();
        return ResponseEntity.ok(ApiResponse.<List<CdcDeadLetterGroupDto>>builder()
                .success(true)
                .message("CDC inbox dead-letter summary retrieved successfully.")
                .data(groups)
                .build());
    }

    /**
     * Queues every dead-lettered event for reprocessing. Run this once the cause of the failure is fixed; the normal
     * processing sweep picks the events up within seconds. Safe to run more than once: every processor upserts by
     * primary key and ignores events that are not newer than what is already stored, so replaying an event that has
     * since been superseded changes nothing.
     */
    @PostMapping("/dead-letters/replay")
    public ResponseEntity<ApiResponse<Integer>> replayDeadLetters() {
        int queued = cdcInboxService.replayDeadLetters();
        log.warn("[CDC-INBOX] Replay of {} dead-lettered event(s) requested through the operations API", queued);
        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .success(true)
                .message(queued == 0
                        ? "No dead-lettered CDC events to replay."
                        : queued + " dead-lettered CDC event(s) queued for replay.")
                .data(queued)
                .build());
    }
}
