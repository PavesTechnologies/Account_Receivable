package com.AccountReceivableManagement.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G-25 - effective security configuration through the real filter chain:
 * CORS only for configured origins, invalid tokens rejected, API docs off by
 * default, recovery endpoints closed to callers without OPERATIONS_RECOVERY
 * (the default authority list is empty = nobody).
 */
class SecurityConfigurationTest {

    private static final String[] COMMON = {
            "spring.datasource.url=jdbc:h2:mem:security_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "client-cdc.enabled=false", "project-cdc.enabled=false", "ar.automatic-billing.enabled=false",
            "ar.receivable-reconciliation.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer",
            "tms.api.base-url=http://localhost/tms"
    };

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:security_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "client-cdc.enabled=false", "project-cdc.enabled=false", "ar.automatic-billing.enabled=false",
            "ar.receivable-reconciliation.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer",
            "tms.api.base-url=http://localhost/tms",
            "ar.cors.allowed-origins=https://ar.example.com"
    })
    @AutoConfigureMockMvc
    class WithConfiguredOrigin {

        @Autowired private MockMvc mockMvc;
        @MockBean private JwtDecoder jwtDecoder;

        @Test
        void preflightFromTrustedOrigin_isAllowed() throws Exception {
            mockMvc.perform(options("/api/v1/invoices")
                            .header("Origin", "https://ar.example.com")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://ar.example.com"))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        @Test
        void preflightFromAnyOtherOrigin_includingLocalhost_isRejected() throws Exception {
            for (String origin : new String[]{"http://localhost:5173", "https://evil.example.com"}) {
                mockMvc.perform(options("/api/v1/invoices")
                                .header("Origin", origin)
                                .header("Access-Control-Request-Method", "POST"))
                        .andExpect(status().isForbidden())
                        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            }
        }

        @Test
        void invalidToken_isRejected_401() throws Exception {
            when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("expired"));
            mockMvc.perform(get("/api/v1/invoices").header("Authorization", "Bearer not-a-valid-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void noToken_isRejected_401() throws Exception {
            mockMvc.perform(get("/api/v1/invoices")).andExpect(status().isUnauthorized());
        }

        @Test
        void apiDocs_areDisabledByDefault() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        }

        @Test
        void recoveryEndpoint_withoutRecoveryAuthority_isForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/recovery/automatic-billing-runs/" + java.util.UUID.randomUUID()
                                    + "/requeue")
                            .with(jwt().jwt(token -> token.claim("user_id", 5L).claim("roles", java.util.List.of("FINANCE"))))
                            .contentType("application/json")
                            .content("{\"reason\":\"retry\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void wildcardOrigin_isRefusedAtStartup() {
        assertThatThrownBy(() -> com.AccountReceivableManagement.config.CorsConfigTestAccess.trustedOrigins("*"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> com.AccountReceivableManagement.config.CorsConfigTestAccess
                .trustedOrigins("https://*.example.com"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> com.AccountReceivableManagement.config.CorsConfigTestAccess
                .trustedOrigins("ar.example.com"))
                .isInstanceOf(IllegalStateException.class);
    }
}
