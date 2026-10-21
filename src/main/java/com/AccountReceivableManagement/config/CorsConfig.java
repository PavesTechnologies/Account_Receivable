package com.AccountReceivableManagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Cross-origin access for the AR frontend (G-25). The trusted origins are
 * configured per environment ({@code ar.cors.allowed-origins}); none is
 * built in, so a deployment never silently depends on a developer's
 * localhost. Empty = no cross-origin browser access (a frontend served from
 * the same origin, e.g. behind the same ingress host, needs none). Because
 * credentials are allowed, a wildcard origin is refused at startup.
 */
@Configuration
public class CorsConfig {

    static List<String> trustedOrigins(String configured) {
        List<String> origins = Arrays.stream(configured == null ? new String[0] : configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalStateException("ar.cors.allowed-origins must list explicit origins; '" + origin
                        + "' is a wildcard, which is not allowed with credentials.");
            }
            if (!origin.startsWith("https://") && !origin.startsWith("http://")) {
                throw new IllegalStateException("ar.cors.allowed-origins entry '" + origin
                        + "' must be a scheme://host[:port] origin.");
            }
        }
        return origins;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${ar.cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = trustedOrigins(allowedOrigins);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (origins.isEmpty()) {
                    return;
                }
                registry.addMapping("/**")
                        .allowedOrigins(origins.toArray(String[]::new))
                        .allowedMethods(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
