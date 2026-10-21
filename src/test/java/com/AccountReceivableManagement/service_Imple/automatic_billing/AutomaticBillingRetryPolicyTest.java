package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TmsServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Failure classification and bounded backoff - no Spring context, no database. */
class AutomaticBillingRetryPolicyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private AutomaticBillingSchedulerProperties properties;
    private AutomaticBillingRetryPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new AutomaticBillingSchedulerProperties();
        policy = new AutomaticBillingRetryPolicy(properties, CLOCK);
    }

    // ================================================================ classification

    @Test
    void temporaryFailures_areRetryable() {
        assertThat(policy.isRetryable(new TmsServiceUnavailableException("TMS service failed with status 503.")))
                .isTrue();
        assertThat(policy.isRetryable(new ResourceAccessException("I/O error", new SocketTimeoutException())))
                .isTrue();
        assertThat(policy.isRetryable(new CannotGetJdbcConnectionException("connection refused"))).isTrue();
        assertThat(policy.isRetryable(new CannotCreateTransactionException("pool exhausted"))).isTrue();
        assertThat(policy.isRetryable(new CannotAcquireLockException("lock wait timeout"))).isTrue();
        assertThat(policy.isRetryable(new QueryTimeoutException("timeout"))).isTrue();
        assertThat(policy.isRetryable(new ObjectOptimisticLockingFailureException("run", "id"))).isTrue();
        // A concurrent duplicate: the next attempt finds and reuses the winner's record.
        assertThat(policy.isRetryable(new GlobalExceptionHandler.DuplicateResourceException(
                "An invoice has already been generated for this billing occurrence."))).isTrue();
    }

    @Test
    void temporaryCause_wrappedInAnUnclassifiedException_isRetryable() {
        assertThat(policy.isRetryable(new RuntimeException("acquisition failed",
                new CannotGetJdbcConnectionException("connection reset")))).isTrue();
    }

    @Test
    void businessAndConfigurationFailures_areNotRetryable() {
        assertThat(policy.isRetryable(new GlobalExceptionHandler.ValidationException(
                "No active hourly rate found for billing configuration X."))).isFalse();
        assertThat(policy.isRetryable(new GlobalExceptionHandler.ResourceNotFoundException(
                "Billing occurrence could not be found."))).isFalse();
        // TMS 400
        assertThat(policy.isRetryable(new IllegalArgumentException("TMS rejected the request"))).isFalse();
        // TMS 401/403 - authentication is configuration, not a temporary condition.
        assertThat(policy.isRetryable(new IllegalStateException("TMS authentication failed"))).isFalse();
        // Unknown failures need a person to look at them.
        assertThat(policy.isRetryable(new NullPointerException())).isFalse();
    }

    @Test
    void validationFailure_isNeverRetried_whateverItWasCausedBy() {
        assertThat(policy.isRetryable(new GlobalExceptionHandler.ValidationException("bad data") {
            @Override
            public synchronized Throwable getCause() {
                return new CannotGetJdbcConnectionException("connection reset");
            }
        })).isFalse();
    }

    // ================================================================ backoff and limits

    @Test
    void delayDoublesFromTheInitialDelay_andIsCapped() {
        properties.getRetry().setMaxAttempts(10);
        Throwable temporary = new TmsServiceUnavailableException("503");

        assertThat(policy.nextRetryAt(temporary, 1)).isEqualTo(NOW.plusMinutes(15));
        assertThat(policy.nextRetryAt(temporary, 2)).isEqualTo(NOW.plusMinutes(30));
        assertThat(policy.nextRetryAt(temporary, 3)).isEqualTo(NOW.plusMinutes(60));
        assertThat(policy.nextRetryAt(temporary, 9)).isEqualTo(NOW.plusHours(6));
    }

    @Test
    void lastAttempt_isNeverFollowedByAnotherRetry() {
        Throwable temporary = new TmsServiceUnavailableException("503");

        assertThat(policy.nextRetryAt(temporary, 2)).isNotNull();
        assertThat(policy.nextRetryAt(temporary, 3)).isNull();
        assertThat(policy.nextRetryAt(temporary, 4)).isNull();
    }

    @Test
    void permanentFailure_getsNoRetryEvenWithAttemptsLeft() {
        assertThat(policy.nextRetryAt(new GlobalExceptionHandler.ValidationException("bad data"), 1)).isNull();
    }

    @Test
    void retriesDisabled_meansNoRetry() {
        properties.getRetry().setEnabled(false);

        assertThat(policy.nextRetryAt(new TmsServiceUnavailableException("503"), 1)).isNull();
    }

    @Test
    void configuredPolicy_isApplied() {
        properties.getRetry().setMaxAttempts(5);
        properties.getRetry().setInitialDelay(Duration.ofMinutes(5));
        properties.getRetry().setMultiplier(3.0);
        properties.getRetry().setMaxDelay(Duration.ofHours(1));
        Throwable temporary = new QueryTimeoutException("timeout");

        assertThat(policy.nextRetryAt(temporary, 1)).isEqualTo(NOW.plusMinutes(5));
        assertThat(policy.nextRetryAt(temporary, 2)).isEqualTo(NOW.plusMinutes(15));
        assertThat(policy.nextRetryAt(temporary, 3)).isEqualTo(NOW.plusMinutes(45));
        assertThat(policy.nextRetryAt(temporary, 4)).isEqualTo(NOW.plusHours(1));
        assertThat(policy.nextRetryAt(temporary, 5)).isNull();
    }
}
