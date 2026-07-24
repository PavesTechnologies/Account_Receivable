//package com.AccountReceivableManagement.Repo.billing_config;
//
//import com.AccountReceivableManagement.Entity.billing_config.Billing_Configuration;
//import com.AccountReceivableManagement.Entity_Enums.billing_config.BillingConfigurationStatus;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.UUID;
//
//public interface BillingConfigurationRepository extends JpaRepository<Billing_Configuration, UUID> {
//    boolean existsByProjectIdAndStatus(
//            UUID projectId,
//            BillingConfigurationStatus status);
//
//    Optional<BillingConfiguration> findByProjectIdAndStatus(
//            UUID projectId,
//            BillingConfigurationStatus status);
//}
