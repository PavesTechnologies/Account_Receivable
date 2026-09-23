package com.AccountReceivableManagement.dependency.billing_data_acquisition;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMasterDataServiceImplTest {

    private static final Long PROJECT_ID = 23L;

    @Mock
    private ProjectMasterReferenceRepository projectMasterReferenceRepository;

    @InjectMocks
    private ProjectMasterDataServiceImpl projectMasterDataService;

    @Test
    void getClientIdByProjectId_projectSynced_returnsItsCdcClientId() {
        UUID clientId = UUID.randomUUID();
        when(projectMasterReferenceRepository.findBypmsProjectId(PROJECT_ID))
                .thenReturn(Optional.of(ProjectMasterReference.builder()
                        .pmsProjectId(PROJECT_ID)
                        .clientId(clientId)
                        .build()));

        assertThat(projectMasterDataService.getClientIdByProjectId(PROJECT_ID))
                .isEqualTo(clientId);
    }

    @Test
    void getClientIdByProjectId_projectNotSynced_throwsInsteadOfPlaceholder() {
        when(projectMasterReferenceRepository.findBypmsProjectId(PROJECT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMasterDataService.getClientIdByProjectId(PROJECT_ID))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class)
                .hasMessage("Client could not be resolved for project 23.");
    }

    @Test
    void getClientIdByProjectId_projectHasNoClient_throws() {
        when(projectMasterReferenceRepository.findBypmsProjectId(PROJECT_ID))
                .thenReturn(Optional.of(ProjectMasterReference.builder()
                        .pmsProjectId(PROJECT_ID)
                        .build()));

        assertThatThrownBy(() -> projectMasterDataService.getClientIdByProjectId(PROJECT_ID))
                .isInstanceOf(GlobalExceptionHandler.ResourceNotFoundException.class);
    }
}
