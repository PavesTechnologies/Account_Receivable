package com.AccountReceivableManagement.global_exception_handler;

/**
 * The caller no longer owns the automatic billing run it tried to update -
 * its claim expired and another worker took the run over, or the run already
 * left PROCESSING. The caller must stop working on the run; it is not a
 * failure of the run itself.
 */
public class AutomaticBillingRunClaimLostException extends RuntimeException {
    public AutomaticBillingRunClaimLostException(String message) {
        super(message);
    }
}
