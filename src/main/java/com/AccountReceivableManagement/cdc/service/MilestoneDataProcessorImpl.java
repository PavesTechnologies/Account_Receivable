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
 * deletes become a tombstone (source_deleted = true) so replays never resurrect a row.
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
                log.info("[MILESTONE-CDC] Milestone {} already deleted in PMS; ignoring event", milestoneId);
                return;
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
