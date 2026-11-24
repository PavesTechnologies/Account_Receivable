package com.AccountReceivableManagement.security;

import java.util.Locale;

/**
 * Canonical spelling of a UMS role or permission name.
 *
 * <p>UMS issues role names in its own spelling ({@code Finance_Executive}),
 * which is not the spelling {@code hasAnyRole('FINANCE_EXECUTIVE')} compares
 * against. Matching the two literally would make authorization depend on how
 * UMS happens to capitalise a role: a rename from {@code Finance_Executive} to
 * {@code Finance Executive} would silently deny every holder, with no error to
 * explain it.
 *
 * <p>Every authority therefore passes through {@link #canonical(String)} before
 * it is compared: case is folded, and runs of space, hyphen and underscore
 * collapse to a single underscore. {@code "Finance Executive"},
 * {@code "finance-executive"} and {@code "FINANCE_EXECUTIVE"} are one role. The
 * name itself must still match exactly - this normalises spelling, never
 * identity.
 *
 * <p>{@link SecurityConfig} applies it when converting JWT claims to
 * authorities, and {@link FinancialActionAuthorizer} when comparing configured
 * authorities, so both sides of every check use the same form.
 */
public final class RoleNames {

    /** Spring Security's prefix for a role authority, as {@code hasAnyRole} expects it. */
    public static final String ROLE_PREFIX = "ROLE_";

    private RoleNames() {
    }

    /**
     * {@code authority} with case folded and separators normalised, or an empty
     * string when it is {@code null} or blank. No prefix is added or removed.
     */
    public static String canonical(String authority) {
        return authority == null ? ""
                : authority.trim().replaceAll("[\\s\\-_]+", "_").toUpperCase(Locale.ROOT);
    }

    /**
     * {@code role} as the authority {@code hasAnyRole('<role>')} looks for:
     * canonical, with a single {@code ROLE_} prefix. A role UMS already sends
     * prefixed is not prefixed twice. Empty when {@code role} is blank.
     */
    public static String roleAuthority(String role) {
        String canonical = canonical(role);
        if (canonical.isEmpty()) {
            return "";
        }
        return canonical.startsWith(ROLE_PREFIX) ? canonical : ROLE_PREFIX + canonical;
    }
}
