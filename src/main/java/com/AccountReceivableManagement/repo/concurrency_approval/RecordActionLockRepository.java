package com.AccountReceivableManagement.repo.concurrency_approval;

import com.AccountReceivableManagement.entity.concurrency_approval.RecordActionLock;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordActionLockRepository
        extends JpaRepository<RecordActionLock, UUID> {

    Optional<RecordActionLock> findByResourceTypeAndResourceId(
            LockResourceType resourceType,
            UUID resourceId
    );

    void deleteByResourceTypeAndResourceId(
            LockResourceType resourceType,
            UUID resourceId
    );
}
