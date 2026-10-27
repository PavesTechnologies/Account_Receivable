package com.AccountReceivableManagement.dto.concurrency_approval;

import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RecordLockResponse {

    /**
     * Indicates whether the record is currently locked.
     */
    private boolean locked;

    /**
     * ID of the record being locked.
     */
    private UUID resourceId;

    /**
     * Type of record being locked.
     *
     * Example:
     * BILLING_CONFIGURATION
     * INVOICE
     */
    private LockResourceType resourceType;

    /**
     * Action currently being performed.
     *
     * Example:
     * EDIT
     * APPROVAL
     */
    private LockActionType actionType;

    /**
     * ID of the user who currently owns the lock.
     */
    private Long lockedByUserId;

    /**
     * Username of the user who owns the lock.
     */
    private String lockedByUsername;


    private String lockedByDisplayName;

    /**
     * When the lock was acquired.
     */
    private LocalDateTime acquiredAt;

    /**
     * When the current lock will expire
     * if no heartbeat is received.
     */
    private LocalDateTime expiresAt;

    /**
     * Indicates whether the current logged-in user
     * owns this lock.
     *
     * true  -> current user owns the lock
     * false -> another user owns the lock
     */
    private boolean currentUser;
}
