package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing-configurations")
@RequiredArgsConstructor

public class BillingConfigurationController {
    private final BillingConfigurationService billingConfigurationService;

    @PostMapping
    public ResponseEntity<ApiResponse<BillingConfigurationResponseDto>> create(
            @Valid @RequestBody BillingConfigurationRequestDto request) {

        BillingConfigurationResponseDto response =
                billingConfigurationService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingConfigurationResponseDto>builder()
                        .success(true)
                        .message("Billing configuration created successfully.")
                        .data(response)
                        .build());
    }
}
