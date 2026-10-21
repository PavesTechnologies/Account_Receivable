package com.AccountReceivableManagement.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenValidationTest {

    private static final String ISSUER = "https://ums.paves.example/auth";

    private Jwt createToken(String issuer, Instant issuedAt, Instant expiresAt, Map<String, Object> claims) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claims(c -> c.putAll(claims))
                .build();
    }

    @Test
    void validToken_withUserId_passesValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, Collections.emptyList());

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("user_id", "42", "email", "user@example.com"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void missingUserIdClaim_failsValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, Collections.emptyList());

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("email", "user@example.com"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(err -> err.getDescription().contains("user_id"));
    }

    @Test
    void wrongIssuer_failsValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, Collections.emptyList());

        Jwt jwt = createToken("https://malicious.issuer.com", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("user_id", "42"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void expiredToken_failsValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, Collections.emptyList());

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600),
                Map.of("user_id", "42"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void audienceConfigured_matchingAudience_passesValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, List.of("ar-service", "paves-ar"));

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("user_id", "42", "aud", List.of("ar-service")));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void audienceConfigured_wrongAudience_failsValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, List.of("ar-service"));

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("user_id", "42", "aud", List.of("other-service")));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void audienceConfigured_missingAudience_failsValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, List.of("ar-service"));

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("user_id", "42"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void audienceNotConfigured_tokenWithoutAudience_passesValidation() {
        OAuth2TokenValidator<Jwt> validator = JwtTokenValidation.validator(ISSUER, List.of());

        Jwt jwt = createToken(ISSUER, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                Map.of("user_id", "42"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }
}
