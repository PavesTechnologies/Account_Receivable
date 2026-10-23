package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
// Defaults to enabled; only the local-demo profile switches it off.
@ConditionalOnProperty(name = "ar.billing.status-schedulers.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class BillingOccurrenceStatusScheduler {

    private final BillingOccurrenceServiceImpl billingOccurrenceService;

//    @Scheduled(cron = "0 0 0 * * *")
    @Scheduled(cron = "0 * * * * *")
    public void transitionScheduledToTaxPending() {
        log.info("Starting scheduled transition of SCHEDULED occurrences to TAX_PENDING");
        try {
            billingOccurrenceService.transitionScheduledToTaxPending();
            log.info("Completed scheduled transition of SCHEDULED occurrences to TAX_PENDING");
        } catch (Exception e) {
            log.error("Fatal error during scheduled transition of SCHEDULED occurrences to TAX_PENDING", e);
            // This catch is for unexpected system-level errors (database connection, etc.)
            // Individual occurrence failures are handled within the service method
        }
    }
}
