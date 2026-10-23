package com.AccountReceivableManagement.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The early (spring.factories) layer of the guard: registered, ordered after config loading, and a no-op without the profile. */
class LocalDemoIsolationHooksTest {

    @Test
    void earlyGuardIsRegisteredInSpringFactories() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader()))
                .contains(LocalDemoIsolationEnvironmentPostProcessor.class.getName());
    }

    @Test
    void earlyGuardRunsAfterConfigFilesAreLoaded() {
        assertThat(new LocalDemoIsolationEnvironmentPostProcessor().getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void earlyGuardRejectsAnUnsafeDemoEnvironmentBeforeAnyBeanExists() {
        StandardEnvironment env = LocalDemoIsolationValidatorTest.demoEnv(Map.of(
                "spring.datasource.url", "jdbc:mysql://marker-ar-db.f.aivencloud.com:12345/marker_ar_db"));

        assertThatThrownBy(() -> new LocalDemoIsolationEnvironmentPostProcessor().postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-loopback host 'marker-ar-db.f.aivencloud.com'");
    }

    @Test
    void earlyGuardPinsTmsOverAnyHigherPrioritySourceButOnlyForTheDemoProfile() {
        Map<String, Object> shadowing = new java.util.LinkedHashMap<>(LocalDemoIsolationValidatorTest.isolated());
        shadowing.put("tms.api.base-url", "https://tms.shared-dev.example.com/api"); // e.g. the OS variable TMS_API_BASE_URL

        StandardEnvironment demo = LocalDemoIsolationValidatorTest.demoEnv(shadowing);
        new LocalDemoIsolationEnvironmentPostProcessor().postProcessEnvironment(demo, null); // would throw if not pinned
        assertThat(demo.getProperty("tms.api.base-url")).isEqualTo("http://127.0.0.1:9");

        StandardEnvironment other = LocalDemoIsolationValidatorTest.env(shadowing, "local");
        new LocalDemoIsolationEnvironmentPostProcessor().postProcessEnvironment(other, null);
        assertThat(other.getProperty("tms.api.base-url")).isEqualTo("https://tms.shared-dev.example.com/api");
        assertThat(other.getPropertySources().contains(LocalDemoIsolationValidator.PINNED_SOURCE_NAME)).isFalse();
    }

    @Test
    void earlyGuardIsANoOpForEveryOtherProfile() {
        Map<String, Object> unsafe = Map.of(
                "spring.datasource.url", "jdbc:mysql://marker-ar-db.f.aivencloud.com:12345/marker_ar_db",
                "client-cdc.enabled", "true");

        for (String[] profiles : new String[][]{{}, {"local"}, {"dev"}, {"prod"}, {"default"}}) {
            StandardEnvironment env = LocalDemoIsolationValidatorTest.env(unsafe, profiles);
            assertThatCode(() -> new LocalDemoIsolationEnvironmentPostProcessor().postProcessEnvironment(env, null))
                    .doesNotThrowAnyException();
        }
    }
}
