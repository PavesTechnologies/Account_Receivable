package com.AccountReceivableManagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures that endpoints are accessible with or without the "/paves/ar" prefix
 * when running at root context path in local development.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalContextPathFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/paves/ar";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        // Only forward if the servlet container is at root context path and the URI starts with /paves/ar
        if ((contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) && uri != null && uri.startsWith(PREFIX)) {
            String forwardPath = uri.substring(PREFIX.length());
            if (forwardPath.isEmpty()) {
                forwardPath = "/";
            }
            request.getRequestDispatcher(forwardPath).forward(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
