package com.AccountReceivableManagement.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs a REAL {@link org.springframework.boot.SpringApplication} so the guard is exercised through Spring Boot's own
 * discovery ({@code META-INF/spring.factories}) rather than by calling it directly. The context is minimal - only the demo
 * configuration classes - so there is no web server, no DataSource, no Debezium and no network; the "operating system
 * environment" is simulated and full of shared-looking markers.
 */
class LocalDemoSpringApplicationStartTest {

    @Configuration(proxyBeanMethods = false)
    static class Minimal {
    }

    private static StandardEnvironment environment(Map<String, Object> osEnvironment) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, osEnvironment));
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return env;
    }

    private static ConfigurableApplicationContext start(Map<String, Object> os, String... profiles) {
        return new SpringApplicationBuilder(Minimal.class, LocalDemoIsolationConfig.class, DemoJwtConfig.class, DemoMailConfig.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .environment(environment(os))
                .profiles(profiles)
                .run();
    }

    @Test
    void demoProfileStartsThroughSpringBootEvenWithSharedVariablesPresent() {
        try (ConfigurableApplicationContext context = start(LocalDemoProfileResolutionTest.osEnvironment(), "local-demo")) {
            // the early guard (spring.factories) pinned the property that an OS variable would otherwise have overridden
            assertThat(context.getEnvironment().getProperty("tms.api.base-url")).isEqualTo("http://127.0.0.1:9");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).contains("127.0.0.1");
            assertThat(context.containsBean("localDemoIsolationGuard")).isTrue();
            assertThat(context.getBeansOfType(JwtDecoder.class)).containsOnlyKeys("demoJwtDecoder");
            assertThat(context.getBean(JavaMailSender.class)).isInstanceOf(LogOnlyMailSender.class);
        }
    }

    @Test
    void springBootRefusesToStartWhenAnOsVariableTargetsSharedInfrastructure() {
        Map<String, Object> os = LocalDemoProfileResolutionTest.osEnvironment();
        os.put("SPRING_DATASOURCE_URL", "jdbc:mysql://" + LocalDemoProfileResolutionTest.SHARED_AR_DB_HOST + ":12345/marker_ar_db");

        assertThatThrownBy(() -> start(os, "local-demo"))
                .hasStackTraceContaining("local-demo isolation guard refused to start")
                .hasStackTraceContaining("non-loopback host '" + LocalDemoProfileResolutionTest.SHARED_AR_DB_HOST + "'");
    }

    @Test
    void springBootRefusesToStartWhenAGatedFlagIsReEnabledFromTheEnvironment() {
        Map<String, Object> os = LocalDemoProfileResolutionTest.osEnvironment();
        os.put("PROJECT_CDC_MILESTONE_INITIAL_SYNC_ENABLED", "true");

        assertThatThrownBy(() -> start(os, "local-demo"))
                .hasStackTraceContaining("project-cdc.milestone-initial-sync.enabled must be explicitly false");
    }

    @Test
    void springBootRefusesToStartWithoutTheDemoVariables() {
        Map<String, Object> os = LocalDemoProfileResolutionTest.osEnvironment();
        os.remove("AR_DEMO_DB_PASSWORD");
        os.remove("AR_DEMO_JWT_SECRET");

        assertThatThrownBy(() -> start(os, "local-demo"))
                .hasStackTraceContaining("spring.datasource.password cannot be resolved")
                .hasStackTraceContaining("ar.demo.jwt.secret (AR_DEMO_JWT_SECRET) must be set");
    }

    @Test
    void withoutTheDemoProfileNothingIsPinnedGuardedOrCreated() {
        try (ConfigurableApplicationContext context = start(LocalDemoProfileResolutionTest.osEnvironment())) {
            assertThat(context.getEnvironment().getProperty("tms.api.base-url"))
                    .isEqualTo(LocalDemoProfileResolutionTest.SHARED_TMS_URL);
            assertThat(context.containsBean("localDemoIsolationGuard")).isFalse();
            assertThat(context.getBeansOfType(JwtDecoder.class)).isEmpty();
            assertThat(context.getBeansOfType(JavaMailSender.class)).isEmpty();
        }
    }
}
