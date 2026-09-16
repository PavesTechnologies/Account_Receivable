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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionSingleOccurrenceToTaxPending(BillingSchedule schedule) {
        if (schedule.getPeriodStatus() == BillingPeriodStatus.SCHEDULED) {
            schedule.setPeriodStatus(BillingPeriodStatus.TAX_PENDING);
            schedule.setTaxStatus(BillingPeriodStatus.TAX_PENDING);
            billingScheduleRepository.save(schedule);
            log.info("Transitioned occurrence {} to TAX_PENDING", schedule.getBillingScheduleId());
        }
    }
}
