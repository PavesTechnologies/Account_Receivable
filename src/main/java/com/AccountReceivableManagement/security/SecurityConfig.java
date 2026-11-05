package com.AccountReceivableManagement.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@org.springframework.boot.context.properties.EnableConfigurationProperties(FinancialSecurityProperties.class)
public class SecurityConfig {

    @Autowired
    private CustomAccessDeniedHandler customAccessDeniedHandler;
    @Autowired
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @Autowired
    private CorsConfigurationSource corsConfigurationSource;
    @Autowired
    private OperationsReportAccess operationsReportAccess;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/health",
                                "/public/**",
                                // Load balancer / Kubernetes probes: status only, never details.
                                "/health",
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // Operations reports carry client and financial data: configured authorities only.
                        .requestMatchers("/api/v1/operations/**").access(operationsReportAccess)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(customAccessDeniedHandler)
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * The UMS token decoder: signature against the issuer's published keys,
     * plus {@link JwtTokenValidation}. Resolved on the first request (as
     * Spring Boot's own issuer-uri decoder is), so startup does not depend on
     * UMS being reachable.
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder(
            @org.springframework.beans.factory.annotation.Value(
                    "${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            FinancialSecurityProperties securityProperties) {
        return new org.springframework.security.oauth2.jwt.SupplierJwtDecoder(() -> {
            org.springframework.security.oauth2.jwt.NimbusJwtDecoder decoder =
                    (org.springframework.security.oauth2.jwt.NimbusJwtDecoder)
                            org.springframework.security.oauth2.jwt.JwtDecoders.fromIssuerLocation(issuerUri);
            decoder.setJwtValidator(JwtTokenValidation.validator(issuerUri,
                    securityProperties.getJwt().getAudiences()));
            return decoder;
        });
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                for (String perm : permissions) {
                    authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(perm));
                }
            }
            return authorities;
        });
        return converter;
    }
}
