package com.AccountReceivableManagement.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign Client Configuration for TMS integration.
 * Automatically extracts the Authorization JWT header from the incoming
 * HTTP request context and forwards it on all outbound Feign calls to TMS.
 */
@Configuration
public class TmsFeignConfig {

    @Bean
    public RequestInterceptor tmsJwtRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (authHeader != null && !authHeader.isBlank()) {
                        template.header(HttpHeaders.AUTHORIZATION, authHeader);
                    }
                }
            }
        };
    }
}
