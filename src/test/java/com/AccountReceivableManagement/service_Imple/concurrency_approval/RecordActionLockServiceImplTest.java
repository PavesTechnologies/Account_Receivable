package com.AccountReceivableManagement.service_Imple.concurrency_approval;

import com.AccountReceivableManagement.dto.UserDTO;
import com.AccountReceivableManagement.dto.concurrency_approval.RecordLockResponse;
import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import com.AccountReceivableManagement.repo.concurrency_approval.RecordActionLockRepository;
import com.AccountReceivableManagement.security.Actor;
import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.security.FinancialAction;
import com.AccountReceivableManagement.security.FinancialActionAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The billing-configuration APPROVAL lock (POST /api/record-locks/BILLING_CONFIGURATION/{id}) is the first gate the UI hits. */
@ExtendWith(MockitoExtension.class)
class RecordActionLockServiceImplTest {

    @Mock private RecordActionLockRepository lockRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private FinancialActionAuthorizer financialActionAuthorizer;

    private RecordActionLockServiceImpl service;
    private final UUID configurationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecordActionLockServiceImpl(lockRepository, currentUserService, financialActionAuthorizer);
    }

    @Test
    void approvalLock_authorizedFinanceManager_isGrantedToThem() {
        when(financialActionAuthorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .thenReturn(new Actor(20L, "finance.manager@example.com"));
        when(currentUserService.getCurrentUser()).thenReturn(
                new UserDTO(20L, "finance.manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of()));
        when(lockRepository.findByResourceTypeAndResourceId(LockResourceType.BILLING_CONFIGURATION, configurationId))
                .thenReturn(Optional.empty());

        RecordLockResponse response = service.acquireLock(
                LockResourceType.BILLING_CONFIGURATION, configurationId, LockActionType.APPROVAL);

        assertThat(response.isLocked()).isTrue();
        assertThat(response.isCurrentUser()).isTrue();
        assertThat(response.getActionType()).isEqualTo(LockActionType.APPROVAL);
        verify(lockRepository).saveAndFlush(any());
    }

    @Test
    void approvalLock_userWithoutAuthority_isDeniedAndNoLockIsTaken() {
        when(financialActionAuthorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .thenThrow(new AccessDeniedException("You are not authorized to approve or reject billing configurations."));

        assertThatThrownBy(() -> service.acquireLock(
                LockResourceType.BILLING_CONFIGURATION, configurationId, LockActionType.APPROVAL))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not authorized to approve or reject billing configurations.");

        verifyNoInteractions(lockRepository);
    }

    @Test
    void editLock_doesNotRequireApprovalAuthority() {
        when(currentUserService.getCurrentUser()).thenReturn(
                new UserDTO(7L, "exec@example.com", "Finance Executive", List.of("FINANCE_EXECUTIVE"), List.of()));
        when(lockRepository.findByResourceTypeAndResourceId(LockResourceType.BILLING_CONFIGURATION, configurationId))
                .thenReturn(Optional.empty());

        service.acquireLock(LockResourceType.BILLING_CONFIGURATION, configurationId, LockActionType.EDIT);

        verify(financialActionAuthorizer, never()).requirePermission(any());
    }
}
