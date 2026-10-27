package com.AccountReceivableManagement.controller.concurrency_approval;

import com.AccountReceivableManagement.dto.concurrency_approval.AcquireLockRequest;
import com.AccountReceivableManagement.dto.concurrency_approval.RecordLockResponse;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.service_Imple.concurrency_approval.RecordActionLockServiceImpl;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/record-locks")
@RequiredArgsConstructor
public class RecordActionLockController {

    private final RecordActionLockService lockService;

    @PostMapping("/{resourceType}/{resourceId}")
    public ResponseEntity<RecordLockResponse> acquireLock(

            @PathVariable
            LockResourceType resourceType,

            @PathVariable
            UUID resourceId,

            @Valid
            @RequestBody
            AcquireLockRequest request) {

        RecordLockResponse response =
                lockService.acquireLock(
                        resourceType,
                        resourceId,
                        request.getActionType()
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Get lock status.
     */
    @GetMapping("/{resourceType}/{resourceId}")
    public ResponseEntity<RecordLockResponse> getLockStatus(

            @PathVariable
            LockResourceType resourceType,

            @PathVariable
            UUID resourceId) {

        RecordLockResponse response =
                lockService.getLockStatus(
                        resourceType,
                        resourceId
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Heartbeat.
     */
    @PutMapping("/{resourceType}/{resourceId}/heartbeat")
    public ResponseEntity<RecordLockResponse> heartbeat(

            @PathVariable
            LockResourceType resourceType,

            @PathVariable
            UUID resourceId) {

        RecordLockResponse response =
                lockService.heartbeat(
                        resourceType,
                        resourceId
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Release lock.
     */
    @DeleteMapping("/{resourceType}/{resourceId}")
    public ResponseEntity<Void> releaseLock(

            @PathVariable
            LockResourceType resourceType,

            @PathVariable
            UUID resourceId) {

        lockService.releaseLock(
                resourceType,
                resourceId
        );

        return ResponseEntity.noContent().build();
    }
}
