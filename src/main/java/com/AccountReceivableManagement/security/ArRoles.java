package com.AccountReceivableManagement.security;

/**
 * The AR role names, as UMS issues them in the {@code roles} claim.
 *
 * <p>These two names are the only place a role is spelled out in Java. The
 * access annotations ({@link ExecutiveOnly}, {@link ManagerOnly},
 * {@link AnyFinanceRole}) are the only things that reference them, and every
 * endpoint references an annotation - never a role. A role rename is therefore
 * a change to the annotations alone, not to the 200-odd endpoints behind them.
 *
 * <p>The values are the canonical form produced by {@link RoleNames}: UMS sends
 * {@code Finance_Executive} and the JWT converter normalises it, so the
 * spelling UMS happens to use does not have to be repeated here.
 *
 * <p>Note that the two roles are <strong>not</strong> a hierarchy. Neither
 * outranks the other: invoice approval belongs to the manager, the authorized
 * signature to the executive. Code must never assume one implies the other.
 */
public final class ArRoles {

    /**
     * Finance Executive. Holds the authorized signature
     * (see {@code docs/authorized-signature.md}).
     */
    public static final String FINANCE_EXECUTIVE = "FINANCE_EXECUTIVE";

    /**
     * Finance Manager. Approves and rejects invoices
     * (see {@code UAT_AND_ROLLOUT_PLAN.md} AUTH-1 and
     * {@code AR_BACKEND_API_CONTRACT.md} §5).
     */
    public static final String FINANCE_MANAGER = "FINANCE_MANAGER";

    private ArRoles() {
    }
}
