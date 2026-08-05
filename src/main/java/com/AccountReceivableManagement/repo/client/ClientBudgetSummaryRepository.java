package com.AccountReceivableManagement.repo.client;

import com.AccountReceivableManagement.entity.client_entity.ClientBudgetSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientBudgetSummaryRepository extends JpaRepository<ClientBudgetSummary, UUID> {

    Optional<ClientBudgetSummary>
    findByClient_ClientId(UUID clientId);

    boolean existsByClient_ClientId(UUID clientId);
}
