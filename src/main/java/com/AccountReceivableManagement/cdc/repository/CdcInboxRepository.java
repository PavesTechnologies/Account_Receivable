package com.AccountReceivableManagement.cdc.repository;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CdcInboxRepository extends JpaRepository<CdcInbox, UUID> {

    /**
     * Find all pending events
     */
    List<CdcInbox> findByStatus(CdcInbox.ProcessingStatus status);

    /**
     * Find pending events for a specific connector
     */
    List<CdcInbox> findByStatusAndConnectorName(CdcInbox.ProcessingStatus status, String connectorName);

    /**
     * Find pending events for a specific entity type
     */
    List<CdcInbox> findByStatusAndEntityType(CdcInbox.ProcessingStatus status, String entityType);

    /**
     * Find events by entity ID
     */
    List<CdcInbox> findByEntityId(String entityId);

    /**
     * Count pending events
     */
    long countByStatus(CdcInbox.ProcessingStatus status);

    /**
     * Find failed events with retry count below threshold
     */
    List<CdcInbox> findByStatusAndRetryCountLessThan(CdcInbox.ProcessingStatus status, int maxRetries);

    /**
     * Dead-lettered events grouped for reporting: one row per (connectorName, entityType) with how many are outstanding
     * and the oldest creation time, so an alert can say what is stale and for how long without loading every payload.
     */
    @Query("""
            SELECT i.connectorName, i.entityType, COUNT(i), MIN(i.createdAt)
            FROM CdcInbox i
            WHERE i.status = com.AccountReceivableManagement.cdc.entity.CdcInbox$ProcessingStatus.DEAD_LETTER
            GROUP BY i.connectorName, i.entityType
            ORDER BY COUNT(i) DESC
            """)
    List<Object[]> summariseDeadLetters();
}
