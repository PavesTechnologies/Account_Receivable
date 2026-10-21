package com.AccountReceivableManagement.integration.billing_data_acquisition;

/**
 * TMS answered with a server error (5xx): a temporary condition on the TMS
 * side, so automatic billing may retry the acquisition later. Extends
 * {@link IllegalStateException} - what this failure was reported as before -
 * so existing handling is unchanged.
 */
public class TmsServiceUnavailableException extends IllegalStateException {

    public TmsServiceUnavailableException(String message) {
        super(message);
    }
}
