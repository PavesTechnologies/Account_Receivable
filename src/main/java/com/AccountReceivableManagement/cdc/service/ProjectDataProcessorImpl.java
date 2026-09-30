package com.AccountReceivableManagement.cdc.service;

import com.AccountReceivableManagement.cdc.mapping.ProjectCdcMappingRegistry;
import com.AccountReceivableManagement.cdc.mapping.ColumnMapping;
import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.repo.client.ClientBudgetSummaryRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.service_interface.client.ClientBudgetSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDataProcessorImpl implements ProjectDataProcessor {

    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;
    private final CdcValueConverter valueConverter;
    private final ClientBudgetSummaryService clientBudgetSummaryService;

    @Override
    public void process(CdcEventPayload payload) {
        String operation = payload.getOperation();
        Map<String, Object> data;

        log.info("[PROJECT-CDC] ProjectDataProcessor.process() called - Operation: {}, EntityId: {}",
                operation, payload.getEntityId());

        switch (operation) {
            case "c":
                log.info("[PROJECT-CDC] CREATE operation detected for EntityId: {}", payload.getEntityId());
                data = payload.getAfter();
                if (data == null) {
                    log.warn("After payload is null for create operation and entityId '{}'", payload.getEntityId());
                    return;
                }
                handleCreate(data);
                // Refresh client budget in separate transaction after project is committed
                Long pmsProjectId = getPmsProjectId(data);
                projectMasterReferenceRepository.findBypmsProjectId(pmsProjectId).ifPresent(project -> {
                    refreshClientBudgetAsync(project.getClientId());
                });
                break;
            case "u":
                data = payload.getAfter();
                if (data == null) {
                    log.warn("After payload is null for update operation and entityId '{}'", payload.getEntityId());
                    return;
                }
                handleUpdate(data);
                // Refresh client budget in separate transaction after update is committed
                Long pmsProjectIdUpdate = getPmsProjectId(data);
                projectMasterReferenceRepository.findBypmsProjectId(pmsProjectIdUpdate).ifPresent(project -> {
                    refreshClientBudgetAsync(project.getClientId());
                });
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

    @Transactional
    private void handleCreate(Map<String, Object> data) {
        Long pmsProjectId = getPmsProjectId(data);
        log.info("[PROJECT-CDC] CREATE START - Extracted pmsProjectId: {}", pmsProjectId);
        
        if (pmsProjectId == null) {
            log.error("PMS Project ID is null for create operation.");
            throw new IllegalArgumentException("PMS Project ID cannot be null for create operations.");
        }

        boolean projectExists = projectMasterReferenceRepository.existsBypmsProjectId(pmsProjectId);
        log.info("[PROJECT-CDC] Existing project check - pmsProjectId: {}, exists: {}", pmsProjectId, projectExists);
        
        if (projectExists) {
            log.warn("Project with PMS ID {} already exists. Skipping create operation.", pmsProjectId);
            return;
        }

        log.info("[PROJECT-CDC] Creating new ProjectMasterReference for pmsProjectId: {}", pmsProjectId);
        ProjectMasterReference project = new ProjectMasterReference();
        updateProjectFromMap(data, project);
        project.setPmsProjectId(pmsProjectId); // Ensure the ID is set
        
        log.info("[PROJECT-CDC] Saving project_master_reference - pmsProjectId: {}, clientId: {}, budget: {}, currency: {}",
                project.getPmsProjectId(), project.getClientId(), project.getProjectBudget(), project.getProjectBudgetCurrency());
        
        ProjectMasterReference savedProject =
                projectMasterReferenceRepository.save(project);
        
        log.info("[PROJECT-CDC] project_master_reference saved successfully - pmsProjectId: {}",
                savedProject.getPmsProjectId());
        
        log.info("[PROJECT-CDC] CREATE COMPLETE - Created project with PMS ID: {}", project.getPmsProjectId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void refreshClientBudgetAsync(UUID clientId) {
        try {
            log.info("[PROJECT-CDC] Refreshing client budget summary for clientId: {}", clientId);
            clientBudgetSummaryService.refreshClientBudget(clientId);
            log.info("[PROJECT-CDC] Client budget summary refreshed successfully for clientId: {}", clientId);
        } catch (Exception e) {
            log.error("Failed to refresh client budget summary for client {}. This runs in a separate transaction and does not affect project save.", 
                    clientId, e);
        }
    }

    @Transactional
    private void handleUpdate(Map<String, Object> data) {
        Long pmsProjectId = getPmsProjectId(data);
        if (pmsProjectId == null) {
            log.error("PMS Project ID is null for update operation.");
            throw new IllegalArgumentException("PMS Project ID cannot be null for update operations.");
        }

        ProjectMasterReference existingProject = projectMasterReferenceRepository.findBypmsProjectId(pmsProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found for update with PMS ID: " + pmsProjectId));

        updateProjectFromMap(data, existingProject);
        ProjectMasterReference updatedProject =
                projectMasterReferenceRepository.save(existingProject);

        log.info("Updated project with PMS ID: {}", pmsProjectId);
    }

    @Transactional
    private void handleDelete(Map<String, Object> data) {

        Long pmsProjectId = getPmsProjectId(data);

        if (pmsProjectId == null) {
            log.error("PMS Project ID is null for delete operation.");
            throw new IllegalArgumentException("PMS Project ID cannot be null for delete operations.");
        }

        projectMasterReferenceRepository.findBypmsProjectId(pmsProjectId)
                .ifPresent(project -> {

                    // Get clientId before deleting
                    var clientId = project.getClientId();

                    // Delete project
                    projectMasterReferenceRepository.delete(project);

                    log.info("Deleted project with PMS ID: {}", pmsProjectId);
                    
                    // Refresh summary in separate transaction after delete is committed
                    refreshClientBudgetAsync(clientId);
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
