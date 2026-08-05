package com.AccountReceivableManagement.service_interface.client;

import com.AccountReceivableManagement.dto.client.ClientBudgetSummaryResponseDto;

import java.util.UUID;

public interface ClientBudgetSummaryService {

    void refreshClientBudget(UUID clientId);

    ClientBudgetSummaryResponseDto getClientBudget(UUID clientId);
}
