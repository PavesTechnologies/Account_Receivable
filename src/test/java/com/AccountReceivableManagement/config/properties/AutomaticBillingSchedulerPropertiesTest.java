package com.AccountReceivableManagement.config.properties;

import com.AccountReceivableManagement.config.AutomaticBillingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Binding and startup validation of ar.automatic-billing.* - only these beans, no database. */
class AutomaticBillingSchedulerPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(AutomaticBillingConfig.class);

    @Test
    void defaults_areDisabledEveryFiveMinutesBatchOf50() {
        runner.run(context -> {
            AutomaticBillingSchedulerProperties properties =
                    context.getBean(AutomaticBillingSchedulerProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getCron()).isEqualTo("0 */5 * * * *");
            assertThat(properties.getBatchSize()).isEqualTo(50);
        });
    }

    @Test
    void configuredValues_areBound() {
        runner.withPropertyValues(
                        "ar.automatic-billing.enabled=true",
                        "ar.automatic-billing.cron=0 0 1 * * *",
                        "ar.automatic-billing.batch-size=10")
                .run(context -> {
                    AutomaticBillingSchedulerProperties properties =
                            context.getBean(AutomaticBillingSchedulerProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getCron()).isEqualTo("0 0 1 * * *");
                    assertThat(properties.getBatchSize()).isEqualTo(10);
                });
    }

    @Test
    void invalidCron_failsStartupNamingTheProperty() {
        runner.withPropertyValues("ar.automatic-billing.cron=every five minutes")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("ar.automatic-billing");
                });
    }

    @Test
    void zeroBatchSize_failsStartup() {
        runner.withPropertyValues("ar.automatic-billing.batch-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("batchSize");
                });
    }

    @Test
    void oversizedBatch_failsStartup() {
        runner.withPropertyValues("ar.automatic-billing.batch-size=10000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void retryAndBackfillDefaults_areBoundedAndBackfillHasNoAgeLimit() {
        runner.run(context -> {
            AutomaticBillingSchedulerProperties properties =
                    context.getBean(AutomaticBillingSchedulerProperties.class);
            assertThat(properties.getRetry().isEnabled()).isTrue();
            assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getRetry().getInitialDelay()).isEqualTo(java.time.Duration.ofMinutes(15));
            assertThat(properties.getRetry().getMultiplier()).isEqualTo(2.0);
            assertThat(properties.getRetry().getMaxDelay()).isEqualTo(java.time.Duration.ofHours(6));
            assertThat(properties.getBackfill().isEnabled()).isTrue();
            assertThat(properties.getBackfill().getMaxAgeDays()).isZero();
        });
    }

    @Test
    void retryAndBackfillValues_areBound() {
        runner.withPropertyValues(
                        "ar.automatic-billing.retry.max-attempts=5",
                        "ar.automatic-billing.retry.initial-delay=PT5M",
                        "ar.automatic-billing.retry.multiplier=3",
                        "ar.automatic-billing.retry.max-delay=PT2H",
                        "ar.automatic-billing.backfill.enabled=false",
                        "ar.automatic-billing.backfill.max-age-days=60")
                .run(context -> {
                    AutomaticBillingSchedulerProperties properties =
                            context.getBean(AutomaticBillingSchedulerProperties.class);
                    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
                    assertThat(properties.getRetry().getInitialDelay()).isEqualTo(java.time.Duration.ofMinutes(5));
                    assertThat(properties.getRetry().getMultiplier()).isEqualTo(3.0);
                    assertThat(properties.getBackfill().isEnabled()).isFalse();
                    assertThat(properties.getBackfill().getMaxAgeDays()).isEqualTo(60);
                });
    }

    @Test
    void unboundedOrInconsistentRetry_failsStartup() {
        runner.withPropertyValues("ar.automatic-billing.retry.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ar.automatic-billing.retry.max-attempts=1000")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ar.automatic-billing.retry.initial-delay=PT12H",
                        "ar.automatic-billing.retry.max-delay=PT1H")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ar.automatic-billing.retry.multiplier=0.5")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ar.automatic-billing.backfill.max-age-days=-1")
                .run(context -> assertThat(context).hasFailed());
    }
}
