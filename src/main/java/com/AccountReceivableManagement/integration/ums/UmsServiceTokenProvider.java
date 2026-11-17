package com.AccountReceivableManagement.integration.ums;

import com.AccountReceivableManagement.config.properties.UmsServiceAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Obtains and caches the access token AR uses for its own (background) calls
 * to other Paves services - today, the automatic billing scheduler's TMS
 * calls, where no user JWT exists. Interactive requests keep forwarding the
 * user's own JWT and never use this token.
 * <p>
 * Contract (UMS {@code POST /ums/auth/login}): JSON {@code {email, password}}
 * in, JSON with {@code access_token} out. Expiry is taken from the response's
 * {@code expires_in} when present and from the token's own {@code exp} claim
 * when it is a JWT - the earlier of the two; none is invented. A token whose
 * expiry cannot be determined is used for the one call that asked for it and
 * not cached. {@code exp} is read without verifying the signature: it only
 * decides when to log in again (TMS, not AR, validates the token), and the
 * token came straight from UMS over the configured connection.
 * <p>
 * Lifecycle and failures:
 * <ul>
 *   <li>one login at a time - concurrent callers wait for it and share its
 *       token instead of logging in again;</li>
 *   <li>renewed {@code refresh-before-expiry} ahead of expiry (at most half
 *       its lifetime);</li>
 *   <li>{@link #invalidate} drops a token TMS refused, so the next call logs
 *       in again - the caller re-authenticates at most once per request;</li>
 *   <li>credentials rejected (400/401/403) or a response without a usable
 *       token: {@link UmsAuthenticationException}, never retried; after a
 *       rejection no login is attempted for {@code credential-rejection-cooldown},
 *       so a wrong password cannot lock the account;</li>
 *   <li>UMS unreachable, timed out, 429 or 5xx: {@link UmsServiceUnavailableException},
 *       which automatic billing retries later with its bounded backoff.</li>
 * </ul>
 * Nothing logged or thrown contains the password, the token, the
 * Authorization header or the login response body.
 */
@Slf4j
@Component
public class UmsServiceTokenProvider {

    /** Login responses are small; anything larger is not a login response. */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final UmsServiceAuthProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final ReentrantLock loginLock = new ReentrantLock();

    private volatile CachedToken cached;
    private volatile Instant loginSuspendedUntil;

    private record CachedToken(String value, Instant expiresAt, Instant refreshAt) {
    }

    private record LoginResponse(int status, byte[] body) {
    }

    @Autowired
    public UmsServiceTokenProvider(UmsServiceAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    UmsServiceTokenProvider(UmsServiceAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (!properties.isEnabled()) {
            this.restClient = null;
            return;
        }
        requireConfigured(properties);
        // java.net.http, not HttpURLConnection: the latter reports a 401/403 answer to a
        // POST as an I/O error (HttpRetryException), which would make rejected
        // credentials look like a temporary outage and be retried.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        log.info("[UmsServiceToken] UMS service-account authentication enabled (login endpoint host {}).",
                URI.create(properties.getLoginUrl().trim()).getHost());
    }

    /**
     * Fail closed at startup: enabled without complete, safe configuration is
     * a deployment error, not something to discover on the first billing run.
     */
    private static void requireConfigured(UmsServiceAuthProperties properties) {
        StringBuilder missing = new StringBuilder();
        if (isBlank(properties.getLoginUrl())) {
            missing.append(" UMS_LOGIN_URL (ar.ums.service-auth.login-url)");
        }
        if (isBlank(properties.getEmail())) {
            missing.append(" UMS_SERVICE_EMAIL (ar.ums.service-auth.email)");
        }
        if (isBlank(properties.getPassword())) {
            missing.append(" UMS_SERVICE_PASSWORD (ar.ums.service-auth.password)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("UMS service authentication is enabled but not configured; missing:"
                    + missing + ".");
        }
        URI loginUri;
        try {
            loginUri = URI.create(properties.getLoginUrl().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("UMS login URL (ar.ums.service-auth.login-url) is not a valid URL.");
        }
        if (loginUri.getHost() == null
                || !("https".equalsIgnoreCase(loginUri.getScheme())
                        || ("http".equalsIgnoreCase(loginUri.getScheme()) && isLoopback(loginUri.getHost())))) {
            throw new IllegalStateException("UMS login URL (ar.ums.service-auth.login-url) must use https "
                    + "(plain http is only accepted for a loopback test server): the request carries the "
                    + "service-account password.");
        }
        for (Duration duration : new Duration[] {properties.getConnectTimeout(), properties.getReadTimeout()}) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalStateException("UMS connect and read timeouts must be positive.");
            }
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * A valid access token: the cached one while it is not due for renewal,
     * otherwise a fresh login (one at a time).
     *
     * @throws UmsAuthenticationException    not enabled, credentials rejected, or unusable response
     * @throws UmsServiceUnavailableException UMS temporarily unavailable
     */
    public String getAccessToken() {
        if (!properties.isEnabled()) {
            throw new UmsAuthenticationException("UMS service authentication is not enabled "
                    + "(AR_UMS_SERVICE_AUTH_ENABLED).");
        }
        CachedToken current = cached;
        if (isFresh(current)) {
            return current.value();
        }
        loginLock.lock();
        try {
            current = cached;
            if (isFresh(current)) {
                // Another caller logged in while this one waited.
                return current.value();
            }
            return login();
        } finally {
            loginLock.unlock();
        }
    }

    /**
     * Drops {@code token} from the cache if it is still the cached one -
     * called when TMS refused it, so the next {@link #getAccessToken()} logs
     * in again. A token another caller has already replaced is left alone.
     */
    public void invalidate(String token) {
        loginLock.lock();
        try {
            CachedToken current = cached;
            if (current != null && current.value().equals(token)) {
                cached = null;
                log.info("[UmsServiceToken] cached service token invalidated after it was refused.");
            }
        } finally {
            loginLock.unlock();
        }
    }

    private boolean isFresh(CachedToken token) {
        return token != null && clock.instant().isBefore(token.refreshAt());
    }

    // ================================================================ login

    private String login() {
        Instant now = clock.instant();
        Instant suspendedUntil = loginSuspendedUntil;
        if (suspendedUntil != null && now.isBefore(suspendedUntil)) {
            throw new UmsAuthenticationException("UMS rejected the service-account credentials; no new login is "
                    + "attempted before " + suspendedUntil + ". Correct UMS_SERVICE_EMAIL / UMS_SERVICE_PASSWORD "
                    + "and restart, then re-queue the failed runs.");
        }

        LoginResponse response = callLogin();
        int status = response.status();

        if (status == 400 || status == 401 || status == 403) {
            Duration cooldown = properties.getCredentialRejectionCooldown();
            loginSuspendedUntil = cooldown != null && !cooldown.isNegative() ? now.plus(cooldown) : null;
            cached = null;
            count("rejected");
            log.error("[UmsServiceToken] UMS rejected the service-account login (status {}); further logins "
                    + "suspended until {}.", status, loginSuspendedUntil);
            throw new UmsAuthenticationException("UMS rejected the service-account login (status " + status
                    + "); check UMS_SERVICE_EMAIL / UMS_SERVICE_PASSWORD and that the account is allowed to log in.");
        }
        if (status == 429 || status >= 500) {
            count("unavailable");
            throw new UmsServiceUnavailableException("UMS login temporarily unavailable (status " + status + ").");
        }
        if (status < 200 || status >= 300) {
            count("unexpected_status");
            throw new UmsAuthenticationException("Unexpected UMS login response (status " + status + ").");
        }

        String token = extractAccessToken(response.body());
        Instant expiresAt = resolveExpiry(response.body(), token, now);
        loginSuspendedUntil = null;
        count("success");

        if (expiresAt == null) {
            cached = null;
            log.warn("[UmsServiceToken] UMS login response carries no expiry (no expires_in, token has no exp "
                    + "claim); the token is used for this call only and not cached.");
            return token;
        }
        if (!expiresAt.isAfter(now)) {
            cached = null;
            count("invalid_response");
            throw new UmsAuthenticationException("UMS issued an access token that has already expired "
                    + "(check the clocks of AR and UMS).");
        }
        Duration lifetime = Duration.between(now, expiresAt);
        Duration skew = properties.getRefreshBeforeExpiry() != null && !properties.getRefreshBeforeExpiry().isNegative()
                ? properties.getRefreshBeforeExpiry()
                : Duration.ZERO;
        if (skew.compareTo(lifetime.dividedBy(2)) > 0) {
            skew = lifetime.dividedBy(2);
        }
        cached = new CachedToken(token, expiresAt, expiresAt.minus(skew));
        log.info("[UmsServiceToken] service token obtained; expires at {}, renewed from {}.",
                expiresAt, expiresAt.minus(skew));
        return token;
    }

    private LoginResponse callLogin() {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("email", properties.getEmail().trim());
        credentials.put("password", properties.getPassword());
        try {
            return restClient.post()
                    .uri(URI.create(properties.getLoginUrl().trim()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(credentials)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        // Only a successful answer's body is needed; an error body is never read.
                        return new LoginResponse(status, status >= 200 && status < 300
                                ? readBounded(response.getBody())
                                : new byte[0]);
                    });
        } catch (RestClientException ex) {
            // Unreachable or timed out. The cause names the URL and the I/O error only.
            count("unavailable");
            throw new UmsServiceUnavailableException("UMS login could not be completed ("
                    + rootCauseName(ex) + ").", ex);
        }
    }

    /** The {@code access_token}; the body itself is never put in a message or log. */
    private String extractAccessToken(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException malformed) {
            root = null;
        }
        if (root == null || !root.isObject()) {
            count("invalid_response");
            throw new UmsAuthenticationException("UMS login response is not a JSON object; no access token "
                    + "could be read.");
        }
        JsonNode accessToken = root.get("access_token");
        if (accessToken == null || !accessToken.isTextual() || accessToken.asText().isBlank()) {
            count("invalid_response");
            throw new UmsAuthenticationException("UMS login response did not contain an access_token.");
        }
        return accessToken.asText().trim();
    }

    /** Earlier of {@code expires_in} and the JWT {@code exp} claim; {@code null} if neither is present. */
    private Instant resolveExpiry(byte[] body, String token, Instant now) {
        Instant fromResponse = null;
        try {
            JsonNode expiresIn = objectMapper.readTree(body).get("expires_in");
            if (expiresIn != null && expiresIn.canConvertToLong() && expiresIn.asLong() > 0) {
                fromResponse = now.plusSeconds(expiresIn.asLong());
            }
        } catch (IOException ignored) {
            // Already parsed successfully by extractAccessToken.
        }
        Instant fromToken = jwtExpiry(token);
        if (fromResponse == null) {
            return fromToken;
        }
        if (fromToken == null) {
            return fromResponse;
        }
        return fromResponse.isBefore(fromToken) ? fromResponse : fromToken;
    }

    /** The {@code exp} claim of a JWT; {@code null} for an opaque token or one without {@code exp}. */
    private Instant jwtExpiry(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode exp = claims != null ? claims.get("exp") : null;
            return exp != null && exp.canConvertToLong() ? Instant.ofEpochSecond(exp.asLong()) : null;
        } catch (IllegalArgumentException | IOException notAJwt) {
            return null;
        }
    }

    // ================================================================ helpers

    private static byte[] readBounded(InputStream body) throws IOException {
        if (body == null) {
            return new byte[0];
        }
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            // Not a login response; parsing it would fail anyway.
            return "oversized".getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    private static String rootCauseName(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private static boolean isLoopback(String host) {
        try {
            return "localhost".equalsIgnoreCase(host) || InetAddress.getByName(host).isLoopbackAddress();
        } catch (IOException unresolvable) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void count(String outcome) {
        Metrics.counter("ar.ums.service_token.logins", "outcome", outcome).increment();
    }
}
