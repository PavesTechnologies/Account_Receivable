package com.AccountReceivableManagement.service_Imple.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingConfigurationStatus;
import com.AccountReceivableManagement.repo.projectbilling_config.BillingConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BillingConfigurationStatusScheduler {

    private final BillingConfigurationRepository repository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void updateBillingConfigurationStatuses() {

        LocalDate today = LocalDate.now();

        List<BillingConfiguration> configurations =
                repository.findByApprovalStatus(
                        ApprovalStatus.APPROVED
                );

        for (BillingConfiguration config : configurations) {

            // IMPORTANT:
            // Do not automatically reactivate manually deactivated records.
            if (Boolean.TRUE.equals(
                    config.getManuallyDeactivated())) {

                continue;
            }

            BillingConfigurationStatus newStatus;

            if (config.getEffectiveFrom() == null) {

                newStatus =
                        BillingConfigurationStatus.INACTIVE;

            } else if (today.isBefore(
                    config.getEffectiveFrom())) {

                // Project/billing period has not started yet
                newStatus =
                        BillingConfigurationStatus.INACTIVE;

            } else if (config.getEffectiveTo() != null
                    && today.isAfter(
                    config.getEffectiveTo())) {

                // Billing period has ended
                newStatus =
                        BillingConfigurationStatus.EXPIRED;

            } else {

                // Currently within effective period
                newStatus =
                        BillingConfigurationStatus.ACTIVE;
            }

            if (config.getBillingStatus() != newStatus) {

                config.setBillingStatus(newStatus);
                config.setUpdatedAt(LocalDateTime.now());

                repository.save(config);
            }
        }
    }
}
