package com.AccountReceivableManagement.config;

import java.util.List;

/** Exposes {@link CorsConfig}'s origin validation to tests in other packages. */
public final class CorsConfigTestAccess {

    private CorsConfigTestAccess() {
    }

    public static List<String> trustedOrigins(String configured) {
        return CorsConfig.trustedOrigins(configured);
    }
}
