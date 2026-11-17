package com.AccountReceivableManagement.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code ar.ums.service-auth.*}: how AR authenticates itself to UMS for
 * background work (the automatic billing scheduler calling TMS), where no
 * user JWT exists. Off by default: the UMS service-account login must be
 * approved by the UMS team for unattended use before it is enabled.
 * <p>
 * No {@code toString}: the password must never reach a log line.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ar.ums.service-auth")
public class UmsServiceAuthProperties {

    /** Off unless explicitly enabled; when on, every credential below is required (startup fails otherwise). */
    private boolean enabled = false;

    /** UMS login endpoint, e.g. {@code https://<ums-host>/ums/auth/login}. HTTPS required except on loopback. */
    private String loginUrl;

    /** Dedicated service-account login - never a person's account. */
    private String email;

    private String password;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(15);

    /**
     * How long before the token's own expiry it is renewed. Capped at half
     * the token's lifetime, so a short-lived token is still reused.
     */
    private Duration refreshBeforeExpiry = Duration.ofMinutes(2);

    /**
     * After UMS rejects the credentials (400/401/403), no further login is
     * attempted for this long - repeated attempts with a wrong password
     * could lock the service account.
     */
    private Duration credentialRejectionCooldown = Duration.ofMinutes(15);
}
