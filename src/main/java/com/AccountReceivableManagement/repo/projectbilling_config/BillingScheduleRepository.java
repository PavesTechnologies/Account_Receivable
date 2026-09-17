package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.BillingConfiguration;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingSchedule;
import com.AccountReceivableManagement.entity.projectbilling_config.BillingRecurringConfiguration;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingPeriodStatus;
import com.AccountReceivableManagement.entity_enums.projectbilling_config.BillingScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingScheduleRepository extends JpaRepository<BillingSchedule, UUID> {

    List<BillingSchedule> findByBillingConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
            BillingConfiguration billingConfiguration);

    List<BillingSchedule> findByRecurringConfigurationAndIsActiveTrueOrderByPeriodNumberAsc(
            BillingRecurringConfiguration recurringConfiguration);

    List<BillingSchedule> findByBillingConfigurationAndScheduleTypeAndIsActiveTrueOrderByPeriodNumberAsc(
            BillingConfiguration billingConfiguration,
            BillingScheduleType scheduleType);

    List<BillingSchedule> findByRecurringConfigurationAndScheduleTypeAndIsActiveTrueOrderByPeriodNumberAsc(
            BillingRecurringConfiguration recurringConfiguration,
            BillingScheduleType scheduleType);

    Optional<BillingSchedule> findByBillingConfigurationAndPeriodNumberAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            Integer periodNumber);

    Optional<BillingSchedule> findByRecurringConfigurationAndPeriodNumberAndIsActiveTrue(
            BillingRecurringConfiguration recurringConfiguration,
            Integer periodNumber);

    List<BillingSchedule> findByBillingConfigurationAndPeriodStatusAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            BillingPeriodStatus periodStatus);

    List<BillingSchedule> findByRecurringConfigurationAndPeriodStatusAndIsActiveTrue(
            BillingRecurringConfiguration recurringConfiguration,
            BillingPeriodStatus periodStatus);

    boolean existsByBillingConfigurationAndPeriodNumberAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            Integer periodNumber);

    boolean existsByRecurringConfigurationAndPeriodNumberAndIsActiveTrue(
            BillingRecurringConfiguration recurringConfiguration,
            Integer periodNumber);

    void deleteByBillingConfiguration(BillingConfiguration billingConfiguration);

    void deleteByRecurringConfiguration(BillingRecurringConfiguration recurringConfiguration);

    List<BillingSchedule> findByTaxStatusAndIsActiveTrue(BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByPeriodStatusAndIsActiveTrue(BillingPeriodStatus periodStatus);

    List<BillingSchedule> findByPeriodStatusAndTaxStatusAndIsActiveTrue(
            BillingPeriodStatus periodStatus,
            BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByBillingConfigurationAndTaxStatusAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByRecurringConfigurationAndTaxStatusAndIsActiveTrue(
            BillingRecurringConfiguration recurringConfiguration,
            BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByBillingDateBeforeAndTaxStatusAndIsActiveTrue(
            LocalDate billingDate,
            BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByBillingDateLessThanEqualAndTaxStatusAndIsActiveTrue(
            LocalDate billingDate,
            BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByBillingConfigurationAndPeriodStatusAndTaxStatusAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            BillingPeriodStatus periodStatus,
            BillingPeriodStatus taxStatus);

    List<BillingSchedule> findByRecurringConfigurationAndPeriodStatusAndTaxStatusAndIsActiveTrue(
            BillingRecurringConfiguration recurringConfiguration,
            BillingPeriodStatus periodStatus,
            BillingPeriodStatus taxStatus);

    boolean existsByBillingConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
            BillingConfiguration billingConfiguration,
            LocalDate periodStartDate,
            LocalDate periodEndDate);

    boolean existsByRecurringConfigurationAndPeriodStartDateAndPeriodEndDateAndIsActiveTrue(
            BillingRecurringConfiguration recurringConfiguration,
            LocalDate periodStartDate,
            LocalDate periodEndDate);

}
