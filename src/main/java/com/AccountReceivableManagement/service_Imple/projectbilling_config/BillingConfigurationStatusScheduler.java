package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingConfigurationStatusScheduler {

    private final BillingConfigurationRepository repository;

//    @Scheduled(cron = "0 0 0 * * *")
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void updateBillingConfigurationStatuses() {

        LocalDate today = LocalDate.now();

        List<BillingConfiguration> configurations =
                repository.findByApprovalStatus(
                        ApprovalStatus.APPROVED
                );

        for (BillingConfiguration config : configurations) {

            /*
             * Do not automatically reactivate manually deactivated records.
             */
            if (Boolean.TRUE.equals(
                    config.getManuallyDeactivated())) {

                continue;
            }

            BillingConfigurationStatus newStatus;

            /*
             * Project duration has ended.
             *
             * Project end date is the source of truth for determining
             * whether the approved billing configuration has expired.
             */
            if (config.getProject() != null
                    && config.getProject().getEndDate() != null
                    && today.isAfter(
                    config.getProject().getEndDate())) {

                newStatus =
                        BillingConfigurationStatus.EXPIRED;

            } else if (config.getEffectiveFrom() == null) {

                newStatus =
                        BillingConfigurationStatus.INACTIVE;

            } else if (today.isBefore(
                    config.getEffectiveFrom())) {

                /*
                 * Billing configuration has not started yet.
                 */
                newStatus =
                        BillingConfigurationStatus.INACTIVE;

            } else if (config.getEffectiveTo() != null
                    && today.isAfter(
                    config.getEffectiveTo())) {

                /*
                 * Billing configuration's own effective period has ended.
                 */
                newStatus =
                        BillingConfigurationStatus.EXPIRED;

            } else {

                /*
                 * Currently within the valid billing period.
                 */
                newStatus =
                        BillingConfigurationStatus.ACTIVE;
            }

            /*
             * Update only when the status actually changes.
             */
            if (config.getBillingStatus() != newStatus) {

                log.info(
                        "Updating billing configuration {} status from {} to {}. " +
                                "Project: {}, Project End Date: {}, Today: {}",
                        config.getBillingConfigurationId(),
                        config.getBillingStatus(),
                        newStatus,
                        config.getProject() != null
                                ? config.getProject().getProjectName()
                                : null,
                        config.getProject() != null
                                ? config.getProject().getEndDate()
                                : null,
                        today
                );

                config.setBillingStatus(newStatus);
                config.setUpdatedAt(LocalDateTime.now());

                repository.save(config);
            }
        }
    }
}
