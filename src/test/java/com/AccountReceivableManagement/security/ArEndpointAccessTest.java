package com.AccountReceivableManagement.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The access rules of {@code RBAC_ENDPOINT_MATRIX.md}, exercised through the
 * real filter chain on representative endpoints of each kind.
 *
 * <p>{@link ArAccessAnnotationsTest} proves the three annotations behave
 * correctly in isolation. This proves they are actually <em>on</em> the
 * endpoints, and reach a caller through the filter chain - an annotation on
 * the wrong method, or a controller that was missed, is invisible to a unit
 * test.
 *
 * <p>Assertions are about 401/403 only, never 200: whether a request then
 * succeeds depends on fixtures this test deliberately does not create. "Not
 * refused" is the whole claim - an authorized caller must get past the guard,
 * whatever the handler then makes of the request.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rbac_access;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "client-cdc.enabled=false", "project-cdc.enabled=false", "ar.automatic-billing.enabled=false",
        "ar.receivable-reconciliation.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer",
        "tms.api.base-url=http://localhost/tms"
})
@AutoConfigureMockMvc
class ArEndpointAccessTest {

    /** Executive work: the invoice list (RBAC_ENDPOINT_MATRIX.md §6.8). */
    private static final String EXECUTIVE_ONLY = "/api/v1/invoices";

    /** Manager work: the approval queue (§5.5, decision D3). */
    private static final String MANAGER_ONLY = "/api/v1/invoices/pending-approval";

    /** Shared: the AR dashboard (§7.1, decision D7). */
    private static final String BOTH_ROLES = "/api/v1/dashboard/summary";

    @Autowired private MockMvc mockMvc;

    // Never called: jwt() authenticates directly, without decoding a token.
    @MockBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor executive() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_EXECUTIVE"));
    }

    private static RequestPostProcessor manager() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER"));
    }

    private static RequestPostProcessor noFinanceRole() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SALES"));
    }

    private int status(String path, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(who == null ? get(path) : get(path).with(who))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("an executive endpoint admits the executive and refuses the manager")
    void executiveOnlyEndpoint() throws Exception {
        assertThat(status(EXECUTIVE_ONLY, executive())).as("executive").isNotIn(401, 403);
        assertThat(status(EXECUTIVE_ONLY, manager())).as("manager").isEqualTo(403);
    }

    @Test
    @DisplayName("a manager endpoint admits the manager and refuses the executive")
    void managerOnlyEndpoint() throws Exception {
        assertThat(status(MANAGER_ONLY, manager())).as("manager").isNotIn(401, 403);
        // Not a hierarchy, and decision D3: the executive does not see the approval queue.
        assertThat(status(MANAGER_ONLY, executive())).as("executive").isEqualTo(403);
    }

    @Test
    @DisplayName("a shared endpoint admits both roles")
    void sharedEndpoint() throws Exception {
        assertThat(status(BOTH_ROLES, executive())).as("executive").isNotIn(401, 403);
        assertThat(status(BOTH_ROLES, manager())).as("manager").isNotIn(401, 403);
    }

    @Test
    @DisplayName("a valid token without a finance role is refused everywhere")
    void authenticatedButNotFinance() throws Exception {
        // The gap this work closes: before RBAC, any valid UMS token reached all of these.
        assertThat(status(EXECUTIVE_ONLY, noFinanceRole())).as(EXECUTIVE_ONLY).isEqualTo(403);
        assertThat(status(MANAGER_ONLY, noFinanceRole())).as(MANAGER_ONLY).isEqualTo(403);
        assertThat(status(BOTH_ROLES, noFinanceRole())).as(BOTH_ROLES).isEqualTo(403);
    }

    @Test
    @DisplayName("no token is 401, not 403 - the two failures stay distinct")
    void unauthenticatedIsUnauthorized() throws Exception {
        assertThat(status(EXECUTIVE_ONLY, null)).isEqualTo(401);
        assertThat(status(MANAGER_ONLY, null)).isEqualTo(401);
        assertThat(status(BOTH_ROLES, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("the health probe stays open - it is the one PUBLIC row in the matrix")
    void healthIsPublic() throws Exception {
        assertThat(status("/health", null)).isNotIn(401, 403);
    }
}
