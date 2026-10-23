package com.AccountReceivableManagement.demo;

import com.AccountReceivableManagement.cdc.config.ClientDebeziumConfig;
import com.AccountReceivableManagement.cdc.config.DebeziumJdbcTableInitializer;
import com.AccountReceivableManagement.cdc.config.ProjectDebeziumConfig;
import com.AccountReceivableManagement.cdc.config.properties.ClientCdcProperties;
import com.AccountReceivableManagement.cdc.config.properties.ProjectCdcProperties;
import com.AccountReceivableManagement.cdc.processor.InboxEventProcessor;
import com.AccountReceivableManagement.cdc.runner.ClientCdcConfig;
import com.AccountReceivableManagement.cdc.runner.ClientCdcRunner;
import com.AccountReceivableManagement.cdc.runner.MilestoneInitialSyncRunner;
import com.AccountReceivableManagement.cdc.runner.ProjectCdcConfig;
import com.AccountReceivableManagement.cdc.service.CdcInboxService;
import com.AccountReceivableManagement.cdc.service.ClientDataProcessor;
import com.AccountReceivableManagement.cdc.service.MilestoneDataProcessor;
import com.AccountReceivableManagement.cdc.service.ProjectDataProcessor;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingConfigurationStatusScheduler;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceServiceImpl;
import com.AccountReceivableManagement.service_Imple.projectbilling_config.BillingOccurrenceStatusScheduler;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Bean creation and absence. Contexts are created by the runners only: no database, no network, no Debezium engine is
 * started, and JdbcTemplate / processors are mocks wherever a component is expected to exist.
 */
class LocalDemoBeanGatingTest {

    private static final String SECRET = LocalDemoIsolationValidatorTest.SECRET;

    /** Every component the demo profile must keep out of the context. */
    private static final Class<?>[] GATED = {
            InboxEventProcessor.class,
            BillingConfigurationStatusScheduler.class,
            BillingOccurrenceStatusScheduler.class,
            DebeziumJdbcTableInitializer.class,
            ClientDebeziumConfig.class,
            ProjectDebeziumConfig.class,
            ClientCdcConfig.class,
            ProjectCdcConfig.class,
            ClientCdcRunner.class,
            MilestoneInitialSyncRunner.class
    };

    private static final Class<?>[] DEMO_ONLY = {
            DemoJwtConfig.class, DemoMailConfig.class, LocalDemoIsolationConfig.class
    };

