package com.AccountReceivableManagement.operations;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetIntegrationImpl;
import com.AccountReceivableManagement.service_Imple.automatic_billing.AutomaticBillingScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Operational readiness of the deployed application - full context, in-memory
 * database, CDC off: what a Kubernetes probe or an unauthenticated caller can
 * reach and is shown, which management endpoints exist, the metrics
 * operators rely on, that automatic billing is off unless enabled, and that
 * production logging never echoes SQL bind values.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:operational_readiness;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "client-cdc.enabled=false",
        "project-cdc.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationalReadinessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Environment environment;
    @Autowired private AutomaticBillingSchedulerProperties schedulerProperties;
    @Autowired private AutomaticBillingScheduler scheduler;
    @Autowired private MeterRegistry meterRegistry;

    private MvcResult unauthenticated(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn();
    }

    private MvcResult authenticated(String path) throws Exception {
        return mockMvc.perform(get(path).with(jwt())).andReturn();
    }

    // ================================================================ probes

    @Test
    void kubernetesProbes_answerWithoutAJwt_andShowOnlyTheStatus() throws Exception {
        for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            MvcResult result = unauthenticated(probe);
            assertThat(result.getResponse().getStatus()).as(probe).isEqualTo(200);
            assertThat(result.getResponse().getContentAsString()).as(probe)
                    .isEqualTo("{\"status\":\"UP\"}");
        }
        MvcResult overall = unauthenticated("/actuator/health");
        assertThat(overall.getResponse().getStatus()).isEqualTo(200);
        // Status and probe group names only - no components or details without a JWT.
        assertThat(overall.getResponse().getContentAsString())
                .isEqualTo("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}");
    }

    @Test
    void legacyHealthEndpoint_answersWithoutAJwt() throws Exception {
        MvcResult result = unauthenticated("/health");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessChecksTheDatabase_livenessOnlyTheApplication() throws Exception {
        String health = authenticated("/actuator/health").getResponse().getContentAsString();

        assertThat(health).contains("\"db\"").contains("\"livenessState\"").contains("\"readinessState\"");
        // SMTP is not a health dependency - a mail outage must not mark AR unhealthy.
        assertThat(health).doesNotContain("\"mail\"");
    }

    @Test
    void healthDetails_neverDiscloseConfiguration() throws Exception {
        String health = authenticated("/actuator/health").getResponse().getContentAsString().toLowerCase();

        assertThat(health).doesNotContain("password", "jdbc:", "secret", "token", "aiven");
    }

    // ================================================================ management endpoints

    @Test
    void onlyHealthInfoAndMetricsAreExposed() throws Exception {
        for (String hidden : new String[] {"/actuator/env", "/actuator/configprops", "/actuator/beans",
                "/actuator/loggers", "/actuator/heapdump", "/actuator/threaddump", "/actuator/mappings"}) {
            assertThat(authenticated(hidden).getResponse().getStatus()).as(hidden).isEqualTo(404);
            assertThat(unauthenticated(hidden).getResponse().getStatus()).as(hidden).isEqualTo(401);
        }
    }

    @Test
    void metrics_requireAJwt_andIncludeTheAutomaticBillingSignals() throws Exception {
        assertThat(unauthenticated("/actuator/metrics").getResponse().getStatus()).isEqualTo(401);

        String names = authenticated("/actuator/metrics").getResponse().getContentAsString();
        assertThat(names).contains("ar.automatic_billing.runs.stuck", "ar.automatic_billing.runs.needs_intervention",
                "ar.automatic_billing.runs.retry_scheduled", "ar.automatic_billing.runs.pending_approval",
                "ar.automatic_billing.occurrences.due_without_run", "ar.automatic_billing.tick");
        // Gauges read the database: an empty one reports 0, not an error.
        assertThat(meterRegistry.get("ar.automatic_billing.runs.needs_intervention").gauge().value()).isZero();
    }

    // ================================================================ scheduler activation

    @Test
    void automaticBilling_isOffUnlessExplicitlyEnabled_andManualApisStayAvailable() throws Exception {
        assertThat(schedulerProperties.isEnabled()).isFalse();
        assertThat(scheduler.runOnce().handled()).isZero();

        assertThat(authenticated("/api/v1/invoices").getResponse().getStatus()).isEqualTo(200);
        assertThat(unauthenticated("/api/v1/invoices").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void retryAndBackfill_areBoundedByDefault() {
        assertThat(schedulerProperties.getBatchSize()).isBetween(1, 500);
        assertThat(schedulerProperties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(schedulerProperties.getRetry().getMaxDelay()).isEqualTo(java.time.Duration.ofHours(6));
    }

    // ================================================================ logging and configuration

    @Test
    void productionLogging_neverEchoesSqlOrBindValues() {
        assertThat(environment.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isNull();
        assertThat(environment.getProperty("logging.level.org.hibernate.SQL")).isNull();
        assertThat(environment.getProperty("logging.level.com.AccountReceivableManagement")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.pattern.level")).contains("%X{billingRunId}");
    }

    @Test
    void verboseSqlLogging_isConfinedToTheLocalProfile() throws Exception {
        String local = new ClassPathResource("application-local.properties")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(local).contains("logging.level.org.hibernate.orm.jdbc.bind=TRACE", "spring.jpa.show-sql=true");
    }

    @Test
    void missingRequiredExternalConfiguration_failsStartupInsteadOfRunningMisconfigured() {
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class))
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(TimesheetIntegrationImpl.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("tms.api.base-url");
                });
    }

    @Test
    void unexpectedErrors_neverReturnTheirInternalMessage() {
        var response = new com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler()
                .handleException(new IllegalMonitorStateException(
                        "Communications link failure: jdbc:mysql://db.internal:3306/ar user=ar_app"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).doesNotContain("jdbc", "db.internal", "ar_app");
    }
}
