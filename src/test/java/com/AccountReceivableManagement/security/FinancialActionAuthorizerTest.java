package com.AccountReceivableManagement.security;

import com.AccountReceivableManagement.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialActionAuthorizerTest {

    @Mock
    private CurrentUserService currentUserService;

    private FinancialSecurityProperties properties;
    private FinancialActionAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        properties = new FinancialSecurityProperties();
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of("ROLE_FINANCE_MANAGER", "CONFIG_APPROVE"));
        properties.getAuthorization().setInvoiceApprovalAuthorities(List.of("ROLE_FINANCE_MANAGER", "INVOICE_APPROVE"));
        properties.getAuthorization().setInvoiceDeliveryAuthorities(List.of("ROLE_FINANCE_USER", "INVOICE_SEND"));
        properties.getAuthorization().setMakerChecker(true);

        authorizer = new FinancialActionAuthorizer(currentUserService, properties);
    }

    @Test
    void requirePermission_withAuthorizedRole_succeedsAndReturnsActor() {
        UserDTO user = new UserDTO(10L, "manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(user);

        Actor actor = authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL);

        assertThat(actor.userId()).isEqualTo(10L);
        assertThat(actor.label()).isEqualTo("manager@example.com");
        assertThat(actor.isSystem()).isFalse();
    }

    @Test
    void requirePermission_withAuthorizedPermission_succeedsAndReturnsActor() {
        UserDTO user = new UserDTO(11L, null, "Finance Approver", List.of(), List.of("INVOICE_APPROVE"));
        when(currentUserService.getCurrentUser()).thenReturn(user);

        Actor actor = authorizer.requirePermission(FinancialAction.INVOICE_APPROVAL);

        assertThat(actor.userId()).isEqualTo(11L);
        assertThat(actor.label()).isEqualTo("Finance Approver");
    }

    @Test
    void requirePermission_withUnauthorizedRole_throwsAccessDeniedException() {
        UserDTO user = new UserDTO(12L, "exec@example.com", "Finance Executive", List.of("FINANCE_EXECUTIVE"), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to approve or reject billing configurations.");
    }

    @Test
    void requirePermission_configApprovalByPermissionClaim_succeeds() {
        UserDTO user = new UserDTO(13L, "approver@example.com", "Approver", List.of("EMPLOYEE"), List.of("CONFIG_APPROVE"));
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThat(authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL).userId()).isEqualTo(13L);
    }

    @Test
    void requirePermission_noAuthorityConfigured_deniesEvenAFinanceManager() {
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of());
        UserDTO user = new UserDTO(10L, "manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of("CONFIG_APPROVE"));
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not authorized to approve or reject billing configurations.");
    }

    @Test
    void requirePermission_blankConfiguredAuthority_grantsNobody() {
        // An env value like "AR_AUTH_CONFIGURATION_APPROVAL= " must not match anything.
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of(" ", ""));
        UserDTO user = new UserDTO(10L, "manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of(""));
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePermission_tokenWithoutRoleOrPermissionClaims_isDenied() {
        UserDTO user = new UserDTO(14L, "someone@example.com", "Someone", null, null);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePermission_roleConfiguredWithoutRolePrefix_doesNotMatchTheRolesClaim() {
        // Roles from the "roles" claim are compared as ROLE_<role>; a bare role name in the config
        // only matches a permission of that exact name.
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of("FINANCE_MANAGER"));
        UserDTO user = new UserDTO(10L, "manager@example.com", "Finance Manager", List.of("FINANCE_MANAGER"), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .isInstanceOf(AccessDeniedException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "FINANCE_MANAGER", "ROLE_FINANCE_MANAGER", "Finance Manager", "finance_manager", "Finance-Manager", " FINANCE_MANAGER "})
    void requirePermission_financeManagerRoleInAnyUmsSpelling_canApproveConfigurations(String umsRole) {
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of("ROLE_FINANCE_MANAGER"));
        UserDTO bindu = new UserDTO(30L, "bindu@example.com", "Bindu", List.of(umsRole), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(bindu);

        assertThat(authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL).userId()).isEqualTo(30L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "FINANCE_EXECUTIVE", "Finance Executive", "FINANCE_MANAGER_ASSISTANT", "MANAGER", "FINANCE"})
    void requirePermission_otherRoles_areStillDenied(String umsRole) {
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of("ROLE_FINANCE_MANAGER"));
        UserDTO user = new UserDTO(31L, "someone@example.com", "Someone", List.of(umsRole), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePermission_financeManagerApprovalRole_doesNotGrantDeliveryOrRecovery() {
        properties.getAuthorization().setConfigurationApprovalAuthorities(List.of("ROLE_FINANCE_MANAGER"));
        properties.getAuthorization().setInvoiceDeliveryAuthorities(List.of());
        properties.getAuthorization().setOperationsRecoveryAuthorities(List.of());
        UserDTO bindu = new UserDTO(30L, "bindu@example.com", "Bindu", List.of("FINANCE_MANAGER"), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(bindu);

        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.INVOICE_DELIVERY))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.OPERATIONS_RECOVERY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePermission_systemActor_throwsAccessDeniedException() {
        ActorContext.callAsSystem(() -> {
            assertThatThrownBy(() -> authorizer.requirePermission(FinancialAction.BILLING_CONFIGURATION_APPROVAL))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Automatic billing cannot approve or reject billing configurations.");
            return null;
        });
    }

    @Test
    void requireDifferentFromMaker_sameUser_throwsAccessDeniedException() {
        Actor approver = new Actor(10L, "manager@example.com");

        assertThatThrownBy(() -> authorizer.requireDifferentFromMaker(10L, approver, "billing configuration"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You submitted this billing configuration for approval, so it must be approved by another authorized user.");
    }

    @Test
    void requireDifferentFromMaker_differentUser_succeeds() {
        Actor approver = new Actor(10L, "manager@example.com");

        // Submitter is user 7, approver is user 10 -> succeeds
        authorizer.requireDifferentFromMaker(7L, approver, "billing configuration");
    }

    @Test
    void requireDifferentFromMaker_nullMaker_succeeds() {
        Actor approver = new Actor(10L, "manager@example.com");

        // System submission has null maker -> succeeds
        authorizer.requireDifferentFromMaker(null, approver, "invoice");
    }

    @Test
    void requireDifferentFromMaker_makerCheckerDisabled_succeedsEvenIfSameUser() {
        properties.getAuthorization().setMakerChecker(false);
        Actor approver = new Actor(10L, "manager@example.com");

        authorizer.requireDifferentFromMaker(10L, approver, "billing configuration");
    }

    @Test
    void currentActor_systemContext_returnsSystem() {
        ActorContext.callAsSystem(() -> {
            Actor actor = authorizer.currentActor();
            assertThat(actor).isEqualTo(Actor.SYSTEM);
            assertThat(actor.isSystem()).isTrue();
            return null;
        });
    }

    @Test
    void currentActor_userContext_returnsUserActor() {
        UserDTO user = new UserDTO(15L, "user@example.com", "Test User", List.of(), List.of());
        when(currentUserService.getCurrentUser()).thenReturn(user);

        Actor actor = authorizer.currentActor();
        assertThat(actor.userId()).isEqualTo(15L);
        assertThat(actor.label()).isEqualTo("user@example.com");
    }

    @Test
    void currentActor_noAuthenticatedUser_returnsSystem() {
        when(currentUserService.getCurrentUser()).thenThrow(new IllegalStateException("No authenticated user"));

        Actor actor = authorizer.currentActor();
        assertThat(actor).isEqualTo(Actor.SYSTEM);
    }
}
