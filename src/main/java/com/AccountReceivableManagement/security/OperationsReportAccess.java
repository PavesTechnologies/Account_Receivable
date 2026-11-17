package com.AccountReceivableManagement.security;

import com.AccountReceivableManagement.config.properties.OperationsMonitoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Grants the operations reports ({@code /api/v1/operations/**}) to
 * authenticated callers holding one of {@code ar.operations.report-authorities}.
 * With none configured, nobody is granted: the reports carry client and
 * financial data, and no UMS role for them has been agreed yet (see UAT-D1).
 */
@Component
@RequiredArgsConstructor
public class OperationsReportAccess implements AuthorizationManager<RequestAuthorizationContext> {

    private final OperationsMonitoringProperties properties;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        Set<String> allowed = properties.getReportAuthorities().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        boolean granted = current.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(allowed::contains);
        return new AuthorizationDecision(granted);
    }
}
