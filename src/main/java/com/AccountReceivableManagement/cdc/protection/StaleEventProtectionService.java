package com.AccountReceivableManagement.cdc.protection;

import com.AccountReceivableManagement.cdc.entity.CdcInbox;
import com.AccountReceivableManagement.cdc.repository.CdcInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleEventProtectionService {

    private final CdcInboxRepository cdcInboxRepository;

    /**
     * Check if an event is a duplicate (already processed with same entity ID and operation)
     */
    public boolean isDuplicateEvent(String entityType, String entityId, String operation) {
        List<CdcInbox> existingEvents = cdcInboxRepository.findByEntityId(entityId);

        for (CdcInbox event : existingEvents) {
            if (event.getEntityType().equals(entityType) 
                    && event.getOperation().equals(operation)
                    && event.getStatus() == CdcInbox.ProcessingStatus.PROCESSED) {
                log.info("Duplicate event detected - EntityType: {}, EntityId: {}, Operation: {}, ExistingEventId: {}",
                        entityType, entityId, operation, event.getId());
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an event is stale (older than a specified threshold)
     */
    public boolean isStaleEvent(LocalDateTime eventTimestamp, int staleThresholdHours) {
        if (eventTimestamp == null) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(staleThresholdHours);
        boolean isStale = eventTimestamp.isBefore(threshold);
        
        if (isStale) {
            log.warn("Stale event detected - Timestamp: {}, Threshold: {}", eventTimestamp, threshold);
        }
        
        return isStale;
    }

    /**
     * Check if an event with the same ID already exists in the inbox
     */
    public boolean eventExists(UUID eventId) {
        return cdcInboxRepository.existsById(eventId);
    }

    /**
     * Check if there's a newer event for the same entity
     */
    public boolean hasNewerEvent(String entityId, LocalDateTime currentEventTime) {
        List<CdcInbox> existingEvents = cdcInboxRepository.findByEntityId(entityId);
        
        for (CdcInbox event : existingEvents) {
            if (event.getCreatedAt().isAfter(currentEventTime)) {
                log.info("Newer event exists for entity - EntityId: {}, CurrentEventTime: {}, NewerEventTime: {}",
                        entityId, currentEventTime, event.getCreatedAt());
                return true;
            }
        }
        return false;
    }
}
