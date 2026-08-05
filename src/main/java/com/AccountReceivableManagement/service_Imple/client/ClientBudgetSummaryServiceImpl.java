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
                    .ifPresent(clientBudgetSummaryRepository::delete);

            return;
        }
        BigDecimal totalBudget =
                projectMasterReferenceRepository.calculateTotalBudget(clientId);

        if (totalBudget == null) {
            totalBudget = BigDecimal.ZERO;
        }
        List<String> currencies =
                projectMasterReferenceRepository.getCurrencies(clientId);
        String currency = "N/A";

        if (!currencies.isEmpty()) {

            if (currencies.size() > 1) {
                throw new GlobalExceptionHandler.ValidationException("Projects under the same client contain multiple currencies.");
            } else {
                currency = currencies.get(0);
            }
        }
        ClientBudgetSummary summary =
                clientBudgetSummaryRepository.findByClient_ClientId(clientId)
                        .orElseGet(ClientBudgetSummary::new);
        summary.setClient(client);
        summary.setTotalBudget(totalBudget);
        summary.setCurrency(currency);
        summary.setLastCalculatedAt(LocalDateTime.now());
        clientBudgetSummaryRepository.save(summary);
        log.info("Client budget summary refreshed for client: {}", clientId);

    }

    @Override
    @Transactional(readOnly = true)
    public ClientBudgetSummaryResponseDto
    getClientBudget(UUID clientId) {
        ClientBudgetSummary summary =
                clientBudgetSummaryRepository
                        .findByClient_ClientId(clientId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Client Budget Summary not found."));
        long projectCount =
                projectMasterReferenceRepository.countByClientId(clientId);
        return ClientBudgetSummaryResponseDto.builder()

                .clientId(summary.getClient().getClientId())

                .clientName(summary.getClient().getClientName())

                .totalBudget(summary.getTotalBudget())

                .currency(summary.getCurrency())

                .totalProjects(projectCount)

                .lastCalculatedAt(summary.getLastCalculatedAt())

                .build();
    }

}
