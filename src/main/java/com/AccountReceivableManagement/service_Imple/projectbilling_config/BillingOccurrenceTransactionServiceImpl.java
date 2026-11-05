package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingScheduleRepository;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingOccurrenceTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Implementation of BillingOccurrenceTransactionService.
 * Each transition runs in its own transaction (REQUIRES_NEW) to ensure
 * that failures in one occurrence don't affect others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingOccurrenceTransactionServiceImpl implements BillingOccurrenceTransactionService {

    private final BillingScheduleRepository billingScheduleRepository;

    /**
     * The passed occurrence was read outside this transaction and may be
     * stale - changed, deactivated or deleted (configuration reconciliation)
     * since, or already transitioned by an overlapping run. It is therefore
     * only used for its id: the current row is re-read under a row lock and
     * transitioned only if it is still active, SCHEDULED, tax PENDING and
     * due. Saving the stale copy would overwrite concurrent changes or
     * resurrect a deleted occurrence; a repeated or concurrent run now
     * finds the row already TAX_PENDING and changes nothing.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionSingleOccurrenceToTaxPending(BillingSchedule schedule) {
        BillingSchedule current = billingScheduleRepository
                .findByIdForUpdate(schedule.getBillingScheduleId())
                .orElse(null);

        if (current == null) {
            log.info("Occurrence {} no longer exists; not transitioned to TAX_PENDING",
                    schedule.getBillingScheduleId());
            return;
        }

        if (!Boolean.TRUE.equals(current.getIsActive())
                || current.getPeriodStatus() != BillingPeriodStatus.SCHEDULED
                || current.getTaxStatus() != BillingPeriodStatus.PENDING
                || current.getBillingDate() == null
                || current.getBillingDate().isAfter(LocalDate.now())) {
            log.debug("Occurrence {} is no longer a due SCHEDULED occurrence (active={}, status={}, tax={}); unchanged",
                    current.getBillingScheduleId(), current.getIsActive(), current.getPeriodStatus(),
                    current.getTaxStatus());
            return;
        }

        current.setPeriodStatus(BillingPeriodStatus.TAX_PENDING);
        current.setTaxStatus(BillingPeriodStatus.TAX_PENDING);
        billingScheduleRepository.save(current);
        log.info("[BillingOccurrenceTransition] occurrence={} configuration={} period={} billingDate={} "
                        + "SCHEDULED -> TAX_PENDING",
                current.getBillingScheduleId(),
                current.getBillingConfiguration() != null
                        ? current.getBillingConfiguration().getBillingConfigurationId() : null,
                current.getPeriodNumber(), current.getBillingDate());
    }
}
