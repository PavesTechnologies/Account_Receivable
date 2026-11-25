package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.mapping.ColumnMapping;
import com.AccountReceivableManagement.cdc.mapping.MilestoneCdcMappingRegistry;
import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.entity.project_entity.ProjectMilestoneReference;
import com.AccountReceivableManagement.repo.project.ProjectMilestoneReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Applies PMS project_milestones changes to project_milestone_reference.
 *
 * Idempotent and order-tolerant: create, update and snapshot-read events are upserts
 * that are skipped when the incoming PMS version is not newer than the stored one, and
 * deletes become a tombstone (source_deleted = true) so a replayed or late create never
 * resurrects a row. A tombstone is cleared only by an event that is strictly newer than
 * the delete - PMS holding the milestone again (see {@link #isResurrection}).
 * Used by both the live CDC inbox and the initial sync of existing rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneDataProcessorImpl implements MilestoneDataProcessor {

    private final ProjectMilestoneReferenceRepository milestoneReferenceRepository;
    private final CdcValueConverter valueConverter;

    @Override
    @Transactional
    public void process(CdcEventPayload payload) {
        String operation = payload.getOperation();
        log.info("[MILESTONE-CDC] Operation: {}, EntityId: {}", operation, payload.getEntityId());

        switch (operation) {
            case "c", "u", "r" -> {
                Map<String, Object> after = payload.getAfter();
                if (after == null) {
                    log.warn("[MILESTONE-CDC] After payload is null for operation '{}' and entityId '{}'",
                            operation, payload.getEntityId());
                    return;
                }
                upsert(after);
            }
            case "d" -> {
                Map<String, Object> before = payload.getBefore();
                if (before == null) {
                    log.warn("[MILESTONE-CDC] Before payload is null for delete and entityId '{}'", payload.getEntityId());
                    return;
                }
                markDeleted(before);
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private void upsert(Map<String, Object> data) {
        Long milestoneId = requireMilestoneId(data);
        Long incomingVersion = convert(data, "version", Long.class);

        ProjectMilestoneReference reference = milestoneReferenceRepository.findById(milestoneId).orElse(null);
        if (reference != null) {
            if (Boolean.TRUE.equals(reference.getSourceDeleted())) {
                if (!isResurrection(incomingVersion, reference.getSourceVersion())) {
                    // Cannot be shown to be newer than the delete, so it is the late-create case the tombstone guards
                    // against. WARN, not INFO: if PMS really does still hold this milestone, the row is now stuck and
                    // every occurrence billing it waits for good, which is worth seeing in the log.
                    log.warn("[MILESTONE-CDC] Milestone {} is deleted in PMS and the incoming event is not newer "
                                    + "(incoming v{}, stored v{}); ignoring event",
                            milestoneId, incomingVersion, reference.getSourceVersion());
                    return;
                }
                log.info("[MILESTONE-CDC] Milestone {} reappeared in PMS (incoming v{} > stored v{}); "
                                + "clearing the tombstone", milestoneId, incomingVersion, reference.getSourceVersion());
            }
            if (isStale(incomingVersion, reference.getSourceVersion())) {
                log.info("[MILESTONE-CDC] Skipping stale event for milestone {} (incoming v{}, stored v{})",
                        milestoneId, incomingVersion, reference.getSourceVersion());
                return;
            }
        } else {
            reference = new ProjectMilestoneReference();
        }

        applyColumns(data, reference);
        reference.setPmsMilestoneId(milestoneId);
        reference.setSourceDeleted(Boolean.FALSE);
        reference.setLastSyncedAt(LocalDateTime.now());
        milestoneReferenceRepository.save(reference);
        log.info("[MILESTONE-CDC] Upserted milestone {} (v{})", milestoneId, incomingVersion);
    }

    private void markDeleted(Map<String, Object> data) {
        Long milestoneId = requireMilestoneId(data);
        ProjectMilestoneReference reference = milestoneReferenceRepository.findById(milestoneId)
                .orElseGet(() -> {
                    // Delete arrived before (or without) its create: keep a tombstone so a late create is ignored.
                    ProjectMilestoneReference tombstone = new ProjectMilestoneReference();
                    applyColumns(data, tombstone);
                    tombstone.setPmsMilestoneId(milestoneId);
                    return tombstone;
                });
        reference.setSourceDeleted(Boolean.TRUE);
        reference.setLastSyncedAt(LocalDateTime.now());
        milestoneReferenceRepository.save(reference);
        log.info("[MILESTONE-CDC] Marked milestone {} as deleted in PMS", milestoneId);
    }

    static boolean isStale(Long incomingVersion, Long storedVersion) {
        return incomingVersion != null && storedVersion != null && incomingVersion <= storedVersion;
    }

    /**
     * Whether a create / update / snapshot-read event for a tombstoned milestone proves PMS holds the row again, so the
     * tombstone may be cleared: both versions are known and the incoming one is strictly newer than the version stored
     * when the delete was applied.
     *
     * <p>Without this, {@code source_deleted} was a one-way latch: once set, every later event was dropped, so a
     * milestone PMS restored (or one tombstoned by a spurious delete) could never come back - not even through the full
     * re-read in {@code MilestoneInitialSyncRunner}, which applies its rows through this same upsert. A permanently
     * tombstoned row can never reach READY_FOR_TAX, so its occurrences wait in SCHEDULED for good.</p>
     *
     * <p>Requiring a strictly newer version keeps the guard the tombstone exists for. The initial-sync runner reads PMS
     * with its own SELECT while live CDC streams, so its snapshot can be older than a delete already applied; such a row
     * carries a version at or below the stored one and is still ignored. An unknown version on either side is not
     * evidence either, and is ignored too.</p>
     */
    static boolean isResurrection(Long incomingVersion, Long storedVersion) {
        return incomingVersion != null && storedVersion != null && incomingVersion > storedVersion;
    }

    private Long requireMilestoneId(Map<String, Object> data) {
        Long milestoneId = convert(data, "id", Long.class);
        if (milestoneId == null) {
            throw new IllegalArgumentException("PMS milestone id cannot be null");
        }
        return milestoneId;
    }

    private <T> T convert(Map<String, Object> data, String column, Class<T> type) {
        Object value = valueConverter.convertValue(data.get(column), MilestoneCdcMappingRegistry.PMS_TO_AR.get(column));
        return type.cast(value);
    }

    private void applyColumns(Map<String, Object> data, ProjectMilestoneReference reference) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            ColumnMapping mapping = MilestoneCdcMappingRegistry.PMS_TO_AR.get(entry.getKey());
            if (mapping == null) {
                log.debug("[MILESTONE-CDC] No mapping for PMS column: {}", entry.getKey());
                continue;
            }
            try {
                Object converted = valueConverter.convertValue(entry.getValue(), mapping);
                Field field = ProjectMilestoneReference.class.getDeclaredField(mapping.getTargetField());
                field.setAccessible(true);
                field.set(reference, converted);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Cannot map PMS column '" + entry.getKey() + "'", e);
            }
        }
    }
}
