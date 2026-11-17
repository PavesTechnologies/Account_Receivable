package com.AccountReceivableManagement.security;

/**
 * Who performed an action, for audit: an authenticated UMS user (stable
 * {@code userId} plus a readable {@code label}) or the system.
 */
public record Actor(Long userId, String label) {

    public static final Actor SYSTEM = new Actor(null, "SYSTEM");

    public boolean isSystem() {
        return userId == null;
    }
}
