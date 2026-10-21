package com.AccountReceivableManagement.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The checks every UMS access token must pass, on top of the signature
 * (verified by the decoder against the issuer's keys):
 * <ul>
 *   <li>issuer equals the configured {@code issuer-uri}, and the token is
 *       within its validity (exp / nbf, with the default clock skew);</li>
 *   <li>a {@code user_id} claim - the identity AR records and authorizes
 *       ({@link CurrentUserService} cannot work without it), so a token
 *       lacking it is rejected as 401 instead of failing later;</li>
 *   <li>the audience, only when {@code ar.security.jwt.audiences} is set -
 *       no UMS audience contract is documented yet, so none is assumed.</li>
 * </ul>
 */
public final class JwtTokenValidation {

    static final String USER_ID_CLAIM = "user_id";

    private JwtTokenValidation() {
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, Collection<String> audiences) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuer));
        validators.add(new JwtClaimValidator<Object>(USER_ID_CLAIM, Objects::nonNull));
        List<String> accepted = audiences == null ? List.of()
                : audiences.stream().filter(Objects::nonNull).map(String::trim).filter(a -> !a.isEmpty()).toList();
        if (!accepted.isEmpty()) {
            validators.add(new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                    aud -> aud != null && aud.stream().anyMatch(accepted::contains)));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
