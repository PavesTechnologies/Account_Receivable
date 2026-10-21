package com.AccountReceivableManagement.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code ar.security.*}: who may take financial decisions, and the JWT
 * contract beyond issuer and signature.
 * <p>
 * The UMS role and permission names are not defined anywhere in this
 * repository, so none is assumed: each list names the JWT authorities -
 * {@code ROLE_<UMS role>} (from the {@code roles} claim) or a permission
 * (from the {@code permissions} claim) - and an empty list grants nobody.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ar.security")
public class FinancialSecurityProperties {

    @Valid
    @NotNull
    private Authorization authorization = new Authorization();

    @Valid
    @NotNull
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Authorization {

        /** May approve or reject billing configurations. Empty = nobody. */
        @NotNull
        private List<String> configurationApprovalAuthorities = new ArrayList<>();

        /** May approve or reject invoices and finalize their documents. Empty = nobody. */
        @NotNull
        private List<String> invoiceApprovalAuthorities = new ArrayList<>();

        /** May send approved invoices to clients. Empty = nobody. */
        @NotNull
        private List<String> invoiceDeliveryAuthorities = new ArrayList<>();

        /**
         * May perform operational recovery (re-queue billing runs, reconcile
         * runs, receivables and deliveries). Empty = nobody.
         */
        @NotNull
        private List<String> operationsRecoveryAuthorities = new ArrayList<>();

        /**
         * Maker-checker: the user who submitted a billing configuration or an
         * invoice for approval cannot approve it. Records submitted by the
         * system (automatic billing) are exempt - there is no human maker.
         */
        private boolean makerChecker = true;
    }

    @Getter
    @Setter
    public static class Jwt {

        /**
         * Accepted {@code aud} values. Empty = the audience is not validated:
         * no UMS audience contract is documented yet. Set it once UMS defines
         * the audience of tokens issued for AR.
         */
        @NotNull
        private List<String> audiences = new ArrayList<>();
    }
}
