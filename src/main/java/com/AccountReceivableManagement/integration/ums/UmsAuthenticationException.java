package com.AccountReceivableManagement.integration.ums;

/**
 * AR could not obtain a usable UMS service token for a reason that does not
 * clear on its own: credentials missing or rejected, or a login response
 * without a usable access token. Not retried by automatic billing - someone
 * has to fix the configuration (then re-queue the failed runs).
 * <p>
 * Messages never contain the credentials, the token or the UMS response body.
 */
public class UmsAuthenticationException extends IllegalStateException {

    public UmsAuthenticationException(String message) {
        super(message);
    }
}
