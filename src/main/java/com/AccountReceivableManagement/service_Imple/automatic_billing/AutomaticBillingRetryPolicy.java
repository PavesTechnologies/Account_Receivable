package com.AccountReceivableManagement.service_Imple.automatic_billing;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TmsRateLimitedException;
import com.AccountReceivableManagement.integration.billing_data_acquisition.TmsServiceUnavailableException;
import com.AccountReceivableManagement.integration.ums.UmsServiceUnavailableException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Decides whether a failed automatic billing run is retried, and when.
 * <p>
 * Retryable - conditions that clear on their own; the run resumes from its
 * last completed step and every step is find-or-create, so a retry never
 * duplicates a snapshot, tax calculation or invoice:
 * <ul>
 *   <li>TMS unavailable (5xx), rate limited (429) or unreachable (I/O, timeout) -
 *       a 429 is not retried before TMS's {@code Retry-After};</li>
 *   <li>UMS service login temporarily unavailable (unreachable, 429, 5xx);</li>
 *   <li>database connectivity, lock and transient failures;</li>
 *   <li>a concurrent duplicate insert ({@code DuplicateResourceException}) -
 *       the next attempt finds and reuses the winner's record.</li>
 * </ul>
 * Not retryable - they need someone to fix data or configuration:
 * business-rule and data validation ({@code ValidationException},
 * {@code ResourceNotFoundException}, {@code IllegalArgumentException} - e.g.
 * TMS 400, a TMS response of another project or period), TMS and UMS
 * authentication/authorization (rejected credentials), malformed responses, and
 * anything unrecognised. A failure is retryable when it, or any of its
 * causes, is of a retryable type.
 * <p>
 * Bounded: the n-th attempt that fails (n = the run's attempt count) is
 * retried after {@code initialDelay * multiplier^(n-1)}, capped at
 * {@code maxDelay}, only while n is below {@code maxAttempts}.
 */
@Component
public class AutomaticBillingRetryPolicy {

    private static final Set<Class<? extends Throwable>> RETRYABLE = Set.of(
            com.AccountReceivableManagement.integration.billing_data_acquisition.TimesheetsPendingApprovalException.class,
            TmsServiceUnavailableException.class,
            UmsServiceUnavailableException.class,
            ResourceAccessException.class,
            IOException.class,
            TimeoutException.class,
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class,
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class,
            java.sql.SQLTransientException.class,
            java.sql.SQLRecoverableException.class,
            GlobalExceptionHandler.DuplicateResourceException.class);

    /** Never retryable, even when caused by a retryable type. */
    private static final Set<Class<? extends Throwable>> PERMANENT = Set.of(
            GlobalExceptionHandler.ValidationException.class,
            GlobalExceptionHandler.ResourceNotFoundException.class,
            IllegalArgumentException.class);

    private final AutomaticBillingSchedulerProperties.Retry retry;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AutomaticBillingRetryPolicy(AutomaticBillingSchedulerProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    AutomaticBillingRetryPolicy(AutomaticBillingSchedulerProperties properties, Clock clock) {
        this.retry = properties.getRetry();
        this.clock = clock;
    }

    /** {@link #nextRetryAt(Throwable, int, LocalDateTime)} from now. */
    public LocalDateTime nextRetryAt(Throwable failure, int attemptCount) {
        return nextRetryAt(failure, attemptCount, LocalDateTime.now(clock));
    }

    public boolean isRetryable(Throwable failure) {
        int depth = 0;
        for (Throwable cause = failure; cause != null && depth < 20; cause = cause.getCause(), depth++) {
            if (isAny(cause, PERMANENT)) {
                return false;
            }
            if (isAny(cause, RETRYABLE)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * When a run that has just failed its {@code attemptCount}-th attempt
     * should next be attempted, or {@code null} for no automatic retry
     * (permanent failure, retries disabled, or attempts exhausted).
     */
    public LocalDateTime nextRetryAt(Throwable failure, int attemptCount, LocalDateTime now) {
        if (!retry.isEnabled() || !isRetryable(failure) || hasExhaustedAttempts(attemptCount)) {
            return null;
        }
        Duration delay = delayAfterAttempt(attemptCount);
        Duration retryAfter = retryAfterOf(failure);
        return now.plus(retryAfter != null && retryAfter.compareTo(delay) > 0 ? retryAfter : delay);
    }

    /** TMS's {@code Retry-After} of a rate-limited failure anywhere in the cause chain, if any. */
    private static Duration retryAfterOf(Throwable failure) {
        int depth = 0;
        for (Throwable cause = failure; cause != null && depth < 20; cause = cause.getCause(), depth++) {
            if (cause instanceof TmsRateLimitedException rateLimited) {
                return rateLimited.getRetryAfter();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    public boolean hasExhaustedAttempts(int attemptCount) {
        return attemptCount >= retry.getMaxAttempts();
    }

    Duration delayAfterAttempt(int attemptCount) {
        double factor = Math.pow(retry.getMultiplier(), Math.max(0, attemptCount - 1));
        double millis = retry.getInitialDelay().toMillis() * factor;
        long maxMillis = retry.getMaxDelay().toMillis();
        return Duration.ofMillis(millis >= maxMillis ? maxMillis : (long) millis);
    }

    private static boolean isAny(Throwable failure, Set<Class<? extends Throwable>> types) {
        return types.stream().anyMatch(type -> type.isInstance(failure));
    }
}
