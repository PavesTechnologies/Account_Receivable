package com.AccountReceivableManagement.integration.billing_data_acquisition;

/**
 * TMS answered successfully, but the data cannot be verified as belonging to
 * the requested project and billing period (another project, another period,
 * a timesheet dated outside it, or no body at all). Nothing is billed from
 * it, and it is not retried automatically: the same request would get the
 * same answer. Extends {@link IllegalArgumentException}, which automatic
 * billing treats as a permanent data failure.
 * <p>
 * Messages name identifiers only - never employee names or response bodies.
 */
public class TmsInvalidResponseException extends IllegalArgumentException {

    public TmsInvalidResponseException(String message) {
        super(message);
    }
}
