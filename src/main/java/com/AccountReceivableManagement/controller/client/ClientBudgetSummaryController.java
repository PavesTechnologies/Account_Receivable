package com.AccountReceivableManagement.controller.client;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.client.ClientBudgetSummaryResponseDto;
import com.AccountReceivableManagement.service_interface.client.ClientBudgetSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.AccountReceivableManagement.security.ExecutiveOnly;

@RestController
@RequestMapping("/client-budget-summary")
@RequiredArgsConstructor
public class ClientBudgetSummaryController {

    private final ClientBudgetSummaryService clientBudgetSummaryService;
    @ExecutiveOnly
    @GetMapping("/{clientId}")
    public ResponseEntity<ApiResponse<ClientBudgetSummaryResponseDto>> getClientBudget(
            @PathVariable UUID clientId) {

        ClientBudgetSummaryResponseDto response =
                clientBudgetSummaryService.getClientBudget(clientId);

        return ResponseEntity.ok(
                ApiResponse.<ClientBudgetSummaryResponseDto>builder()
                        .success(true)
                        .message("Client budget summary fetched successfully.")
                        .data(response)
                        .build());
    }

    @ExecutiveOnly
    @PostMapping("/refresh/{clientId}")
    public ResponseEntity<ApiResponse<String>> refreshBudget(
            @PathVariable UUID clientId) {

        clientBudgetSummaryService.refreshClientBudget(clientId);

        return ResponseEntity.ok(
                ApiResponse.<String>builder()
                        .success(true)
                        .message("Client budget refreshed successfully.")
                        .data("Refresh completed.")
                        .build());
    }
}
