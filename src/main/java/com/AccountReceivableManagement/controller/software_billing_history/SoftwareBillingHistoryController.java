package com.AccountReceivableManagement.controller.software_billing_history;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.software_billing_history.SoftwareBillingHistoryResponseDto;
import com.AccountReceivableManagement.service_interface.software_billing_history.SoftwareBillingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/software-billing-history")
@RequiredArgsConstructor
public class SoftwareBillingHistoryController {

    private final SoftwareBillingHistoryService softwareBillingHistoryService;

    @GetMapping("/assets/{assetId}")
    public ResponseEntity<ApiResponse<List<SoftwareBillingHistoryResponseDto>>> getHistoryForAsset(
            @PathVariable UUID assetId) {

        List<SoftwareBillingHistoryResponseDto> response =
                softwareBillingHistoryService.getHistoryForAsset(assetId);

        return ResponseEntity.ok(
                ApiResponse.<List<SoftwareBillingHistoryResponseDto>>builder()
                        .success(true)
                        .message("Software billing history retrieved successfully.")
                        .data(response)
                        .build());
    }
}
