package com.AccountReceivableManagement.repo.automatic_billing;

import com.AccountReceivableManagement.entity.automatic_billing.AutomaticBillingRunEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AutomaticBillingRunEventRepository extends JpaRepository<AutomaticBillingRunEvent, UUID> {

    /** A run's operational history, oldest first. */
    List<AutomaticBillingRunEvent> findByRunIdOrderByOccurredAtAsc(UUID runId);
}
