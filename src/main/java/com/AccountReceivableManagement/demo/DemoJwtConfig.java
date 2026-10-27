package com.AccountReceivableManagement.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Demo-only authentication for the {@code local-demo} profile: verifies HS256 tokens signed with a local secret instead of
 * the shared UMS issuer. It only replaces <em>how the signature is verified</em>. {@code SecurityConfig} is untouched, so
 * every request still needs a valid, unexpired token and the existing role and permission handling still applies - the
 * {@code roles} and {@code permissions} claims, {@code user_id}, {@code email} and {@code name} are read exactly as before.
 *
 * <p>Cannot activate outside {@code local-demo}: it is {@code @Profile}-gated, needs {@code ar.demo.jwt.secret}, and the
 * isolation guard refuses to start the profile without a secret of at least 32 characters. No secret exists in the repo;
 * it comes from the {@code AR_DEMO_JWT_SECRET} environment variable. Tokens are minted by
 * {@code scripts/local-demo/new-token.ps1}.
 */
@Configuration(proxyBeanMethods = false)
@Profile(LocalDemoIsolationValidator.PROFILE)
public class DemoJwtConfig {

    /**
     * Issuer the demo token helper stamps; tokens from any other issuer are rejected. URL-shaped (like a real issuer, so
     * {@code Jwt.getIssuer()} works) but on the reserved {@code .invalid} domain: it is an identifier, never contacted.
     */
    public static final String ISSUER = "https://ar-demo.invalid/local";

    @Bean
    JwtDecoder demoJwtDecoder(@Value("${ar.demo.jwt.secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < LocalDemoIsolationValidator.MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("ar.demo.jwt.secret must be at least "
                    + LocalDemoIsolationValidator.MIN_JWT_SECRET_LENGTH + " characters");
        }
        SecretKey secretKey = new SecretKeySpec(key, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> expiryRequired = jwt -> jwt.getExpiresAt() == null
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The token has no expiry.", null))
                : OAuth2TokenValidatorResult.success();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), expiryRequired));
        return decoder;
    }
}
