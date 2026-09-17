package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;

/**
 * Service for handling individual billing schedule transitions with transaction isolation.
 * Each operation runs in its own transaction to ensure failures don't affect other occurrences.
 */
public interface BillingOccurrenceTransactionService {

    /**
     * Transitions a single billing schedule from SCHEDULED to TAX_PENDING in a new transaction.
     * 
     * @param schedule The billing schedule to transition
     */
    void transitionSingleOccurrenceToTaxPending(BillingSchedule schedule);
}
