package com.AccountReceivableManagement.integration.ums;

/**
 * The UMS login was not answered usefully for a temporary reason - UMS
 * unreachable, timed out, rate limited (429) or failing (5xx) - so automatic
 * billing may retry the run later.
 */
public class UmsServiceUnavailableException extends IllegalStateException {

    public UmsServiceUnavailableException(String message) {
        super(message);
    }

    public UmsServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
