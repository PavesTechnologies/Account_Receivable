package com.AccountReceivableManagement.entity_enums.projectbilling_config;

/**
 * Which occurrences {@code GET /api/billing-occurrences} returns, by the
 * lifecycle of their owning billing configuration. Manual deactivation retires
 * a configuration's SCHEDULED and TAX_PENDING occurrences ({@code isActive =
 * false}) but keeps its TAX_CALCULATED and INVOICED ones active as financial
 * history, so without a view they are listed next to the occurrences of the
 * configuration that replaced it.
 */
public enum BillingOccurrenceView {

    /** Every active occurrence, whatever its configuration's state - the behaviour when no view is given. */
    ALL,

    /** Occurrences of configurations that are not manually deactivated: the operational pipeline. */
    CURRENT,

    /** Occurrences kept from manually deactivated configurations: their history and any unbilled leftovers. */
    HISTORICAL
}
