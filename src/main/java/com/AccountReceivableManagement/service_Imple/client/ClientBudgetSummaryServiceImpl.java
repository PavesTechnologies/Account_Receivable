package com.AccountReceivableManagement.service_Imple.client;

import com.AccountReceivableManagement.dto.client.ClientBudgetSummaryResponseDto;
import com.AccountReceivableManagement.entity.client_entity.Client;
import com.AccountReceivableManagement.entity.client_entity.ClientBudgetSummary;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.client.ClientBudgetSummaryRepository;
import com.AccountReceivableManagement.repo.client.ClientRepository;
import com.AccountReceivableManagement.repo.project.ProjectMasterReferenceRepository;
import com.AccountReceivableManagement.service_interface.client.ClientBudgetSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClientBudgetSummaryServiceImpl implements ClientBudgetSummaryService {

    private final ClientBudgetSummaryRepository clientBudgetSummaryRepository;
    private final ClientRepository clientRepository;
    private final ProjectMasterReferenceRepository projectMasterReferenceRepository;

    @Override
    public void refreshClientBudget(UUID clientId) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Client not found."));
        long projectCount = projectMasterReferenceRepository.countByClientId(clientId);

        if (projectCount == 0) {
            clientBudgetSummaryRepository
                    .findByClient_ClientId(clientId)
                    .forEach(clientBudgetSummaryRepository::delete);
            return;
        }

        List<Object[]> budgetSummaries = 
                projectMasterReferenceRepository.getBudgetSummaryByCurrency(clientId);

        for (Object[] row : budgetSummaries) {
            String currency = (String) row[0];
            BigDecimal totalBudget = (BigDecimal) row[1];
            Long currencyProjectCount = ((Number) row[2]).longValue();

            if (totalBudget == null) {
                totalBudget = BigDecimal.ZERO;
            }

            ClientBudgetSummary summary =
                    clientBudgetSummaryRepository.findByClient_ClientIdAndCurrency(clientId, currency)
                            .orElseGet(ClientBudgetSummary::new);
            summary.setClient(client);
            summary.setCurrency(currency);
            summary.setTotalBudget(totalBudget);
            summary.setProjectCount(currencyProjectCount);
            summary.setLastCalculatedAt(LocalDateTime.now());
            clientBudgetSummaryRepository.save(summary);
        }

        List<ClientBudgetSummary> existingSummaries = 
                clientBudgetSummaryRepository.findByClient_ClientId(clientId);
        for (ClientBudgetSummary existing : existingSummaries) {
            boolean currencyStillExists = budgetSummaries.stream()
                    .anyMatch(row -> row[0].equals(existing.getCurrency()));
            if (!currencyStillExists) {
                clientBudgetSummaryRepository.delete(existing);
            }
        }

        log.info("Client budget summary refreshed for client: {} with {} currency summaries", 
                clientId, budgetSummaries.size());

    }

    @Override
    @Transactional(readOnly = true)
    public ClientBudgetSummaryResponseDto
    getClientBudget(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new GlobalExceptionHandler.ResourceNotFoundException("Client not found."));

        List<ClientBudgetSummary> summaries =
                clientBudgetSummaryRepository.findByClient_ClientId(clientId);

        if (summaries.isEmpty()) {
            throw new GlobalExceptionHandler.ResourceNotFoundException(
                    "Client Budget Summary not found.");
        }

        List<ClientBudgetSummaryResponseDto.CurrencyBudget> currencyBudgets = summaries.stream()
                .map(summary -> ClientBudgetSummaryResponseDto.CurrencyBudget.builder()
                        .currency(summary.getCurrency())
                        .totalProjectBudget(summary.getTotalBudget())
                        .projectCount(summary.getProjectCount())
                        .build())
                .toList();

        return ClientBudgetSummaryResponseDto.builder()
                .clientId(client.getClientId())
                .clientName(client.getClientName())
                .budgets(currencyBudgets)
                .lastCalculatedAt(summaries.get(0).getLastCalculatedAt())
                .build();
    }

}
