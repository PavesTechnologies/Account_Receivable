package com.AccountReceivableManagement.repo.projectbilling_config;

import com.AccountReceivableManagement.entity.projectbilling_config.PaymentTermsMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTermsMasterRepository extends JpaRepository<PaymentTermsMaster, UUID> {

    Optional<PaymentTermsMaster> findByPaymentTermNameIgnoreCase(String paymentTermName);

    List<PaymentTermsMaster> findByIsActiveTrueOrderByPaymentDaysAsc();

    boolean existsByPaymentTermNameIgnoreCase(String paymentTermName);
}
