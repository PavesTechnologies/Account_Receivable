package com.AccountReceivableManagement.security;

import java.util.function.Supplier;

/**
 * Who is acting on the current thread: an authenticated user, or the system
 * (automatic billing). Automatic billing runs its work inside
 * {@link #callAsSystem}, so the records it writes are attributed to the
 * system - even if a user's request happens to be on the thread - and it can
 * never pass a check that requires a human decision.
 * <p>
 * {@link #currentUserId()} serves code that cannot be injected (JPA entity
 * lifecycle callbacks); the resolver is registered by
 * {@link FinancialActionAuthorizer} and reads the authenticated UMS identity.
 */
public final class ActorContext {

    private static final ThreadLocal<Boolean> SYSTEM = new ThreadLocal<>();

    private static volatile Supplier<Long> currentUserIdResolver = () -> null;

    private ActorContext() {
    }

    /** Runs {@code work} as the system (automatic billing). */
    public static <T> T callAsSystem(Supplier<T> work) {
        Boolean previous = SYSTEM.get();
        SYSTEM.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                SYSTEM.remove();
            } else {
                SYSTEM.set(previous);
            }
        }
    }

    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    /** The authenticated user's UMS id, or {@code null} for the system or when no user is authenticated. */
    public static Long currentUserId() {
        if (isSystem()) {
            return null;
        }
        try {
            return currentUserIdResolver.get();
        } catch (RuntimeException noAuthenticatedUser) {
            return null;
        }
    }

    static void registerCurrentUserIdResolver(Supplier<Long> resolver) {
        currentUserIdResolver = resolver;
    }
}
