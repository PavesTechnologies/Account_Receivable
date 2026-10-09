package com.AccountReceivableManagement.service_Imple.concurrency_approval;

import com.AccountReceivableManagement.dto.UserDTO;
import com.AccountReceivableManagement.dto.concurrency_approval.RecordLockResponse;
import com.AccountReceivableManagement.entity.concurrency_approval.RecordActionLock;
import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.global_exception_handler.RecordLockedException;
import com.AccountReceivableManagement.repo.concurrency_approval.RecordActionLockRepository;
import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.service_interface.concurrency_approval.RecordActionLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordActionLockServiceImpl implements RecordActionLockService {

    private static final long LOCK_DURATION_MINUTES = 10;

    private final RecordActionLockRepository lockRepository;

    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public RecordLockResponse acquireLock(
            LockResourceType resourceType,
            UUID resourceId,
            LockActionType actionType) {

        UserDTO currentUser = currentUserService.getCurrentUser();

        LocalDateTime now = LocalDateTime.now();

        Optional<RecordActionLock> existingLockOptional =
                lockRepository
                        .findByResourceTypeAndResourceId(resourceType, resourceId);

        if (existingLockOptional.isPresent()) {

            RecordActionLock existingLock = existingLockOptional.get();

            /*
             * Existing lock has expired.
             * We can reuse it for the current user.
             */
            if (existingLock.getExpiresAt().isBefore(now)) {

                updateLock(
                        existingLock,
                        currentUser,
                        actionType,
                        now
                );

                return buildResponse(
                        existingLock,
                        currentUser.getId()
                );
            }

            /*
             * Same user already owns the lock.
             *
             * Refresh the lock instead of creating another one.
             */
            if (existingLock.getLockedByUserId()
                    .equals(currentUser.getId())) {

                existingLock.setLastHeartbeatAt(now);
                existingLock.setExpiresAt(
                        now.plusMinutes(LOCK_DURATION_MINUTES)
                );

                /*
                 * If the user is performing another action on
                 * the same record, update the action type.
                 */
                existingLock.setActionType(actionType);

                lockRepository.save(existingLock);

                return buildResponse(
                        existingLock,
                        currentUser.getId()
                );
            }

            /*
             * Another user currently owns the lock.
             */
            throw new RecordLockedException(
                    buildLockMessage(existingLock, resourceType)
            );
        }

        /*
         * No existing lock.
         * Create a new lock.
         */
        RecordActionLock newLock = RecordActionLock.builder()
                .id(UUID.randomUUID())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .actionType(actionType)
                .lockedByUserId(currentUser.getId())
                .lockedByUsername(currentUser.getEmail())
                .lockedByDisplayName(currentUser.getName())
                .acquiredAt(now)
                .lastHeartbeatAt(now)
                .expiresAt(
                        now.plusMinutes(LOCK_DURATION_MINUTES)
                )
                .build();

        try {

            lockRepository.saveAndFlush(newLock);

        } catch (DataIntegrityViolationException ex) {

            /*
             * Another request may have acquired the lock
             * between our SELECT and INSERT.
             *
             * The unique constraint on
             * (resource_type, resource_id)
             * protects against this race condition.
             */
            throw new RecordLockedException(
                    "This record is currently being accessed by another user. Please try again."
            );
        }

        return buildResponse(
                newLock,
                currentUser.getId()
        );
    }

    /**
     * Returns the current lock status of a record.
     */
    @Override
    @Transactional(readOnly = true)
    public RecordLockResponse getLockStatus(
            LockResourceType resourceType,
            UUID resourceId) {

        Optional<RecordActionLock> lockOptional =
                lockRepository
                        .findByResourceTypeAndResourceId(
                                resourceType,
                                resourceId
                        );

        /*
         * No lock exists.
         */
        if (lockOptional.isEmpty()) {
            return unlockedResponse(resourceType, resourceId);
        }

        RecordActionLock lock = lockOptional.get();

        /*
         * Expired lock should be treated as unlocked.
         */
        if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
            return unlockedResponse(resourceType, resourceId);
        }

        Long currentUserId = currentUserService.getUserId();

        return buildResponse(
                lock,
                currentUserId
        );
    }

    /**
     * Extends the lock expiration time.
     *
     * Called periodically by the frontend while the user
     * is actively editing/reviewing.
     */
    @Override
    @Transactional
    public RecordLockResponse heartbeat(
            LockResourceType resourceType,
            UUID resourceId) {

        UserDTO currentUser = currentUserService.getCurrentUser();

        RecordActionLock lock =
                lockRepository
                        .findByResourceTypeAndResourceId(
                                resourceType,
                                resourceId
                        )
                        .orElseThrow(() ->
                                new RecordLockedException(
                                        "No active lock exists for this record."
                                )
                        );

        /*
         * Check expiration first.
         */
        if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RecordLockedException(
                    "Your lock has expired. Please reopen the record."
            );
        }

        /*
         * Only the user who acquired the lock can
         * send the heartbeat.
         */
        if (!lock.getLockedByUserId()
                .equals(currentUser.getId())) {

            throw new RecordLockedException(
                    buildLockMessage(lock, resourceType)
            );
        }

        LocalDateTime now = LocalDateTime.now();

        lock.setLastHeartbeatAt(now);
        lock.setExpiresAt(
                now.plusMinutes(LOCK_DURATION_MINUTES)
        );

        lockRepository.save(lock);

        return buildResponse(
                lock,
                currentUser.getId()
        );
    }

    /**
     * Releases the lock.
     *
     * Called when the user saves, approves, rejects,
     * cancels or leaves the active operation.
     */
    @Override
    @Transactional
    public void releaseLock(
            LockResourceType resourceType,
            UUID resourceId) {

        Long currentUserId = currentUserService.getUserId();

        Optional<RecordActionLock> lockOptional =
                lockRepository
                        .findByResourceTypeAndResourceId(
                                resourceType,
                                resourceId
                        );

        if (lockOptional.isEmpty()) {
            return;
        }

        RecordActionLock lock = lockOptional.get();

        /*
         * If the lock has expired, simply remove it.
         */
        if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {

            lockRepository
                    .deleteByResourceTypeAndResourceId(
                            resourceType,
                            resourceId
                    );

            return;
        }

        /*
         * Only the lock owner can release it.
         */
        if (!lock.getLockedByUserId()
                .equals(currentUserId)) {

            throw new RecordLockedException(
                    "You cannot release a lock owned by another user."
            );
        }

        lockRepository
                .deleteByResourceTypeAndResourceId(
                        resourceType,
                        resourceId
                );
    }

    /**
     * Validates that the current authenticated user owns
     * the lock for this record.
     *
     * This should be called from the actual update/approval
     * business operation.
     */
    @Override
    @Transactional(readOnly = true)
    public void validateLockOwner(
            LockResourceType resourceType,
            UUID resourceId) {

        Long currentUserId = currentUserService.getUserId();

        RecordActionLock lock =
                lockRepository
                        .findByResourceTypeAndResourceId(
                                resourceType,
                                resourceId
                        )
                        .orElseThrow(() ->
                                new RecordLockedException(
                                        "You do not have an active lock for this record."
                                )
                        );

        /*
         * Lock expired.
         */
        if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RecordLockedException(
                    "Your lock has expired. Please reopen the record."
            );
        }

        /*
         * Another user owns the lock.
         */
        if (!lock.getLockedByUserId()
                .equals(currentUserId)) {

            throw new RecordLockedException(
                    buildLockMessage(lock, resourceType)
            );
        }
    }

    /**
     * Blocks the operation only when another user holds
     * an active lock on this record.
     *
     * Unlike validateLockOwner, a missing or expired lock
     * is allowed, so section saves inside an editing
     * session do not need a lock of their own.
     */
    @Override
    @Transactional(readOnly = true)
    public void validateNotLockedByAnotherUser(
            LockResourceType resourceType,
            UUID resourceId) {

        Optional<RecordActionLock> lockOptional =
                lockRepository
                        .findByResourceTypeAndResourceId(
                                resourceType,
                                resourceId
                        );

        if (lockOptional.isEmpty()) {
            return;
        }

        RecordActionLock lock = lockOptional.get();

        if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
            return;
        }

        if (!lock.getLockedByUserId()
                .equals(currentUserService.getUserId())) {

            throw new RecordLockedException(
                    buildLockMessage(lock, resourceType)
            );
        }
    }

    /**
     * Updates an expired lock with the current user.
     */
    private void updateLock(
            RecordActionLock lock,
            UserDTO currentUser,
            LockActionType actionType,
            LocalDateTime now) {

        lock.setActionType(actionType);
        lock.setLockedByUserId(currentUser.getId());
        lock.setLockedByUsername(currentUser.getEmail());
        lock.setLockedByDisplayName(currentUser.getName());
        lock.setAcquiredAt(now);
        lock.setLastHeartbeatAt(now);
        lock.setExpiresAt(
                now.plusMinutes(LOCK_DURATION_MINUTES)
        );

        lockRepository.save(lock);
    }

    /**
     * Converts entity into API response.
     */
    private RecordLockResponse buildResponse(
            RecordActionLock lock,
            Long currentUserId) {

        return RecordLockResponse.builder()
                .locked(true)
                .resourceId(lock.getResourceId())
                .resourceType(lock.getResourceType())
                .actionType(lock.getActionType())
                .lockedByUserId(
                        lock.getLockedByUserId()
                )
                .lockedByUsername(
                        lock.getLockedByUsername()
                )
                .lockedByDisplayName(
                        lock.getLockedByDisplayName()
                )
                .acquiredAt(
                        lock.getAcquiredAt()
                )
                .expiresAt(
                        lock.getExpiresAt()
                )
                .currentUser(
                        lock.getLockedByUserId()
                                .equals(currentUserId)
                )
                .build();
    }

    /**
     * Response when no active lock exists.
     */
    private RecordLockResponse unlockedResponse(
            LockResourceType resourceType,
            UUID resourceId) {

        return RecordLockResponse.builder()
                .locked(false)
                .resourceId(resourceId)
                .resourceType(resourceType)
                .currentUser(false)
                .build();
    }

    /**
     * Creates a user-friendly lock message.
     */
    private String buildLockMessage(
            RecordActionLock lock,
            LockResourceType resourceType) {

        String displayName = lock.getLockedByDisplayName();

        if (displayName == null || displayName.isBlank()) {
            displayName = lock.getLockedByUsername();
        }

        String actionMessage;

        if (lock.getActionType() == LockActionType.EDIT) {
            actionMessage = "edited";
        } else if (lock.getActionType() == LockActionType.APPROVAL) {
            actionMessage = "reviewed/approved";
        } else {
            actionMessage = "accessed";
        }

        String resourceName =
                resourceType == LockResourceType.BILLING_CONFIGURATION
                        ? "billing configuration"
                        : "invoice";

        return "This " + resourceName +
                " is currently being " +
                actionMessage +
                " by " +
                displayName +
                ". Please try again later.";
    }
}
