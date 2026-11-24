package com.AccountReceivableManagement.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three access annotations, exercised through real Spring method security.
 *
 * <p>Their rule lives in a SpEL expression, which is not checked by the
 * compiler: a typo or an unresolvable {@code T(...)} reference fails only when
 * a request is made. Since some 200 endpoints will carry these annotations,
 * each is proved here to admit exactly the intended role and refuse the other.
 *
 * <p>The crossing matters as much as the granting: the roles are not a
 * hierarchy, so an executive must be refused by {@link ManagerOnly} and a
 * manager by {@link ExecutiveOnly}.
 */
@SpringJUnitConfig(ArAccessAnnotationsTest.Config.class)
class ArAccessAnnotationsTest {

    @Autowired private Guarded guarded;

    // --- @ExecutiveOnly ---------------------------------------------------

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_EXECUTIVE)
    void executiveOnly_admitsExecutive() {
        assertThatCode(guarded::executiveOnly).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_MANAGER)
    void executiveOnly_refusesManager() {
        assertThatThrownBy(guarded::executiveOnly).isInstanceOf(AccessDeniedException.class);
    }

    // --- @ManagerOnly -----------------------------------------------------

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_MANAGER)
    void managerOnly_admitsManager() {
        assertThatCode(guarded::managerOnly).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_EXECUTIVE)
    void managerOnly_refusesExecutive() {
        // Not a hierarchy: the executive does not inherit the manager's rights.
        assertThatThrownBy(guarded::managerOnly).isInstanceOf(AccessDeniedException.class);
    }

    // --- @AnyFinanceRole --------------------------------------------------

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_EXECUTIVE)
    void anyFinanceRole_admitsExecutive() {
        assertThatCode(guarded::anyFinanceRole).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_MANAGER)
    void anyFinanceRole_admitsManager() {
        assertThatCode(guarded::anyFinanceRole).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "SOME_OTHER_TEAM")
    void anyFinanceRole_refusesAnAuthenticatedNonFinanceUser() {
        // "Either finance role" is not "any valid token" - the gap this RBAC work closes.
        assertThatThrownBy(guarded::anyFinanceRole).isInstanceOf(AccessDeniedException.class);
    }

    // --- unauthenticated --------------------------------------------------

    @Test
    @WithAnonymousUser
    void everyAnnotationRefusesAnAnonymousCaller() {
        assertThatThrownBy(guarded::executiveOnly).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(guarded::managerOnly).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(guarded::anyFinanceRole).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void everyAnnotationRefusesWhenThereIsNoAuthenticationAtAll() {
        assertThatThrownBy(guarded::anyFinanceRole)
                .isInstanceOfAny(AccessDeniedException.class,
                        AuthenticationCredentialsNotFoundException.class);
    }

    // --- the authority the annotations compare against ---------------------

    @Test
    @WithMockUser(roles = ArRoles.FINANCE_EXECUTIVE)
    void theRoleConstantsCarryNoPrefix_soHasAnyRoleDoesNotDoubleIt() {
        // hasAnyRole prepends ROLE_ itself; a constant already carrying it
        // would be matched as ROLE_ROLE_* and would deny everyone.
        assertThat(ArRoles.FINANCE_EXECUTIVE).doesNotStartWith(RoleNames.ROLE_PREFIX);
        assertThat(ArRoles.FINANCE_MANAGER).doesNotStartWith(RoleNames.ROLE_PREFIX);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    /** Stands in for a controller; only the annotations are under test. */
    static class Guarded {

        @ExecutiveOnly
        void executiveOnly() {
        }

        @ManagerOnly
        void managerOnly() {
        }

        @AnyFinanceRole
        void anyFinanceRole() {
        }
    }
}
