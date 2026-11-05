package com.AccountReceivableManagement.integration.billing_data_acquisition;

import java.time.Duration;

/**
 * TMS answered 429 (Too Many Requests) and the bounded in-call retries did
 * not get through. Temporary, like a 5xx, so automatic billing retries the
 * run - no earlier than TMS's {@code Retry-After}, when it sent one.
 */
public class TmsRateLimitedException extends TmsServiceUnavailableException {

    private final Duration retryAfter;

    public TmsRateLimitedException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /** TMS's {@code Retry-After}, or {@code null} when it sent none (or an unreadable one). */
    public Duration getRetryAfter() {
        return retryAfter;
    }
}
