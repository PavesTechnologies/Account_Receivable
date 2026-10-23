package com.AccountReceivableManagement.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Early layer of the {@code local-demo} isolation guard: runs after the config files and profiles have been resolved and
 * before any bean (in particular the DataSource) exists. Registered in {@code META-INF/spring.factories}; it does nothing
 * unless the {@code local-demo} profile is active.
 *
 * <p>It first pins the few properties whose relaxed environment-variable name collides with a shared variable the project
 * already uses (see {@link LocalDemoIsolationValidator#PINNED_PROPERTIES}), because an OS variable such as
 * {@code TMS_API_BASE_URL} would otherwise outrank the profile file; then it validates the result.
 * {@link LocalDemoIsolationConfig} repeats the validation once the whole environment (including values added by
 * spring-dotenv) is available.
 */
public class LocalDemoIsolationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(Profiles.of(LocalDemoIsolationValidator.PROFILE))) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    LocalDemoIsolationValidator.PINNED_SOURCE_NAME, LocalDemoIsolationValidator.PINNED_PROPERTIES));
        }
        LocalDemoIsolationValidator.validateIfActive(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
