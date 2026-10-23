package com.AccountReceivableManagement.demo;

import com.AccountReceivableManagement.security.CurrentUserService;
import com.AccountReceivableManagement.security.FinanceExecutiveAuthorizer;
import com.AccountReceivableManagement.security.JwtAuthProperties;
import com.AccountReceivableManagement.security.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Demo JWT acceptance/rejection, preserved role handling, and the impossibility of sending real email. Offline. */
class LocalDemoAuthAndMailTest {

    private static final String SECRET = LocalDemoIsolationValidatorTest.SECRET;

    private final JwtDecoder decoder = new DemoJwtConfig().demoJwtDecoder(SECRET);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Date in(long millis) {
        return new Date(System.currentTimeMillis() + millis);
    }

    private static JWTClaimsSet.Builder claims(String issuer, Date exp, List<String> roles, List<String> permissions) {
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("demo-user")
                .claim("user_id", "1001")
                .claim("email", "demo.user@ar-demo.invalid")
                .claim("name", "Demo User")
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issueTime(new Date());
        if (exp != null) {
            b.expirationTime(exp);
        }
        return b;
    }

    private static String sign(String secret, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static String valid(List<String> roles, List<String> permissions) throws Exception {
        return sign(SECRET, claims(DemoJwtConfig.ISSUER, in(3_600_000), roles, permissions).build());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Decoder
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void acceptsAValidDemoToken() throws Exception {
        Jwt jwt = decoder.decode(valid(List.of("FINANCE_EXECUTIVE"), List.of("invoice.read")));

        assertThat(jwt.getClaimAsString("user_id")).isEqualTo("1001");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("FINANCE_EXECUTIVE");
        assertThat(jwt.getIssuer()).hasToString(DemoJwtConfig.ISSUER);
    }

    @Test
    void rejectsTokensThatAreNotValidDemoTokens() throws Exception {
        String wrongSecret = sign("another-secret-that-is-also-32-chars-long!",
                claims(DemoJwtConfig.ISSUER, in(3_600_000), List.of("FINANCE_EXECUTIVE"), List.of()).build());
        String wrongIssuer = sign(SECRET,
                claims("https://ums.shared-dev.example.com", in(3_600_000), List.of("FINANCE_EXECUTIVE"), List.of()).build());
        String expired = sign(SECRET,
                claims(DemoJwtConfig.ISSUER, in(-3_600_000), List.of("FINANCE_EXECUTIVE"), List.of()).build());
        String noExpiry = sign(SECRET,
                claims(DemoJwtConfig.ISSUER, null, List.of("FINANCE_EXECUTIVE"), List.of()).build());
        String unsigned = new PlainJWT(
                claims(DemoJwtConfig.ISSUER, in(3_600_000), List.of("FINANCE_EXECUTIVE"), List.of()).build()).serialize();
        String[] parts = valid(List.of("VIEWER"), List.of()).split("\\.");
        String tamperedPayload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("VIEWER", "FINANCE_EXECUTIVE");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        for (String bad : List.of(wrongSecret, wrongIssuer, expired, noExpiry, unsigned, tampered, "not-a-jwt", "")) {
            assertThatThrownBy(() -> decoder.decode(bad)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void refusesToBuildADecoderWithAShortSecret() {
        assertThatThrownBy(() -> new DemoJwtConfig().demoJwtDecoder("too-short-for-hmac"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters")
                .hasMessageNotContaining("too-short-for-hmac");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Existing role / claim handling is untouched and still applies to demo tokens
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void existingAuthorityMappingStillAppliesToDemoTokens() throws Exception {
        Jwt jwt = decoder.decode(valid(List.of("FINANCE_EXECUTIVE", "VIEWER"), List.of("invoice.approve")));
        Method converter = SecurityConfig.class.getDeclaredMethod("jwtAuthenticationConverter");
        converter.setAccessible(true);

        AbstractAuthenticationToken auth = ((JwtAuthenticationConverter) converter.invoke(new SecurityConfig())).convert(jwt);

        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()))
                .containsExactlyInAnyOrder("ROLE_FINANCE_EXECUTIVE", "ROLE_VIEWER", "invoice.approve");
    }

    @Test
    void currentUserIsStillDerivedFromTheUserIdEmailAndNameClaims() throws Exception {
        Jwt jwt = decoder.decode(valid(List.of("VIEWER"), List.of()));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        CurrentUserService service = new CurrentUserService();

        assertThat(service.getUserId()).isEqualTo(1001L);
        assertThat(service.getEmail()).isEqualTo("demo.user@ar-demo.invalid");
        assertThat(service.getName()).isEqualTo("Demo User");
    }

    private FinanceExecutiveAuthorizer demoAuthorizer() {
        StandardEnvironment env = LocalDemoProfileResolutionTest.resolve(LocalDemoProfileResolutionTest.osEnvironment(), "local-demo");
        JwtAuthProperties properties = Binder.get(env).bind("app.security.jwt", JwtAuthProperties.class).get();
        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.getRequiredRole()).isEqualTo("FINANCE_EXECUTIVE");
        return new FinanceExecutiveAuthorizer(properties);
    }

    @Test
    void signatureEndpointsKeepTheFinanceExecutiveRoleRequirementUnderTheDemoProfile() throws Exception {
        FinanceExecutiveAuthorizer authorizer = demoAuthorizer();
        String finance = "Bearer " + valid(List.of("FINANCE_EXECUTIVE"), List.of());
        String viewer = "Bearer " + valid(List.of("VIEWER"), List.of());
        String wrongSecret = "Bearer " + sign("another-secret-that-is-also-32-chars-long!",
                claims(DemoJwtConfig.ISSUER, in(3_600_000), List.of("FINANCE_EXECUTIVE"), List.of()).build());
        String wrongIssuer = "Bearer " + sign(SECRET,
                claims("someone-else", in(3_600_000), List.of("FINANCE_EXECUTIVE"), List.of()).build());

        assertThatCode(() -> authorizer.requireFinanceExecutive(finance)).doesNotThrowAnyException();
        assertThatCode(() -> authorizer.requireAuthenticated(viewer)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizer.requireFinanceExecutive(viewer))
                .extracting(e -> e.getClass().getSimpleName()).isEqualTo("AccessForbiddenException");
        for (String header : new String[]{null, "", "Bearer ", wrongSecret, wrongIssuer}) {
            assertThatThrownBy(() -> authorizer.requireFinanceExecutive(header))
                    .extracting(e -> e.getClass().getSimpleName()).isEqualTo("AuthenticationRequiredException");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mail
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void logOnlyMailSenderNeverConnectsAndNeverThrows() throws Exception {
        LogOnlyMailSender sender = spy(new LogOnlyMailSender());
        MimeMessage mime = sender.createMimeMessage();
        SimpleMailMessage simple = new SimpleMailMessage();
        simple.setTo("client@example.invalid");
        simple.setSubject("Invoice");
        simple.setText("body");

        assertThat(sender.getHost()).isNull(); // no SMTP target exists at all
        assertThatCode(() -> sender.send(mime)).doesNotThrowAnyException();
        assertThatCode(() -> sender.send(simple)).doesNotThrowAnyException();
        assertThatCode(sender::testConnection).doesNotThrowAnyException();
        verify(sender, org.mockito.Mockito.atLeast(2)).doSend(any(MimeMessage[].class), any());
    }
}
