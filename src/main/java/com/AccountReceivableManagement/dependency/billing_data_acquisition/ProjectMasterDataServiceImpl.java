package com.AccountReceivableManagement.dependency.billing_data_acquisition;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves a project's owning client from the local CDC-synced
 * {@link ProjectMasterReference} (PMS -> CDC -> project_master_reference).
 * Never falls back to a placeholder id: a snapshot stamped with a client id
 * that has no {@code client} row breaks every downstream client lookup
 * (e.g. the recipient email used by Send to Client).
 */
@Service
@RequiredArgsConstructor
public class ProjectMasterDataServiceImpl implements ProjectMasterDataService {

    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;

    @Override
    public UUID getClientIdByProjectId(Long projectId) {

        UUID clientId =
                projectMasterReferenceRepository.findBypmsProjectId(projectId)
                        .map(ProjectMasterReference::getClientId)
                        .orElse(null);

        if (clientId == null) {
            throw new GlobalExceptionHandler.ResourceNotFoundException(
                    "Client could not be resolved for project " + projectId + "."
            );
        }

        return clientId;
    }
}
