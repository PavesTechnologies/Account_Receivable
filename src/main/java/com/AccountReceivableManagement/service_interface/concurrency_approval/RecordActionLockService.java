package com.AccountReceivableManagement.service_interface.concurrency_approval;

import com.AccountReceivableManagement.dto.concurrency_approval.RecordLockResponse;
import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;

import java.util.UUID;

public interface RecordActionLockService {

    RecordLockResponse acquireLock(
            LockResourceType resourceType,
            UUID resourceId,
            LockActionType actionType
    );

    /**
     * Get the current lock status of a record.
     */
    RecordLockResponse getLockStatus(
            LockResourceType resourceType,
            UUID resourceId
    );

    /**
     * Extend an existing lock.
     */
    RecordLockResponse heartbeat(
            LockResourceType resourceType,
            UUID resourceId
    );

    /**
     * Release a lock owned by the current user.
     */
    void releaseLock(
            LockResourceType resourceType,
            UUID resourceId
    );

    /**
     * Validate that the current user owns
     * the lock before performing the actual
     * business operation.
     */
    void validateLockOwner(
            LockResourceType resourceType,
            UUID resourceId
    );

    /**
     * Reject the operation only when another user
     * holds an active lock on the record. Passes when
     * there is no lock, the lock has expired, or the
     * current user owns it.
     *
     * Used for section-level saves inside an editing
     * session (e.g. billing configuration draft saves).
     */
    void validateNotLockedByAnotherUser(
            LockResourceType resourceType,
            UUID resourceId
    );
}
