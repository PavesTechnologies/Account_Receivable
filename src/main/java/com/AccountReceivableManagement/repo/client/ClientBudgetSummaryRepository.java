package com.AccountReceivableManagement.repo.client;

import com.AccountReceivableManagement.entity.client_entity.ClientBudgetSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientBudgetSummaryRepository extends JpaRepository<ClientBudgetSummary, UUID> {

    List<ClientBudgetSummary> findByClient_ClientId(UUID clientId);

    Optional<ClientBudgetSummary> findByClient_ClientIdAndCurrency(UUID clientId, String currency);

    void deleteByClient_ClientIdAndCurrency(UUID clientId, String currency);

    boolean existsByClient_ClientIdAndCurrency(UUID clientId, String currency);
}
