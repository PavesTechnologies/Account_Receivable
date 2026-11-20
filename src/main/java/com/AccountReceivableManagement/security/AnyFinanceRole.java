package com.AccountReceivableManagement.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Either finance role. Anyone without one is refused.
 *
 * <p>For the shared day-to-day work both roles do: reading an invoice, its PDF
 * and its delivery history, and the billing records they both maintain.
 *
 * <p>"Either role" is not "any authenticated user". A valid UMS token for
 * someone outside finance does not pass; before this annotation existed,
 * {@code /api/**} required only authentication, and it did.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole("
        + "T(com.AccountReceivableManagement.security.ArRoles).FINANCE_EXECUTIVE, "
        + "T(com.AccountReceivableManagement.security.ArRoles).FINANCE_MANAGER)")
public @interface AnyFinanceRole {
}
