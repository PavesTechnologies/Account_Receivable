package com.AccountReceivableManagement.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Finance Executive only. A Finance Manager is refused.
 *
 * <p>For work that belongs to the executive alone - managing the authorized
 * signature, for example. This does not imply seniority: there are actions a
 * manager may take and an executive may not (see {@link ManagerOnly}).
 *
 * <p>The role name is not written here; it comes from {@link ArRoles}, so a
 * rename is a one-line change. {@code hasAnyRole} adds the {@code ROLE_}
 * prefix itself, and {@link SecurityConfig} produces authorities in the same
 * canonical form, so the two sides match whatever spelling UMS uses.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(T(com.AccountReceivableManagement.security.ArRoles).FINANCE_EXECUTIVE)")
public @interface ExecutiveOnly {
}
