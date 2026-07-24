package com.AccountReceivableManagement.service;

import com.AccountReceivableManagement.CDC.mapping.ProjectCdcMappingRegistry;
import com.AccountReceivableManagement.CDC.mapping.ColumnMapping;
import com.AccountReceivableManagement.CDC.parsing.CdcValueConverter;
import com.AccountReceivableManagement.CDC.payload.CdcEventPayload;
import com.AccountReceivableManagement.Entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.Repo.project.ProjectMasterReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDataProcessorImpl implements ProjectDataProcessor {

    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;
    private final CdcValueConverter valueConverter;

    @Override
    @Transactional
    public void process(CdcEventPayload payload) {
        String operation = payload.getOperation();
        Map<String, Object> data;

        switch (operation) {
            case "c":
                data = payload.getAfter();
                if (data == null) {
                    log.warn("After payload is null for create operation and entityId '{}'", payload.getEntityId());
                    return;
                }
                handleCreate(data);
                break;
            case "u":
                data = payload.getAfter();
                if (data == null) {
                    log.warn("After payload is null for update operation and entityId '{}'", payload.getEntityId());
                    return;
                }
                handleUpdate(data);
                break;
            case "d":
                data = payload.getBefore();
                if (data == null) {
                    log.warn("Before payload is null for delete operation and entityId '{}'", payload.getEntityId());
                    return;
                }
                handleDelete(data);
                break;
            default:
                log.warn("Unknown operation: {}", operation);
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    private Long getPmsProjectId(Map<String, Object> data) {
        return (Long) valueConverter.convertValue(
                data.get("id"),
                ProjectCdcMappingRegistry.PMS_TO_AR.get("id")
        );
    }

    private void handleCreate(Map<String, Object> data) {
        Long pmsProjectId = getPmsProjectId(data);
        if (pmsProjectId == null) {
            log.error("PMS Project ID is null for create operation.");
            throw new IllegalArgumentException("PMS Project ID cannot be null for create operations.");
        }

        if (projectMasterReferenceRepository.existsBypmsProjectId(pmsProjectId)) {
            log.warn("Project with PMS ID {} already exists. Skipping create operation.", pmsProjectId);
            return;
        }

        ProjectMasterReference project = new ProjectMasterReference();
        updateProjectFromMap(data, project);
        project.setPmsProjectId(pmsProjectId); // Ensure the ID is set
        projectMasterReferenceRepository.save(project);
        log.info("Created project with PMS ID: {}", project.getPmsProjectId());
    }

    private void handleUpdate(Map<String, Object> data) {
        Long pmsProjectId = getPmsProjectId(data);
        if (pmsProjectId == null) {
            log.error("PMS Project ID is null for update operation.");
            throw new IllegalArgumentException("PMS Project ID cannot be null for update operations.");
        }

        ProjectMasterReference existingProject = projectMasterReferenceRepository.findBypmsProjectId(pmsProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found for update with PMS ID: " + pmsProjectId));

        updateProjectFromMap(data, existingProject);
        projectMasterReferenceRepository.save(existingProject);
        log.info("Updated project with PMS ID: {}", pmsProjectId);
    }

    private void handleDelete(Map<String, Object> data) {
        Long pmsProjectId = getPmsProjectId(data);
        if (pmsProjectId == null) {
            log.error("PMS Project ID is null for delete operation.");
            throw new IllegalArgumentException("PMS Project ID cannot be null for delete operations.");
        }

        projectMasterReferenceRepository.findBypmsProjectId(pmsProjectId).ifPresent(project -> {
            projectMasterReferenceRepository.delete(project);
            log.info("Deleted project with PMS ID: {}", pmsProjectId);
        });
    }

    private void updateProjectFromMap(Map<String, Object> data, ProjectMasterReference project) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            ColumnMapping mapping = ProjectCdcMappingRegistry.PMS_TO_AR.get(entry.getKey());

            if (mapping == null) {
                log.debug("No mapping found for PMS column: {}", entry.getKey());
                continue;
            }

            try {
                Object convertedValue = valueConverter.convertValue(entry.getValue(), mapping);
                Field field = ProjectMasterReference.class.getDeclaredField(mapping.getTargetField());
                field.setAccessible(true);
                field.set(project, convertedValue);
            } catch (NoSuchFieldException e) {
                log.warn("Field '{}' not found in ProjectMasterReference for column '{}'", mapping.getTargetField(), entry.getKey());
            } catch (Exception e) {
                log.error("Failed to map column '{}' to field '{}' with value '{}'",
                        entry.getKey(),
                        mapping.getTargetField(),
                        entry.getValue(),
                        e);
            }
        }
    }
}