    private static Class<?>[] concat(Class<?>[] a, Class<?>[] b) {
        Class<?>[] all = new Class<?>[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    /** The real application.properties + application-local-demo.properties, hermetic (no real OS environment). */
    private WebApplicationContextRunner demoRunner() {
        return new WebApplicationContextRunner()
                .withInitializer(ctx -> {
                    ctx.getEnvironment().getPropertySources().remove("systemEnvironment");
                    ctx.getEnvironment().getPropertySources().remove("systemProperties");
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local-demo",
                        "AR_DEMO_DB_PASSWORD=not-a-real-password",
                        "AR_DEMO_JWT_SECRET=" + SECRET)
                .withConfiguration(AutoConfigurations.of(OAuth2ResourceServerAutoConfiguration.class,
                        MailSenderAutoConfiguration.class))
                // The real app defines its own SecurityFilterChain (SecurityConfig), which makes Boot's default JWT
                // chain back off; a stand-in reproduces that here without pulling in the whole security stack.
                .withBean(SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
                .withUserConfiguration(concat(GATED, DEMO_ONLY));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // local-demo: everything shared is absent, demo-only beans are present
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void demoProfileCreatesNoCdcEngineRunnerSyncInboxOrScheduler() {
        // No mocks are registered on purpose: if any gated component were created it would fail on its missing
        // dependencies (repositories, JdbcTemplate, Debezium engine, properties) and the context would not start.
        demoRunner().run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> gated : GATED) {
                assertThat(context).as(gated.getSimpleName()).doesNotHaveBean(gated);
            }
            assertThat(context).doesNotHaveBean(ClientCdcProperties.class);
            assertThat(context).doesNotHaveBean(ProjectCdcProperties.class);
            assertThat(context).doesNotHaveBean(DebeziumEngine.class);
            assertThat(context).doesNotHaveBean(ClientCdcConfig.DebeziumEngineRunner.class);
            assertThat(context).doesNotHaveBean(ProjectCdcConfig.DebeziumEngineRunner.class);
            assertThat(context).doesNotHaveBean("clientDebeziumConfiguration");
            assertThat(context).doesNotHaveBean("projectDebeziumConfiguration");
        });
    }

    @Test
    void demoProfileCreatesTheGuardTheHmacDecoderAndTheLogOnlyMailSender() {
        demoRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("localDemoIsolationGuard");
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context.getBean(JwtDecoder.class).getClass().getSimpleName()).isEqualTo("NimbusJwtDecoder");
            assertThat(context).hasSingleBean(JavaMailSender.class);
            assertThat(context.getBean(JavaMailSender.class)).isInstanceOf(LogOnlyMailSender.class);
        });
    }

    @Test
    void blankIssuerUriDoesNotMakeBootBuildAnIssuerBasedDecoder() {
        // Boot would otherwise try issuer discovery (network); the demo decoder must be the only JwtDecoder.
        demoRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(JwtDecoder.class)).containsOnlyKeys("demoJwtDecoder");
        });
    }

    @Test
    void demoProfileRefusesToStartWithoutADatabasePassword() {
        new WebApplicationContextRunner()
                .withInitializer(ctx -> {
                    ctx.getEnvironment().getPropertySources().remove("systemEnvironment");
                    ctx.getEnvironment().getPropertySources().remove("systemProperties");
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local-demo", "AR_DEMO_JWT_SECRET=" + SECRET)
                .withUserConfiguration(DEMO_ONLY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("spring.datasource.password cannot be resolved");
                });
    }

    @Test
    void demoProfileRefusesToStartWhenADatasourceOverrideTargetsSharedInfrastructure() {
        demoRunner()
                .withPropertyValues("spring.datasource.url=jdbc:mysql://marker-ar-db.f.aivencloud.com:12345/marker_ar_db")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("non-loopback host 'marker-ar-db.f.aivencloud.com'");
                });
    }

    @Test
    void demoProfileRefusesToStartWhenAGatedFlagIsTurnedBackOn() {
        for (String flag : LocalDemoIsolationValidator.MUST_BE_FALSE) {
            demoRunner().withPropertyValues(flag + "=true").run(context -> {
                assertThat(context).as(flag).hasFailed();
                assertThat(context.getStartupFailure()).as(flag).hasStackTraceContaining(flag + " must be explicitly false");
            });
        }
    }

    @Test
    void demoProfileRefusesToStartTogetherWithAnEnvironmentProfile() {
        demoRunner().withInitializer(ctx -> ctx.getEnvironment().addActiveProfile("prod")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("profile 'prod' must not be active");
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Demo-only beans cannot activate outside local-demo
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void demoOnlyBeansDoNotExistInAnyOtherProfileEvenWhenTheDemoSecretIsSet() {
        for (String profile : List.of("", "default", "local", "dev", "prod", "test", "demo", "local-demo-2", "local_demo")) {
            WebApplicationContextRunner runner = new WebApplicationContextRunner()
                    .withUserConfiguration(DEMO_ONLY)
                    .withPropertyValues("ar.demo.jwt.secret=" + SECRET);
            if (!profile.isEmpty()) {
                runner = runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile));
            }
            runner.run(context -> {
                assertThat(context).as("profile '" + profile + "'").hasNotFailed();
                assertThat(context).as(profile).doesNotHaveBean(JwtDecoder.class);
                assertThat(context).as(profile).doesNotHaveBean(JavaMailSender.class);
                assertThat(context).as(profile).doesNotHaveBean(LogOnlyMailSender.class);
                assertThat(context).as(profile).doesNotHaveBean("localDemoIsolationGuard");
            });
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Default behaviour preserved: with no demo property every gated component is still created
    // ---------------------------------------------------------------------------------------------------------------

    private ApplicationContextRunner defaultRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(InboxEventProcessor.class, BillingConfigurationStatusScheduler.class,
                        BillingOccurrenceStatusScheduler.class, DebeziumJdbcTableInitializer.class,
                        ClientDebeziumConfig.class, ProjectDebeziumConfig.class, ClientCdcRunner.class)
                .withBean(CdcInboxService.class, () -> mock(CdcInboxService.class))
                .withBean(ClientDataProcessor.class, () -> mock(ClientDataProcessor.class))
                .withBean(ProjectDataProcessor.class, () -> mock(ProjectDataProcessor.class))
                .withBean(MilestoneDataProcessor.class, () -> mock(MilestoneDataProcessor.class))
                .withBean(BillingConfigurationRepository.class, () -> mock(BillingConfigurationRepository.class))
                .withBean(BillingOccurrenceServiceImpl.class, () -> mock(BillingOccurrenceServiceImpl.class))
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(DebeziumEngine.class, () -> mock(DebeziumEngine.class))
                .withPropertyValues(
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:3399/gating_test",
                        "spring.datasource.username=gating-test", "spring.datasource.password=gating-test",
                        "client-cdc.hostname=cdc-source.test.invalid", "client-cdc.port=3306",
                        "client-cdc.database-name=rms", "client-cdc.username=u", "client-cdc.password=p",
                        "client-cdc.table-include-list=rms.client",
                        "project-cdc.hostname=cdc-source.test.invalid", "project-cdc.port=3306",
                        "project-cdc.database-name=pms", "project-cdc.username=u", "project-cdc.password=p",
                        "project-cdc.table-include-list=pms.projects",
                        "project-cdc.offset-table=debezium_project_offsets",
                        "project-cdc.schema-history-table=debezium_project_schema_history",
                        "project-cdc.topic-prefix=project-cdc", "project-cdc.server-id=1",
                        "project-cdc.connector-name=PMS",
                        "project-cdc.connector-class=io.debezium.connector.mysql.MySqlConnector");
    }

    @Test
    void withoutDemoPropertiesEveryGatedComponentIsStillCreated() {
        defaultRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InboxEventProcessor.class);
            assertThat(context).hasSingleBean(BillingConfigurationStatusScheduler.class);
            assertThat(context).hasSingleBean(BillingOccurrenceStatusScheduler.class);
            assertThat(context).hasSingleBean(DebeziumJdbcTableInitializer.class);
            assertThat(context).hasSingleBean(ClientDebeziumConfig.class);
            assertThat(context).hasSingleBean(ProjectDebeziumConfig.class);
            assertThat(context).hasSingleBean(ClientCdcRunner.class);
            assertThat(context).hasSingleBean(ClientCdcProperties.class);
            assertThat(context).hasSingleBean(ProjectCdcProperties.class);
        });
    }

    @Test
    void engineFlagsAloneStillDoNotRemoveTheDebeziumConfigurationsAsBefore() {
        // Before this change the Debezium configurations and the table initializer existed regardless of the engine flags.
        defaultRunner().withPropertyValues("client-cdc.enabled=false", "project-cdc.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DebeziumJdbcTableInitializer.class);
            assertThat(context).hasSingleBean(ClientDebeziumConfig.class);
            assertThat(context).hasSingleBean(ProjectDebeziumConfig.class);
        });
    }

    @Test
    void eachNewFlagRemovesExactlyItsOwnComponents() {
        defaultRunner().withPropertyValues("ar.cdc.inbox-processor.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(InboxEventProcessor.class);
            assertThat(context).hasSingleBean(BillingConfigurationStatusScheduler.class);
            assertThat(context).hasSingleBean(DebeziumJdbcTableInitializer.class);
        });
        defaultRunner().withPropertyValues("ar.billing.status-schedulers.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(BillingConfigurationStatusScheduler.class);
            assertThat(context).doesNotHaveBean(BillingOccurrenceStatusScheduler.class);
            assertThat(context).hasSingleBean(InboxEventProcessor.class);
            assertThat(context).hasSingleBean(DebeziumJdbcTableInitializer.class);
        });
        defaultRunner().withPropertyValues("ar.cdc.infrastructure.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DebeziumJdbcTableInitializer.class);
            assertThat(context).doesNotHaveBean(ClientDebeziumConfig.class);
            assertThat(context).doesNotHaveBean(ProjectDebeziumConfig.class);
            assertThat(context).doesNotHaveBean(ClientCdcRunner.class);
            assertThat(context).hasSingleBean(InboxEventProcessor.class);
            assertThat(context).hasSingleBean(BillingConfigurationStatusScheduler.class);
        });
    }

    @Test
    void milestoneSyncKeepsItsOwnTwoFlagGate() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MilestoneInitialSyncRunner.class)
                .withBean(ProjectCdcProperties.class, ProjectCdcProperties::new)
                .withBean(MilestoneDataProcessor.class, () -> mock(MilestoneDataProcessor.class));

        runner.withPropertyValues("project-cdc.enabled=true", "project-cdc.milestone-initial-sync.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MilestoneInitialSyncRunner.class));
        runner.withPropertyValues("project-cdc.enabled=true", "project-cdc.milestone-initial-sync.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MilestoneInitialSyncRunner.class));
        runner.withPropertyValues("project-cdc.enabled=false", "project-cdc.milestone-initial-sync.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(MilestoneInitialSyncRunner.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(MilestoneInitialSyncRunner.class));
    }

    @Test
    void debeziumEnginesKeepTheirExistingFlagGate() {
        // Engines are gated by the pre-existing client-cdc.enabled / project-cdc.enabled; only the absence is asserted
        // because creating them would start a Debezium engine on refresh.
        new ApplicationContextRunner()
                .withUserConfiguration(ClientCdcConfig.class, ProjectCdcConfig.class)
                .withPropertyValues("client-cdc.enabled=false", "project-cdc.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DebeziumEngine.class);
                });
        new ApplicationContextRunner()
                .withUserConfiguration(ClientCdcConfig.class, ProjectCdcConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed(); // absent flag = disabled, unchanged
                    assertThat(context).doesNotHaveBean(DebeziumEngine.class);
                });
    }
}
