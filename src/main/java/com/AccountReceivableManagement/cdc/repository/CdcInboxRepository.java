package com.AccountReceivableManagement.cdc.repository;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CdcInboxRepository extends JpaRepository<CdcInbox, UUID> {

    /**
     * Find all pending events
     */
    List<CdcInbox> findByStatus(CdcInbox.ProcessingStatus status);

    /**
     * Atomic claim: one conditional UPDATE, so of any number of instances racing to pick up
     * the same row (e.g. two pods overlapping during a rolling deploy) exactly one sees {@code 1}
     * - the database row lock decides, not a read-then-write in Java.
     *
     * @return 1 if this caller now owns the event, 0 if another instance already claimed it
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CdcInbox c SET c.status = com.AccountReceivableManagement.cdc.entity.CdcInbox.ProcessingStatus.PROCESSING, "
            + "c.updatedAt = :now "
            + "WHERE c.id = :id AND c.status = com.AccountReceivableManagement.cdc.entity.CdcInbox.ProcessingStatus.PENDING")
    int claim(@Param("id") UUID id, @Param("now") LocalDateTime now);

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
