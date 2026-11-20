package com.AccountReceivableManagement.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Finance Manager only. A Finance Executive is refused.
 *
 * <p>For work that belongs to the manager alone - approving and rejecting
 * invoices, and holding the record lock that precedes it.
 *
 * <p>This annotation decides <em>which role</em> may call the endpoint. It
 * cannot tell whether the caller is the same person who submitted the record;
 * that is maker-checker, enforced in the service layer by
 * {@link FinancialActionAuthorizer#requireDifferentFromMaker}. Both still
 * apply: this one keeps out the wrong role, that one the wrong person.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(T(com.AccountReceivableManagement.security.ArRoles).FINANCE_MANAGER)")
public @interface ManagerOnly {
}
