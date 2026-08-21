package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingTMRateCardResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingTMRateCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-tm-rate-card")
@RequiredArgsConstructor
public class BillingTMRateCardController {
    private final BillingTMRateCardService billingTMRateCardService;

    @PostMapping("/{billingConfigurationId}/tm-rate-cards")
    public ResponseEntity<ApiResponse<BillingTMRateCardResponseDto>> addRateCard(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingTMRateCardRequestDto request) {

        BillingTMRateCardResponseDto response =
                billingTMRateCardService.addRateCard(
                        billingConfigurationId,
                        request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingTMRateCardResponseDto>builder()
                        .success(true)
                        .message("Time & Material Rate Card created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/tm-rate-cards/{rateCardId}")
    public ResponseEntity<ApiResponse<BillingTMRateCardResponseDto>> updateRateCard(
            @PathVariable UUID rateCardId,
            @Valid @RequestBody BillingTMRateCardRequestDto request) {

        BillingTMRateCardResponseDto response =
                billingTMRateCardService.updateRateCard(
                        rateCardId,
                        request);

        return ResponseEntity.ok(
                ApiResponse.<BillingTMRateCardResponseDto>builder()
                        .success(true)
                        .message("Time & Material Rate Card updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{billingConfigurationId}/tm-rate-cards")
    public ResponseEntity<ApiResponse<List<BillingTMRateCardResponseDto>>> getAllRateCards(
            @PathVariable UUID billingConfigurationId) {

        List<BillingTMRateCardResponseDto> response =
                billingTMRateCardService.getAllRateCards(
                        billingConfigurationId);

        return ResponseEntity.ok(
                ApiResponse.<List<BillingTMRateCardResponseDto>>builder()
                        .success(true)
                        .message("Time & Material Rate Cards fetched successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/tm-rate-cards/{rateCardId}")
    public ResponseEntity<ApiResponse<BillingTMRateCardResponseDto>> getRateCard(
            @PathVariable UUID rateCardId) {

        BillingTMRateCardResponseDto response =
                billingTMRateCardService.getRateCard(rateCardId);

        return ResponseEntity.ok(
                ApiResponse.<BillingTMRateCardResponseDto>builder()
                        .success(true)
                        .message("Time & Material Rate Card fetched successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/tm-rate-cards/{rateCardId}")
    public ResponseEntity<ApiResponse<Void>> deleteRateCard(
            @PathVariable UUID rateCardId) {

        billingTMRateCardService.deleteRateCard(rateCardId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Time & Material Rate Card deleted successfully.")
                        .build());
    }

    @PostMapping("/{billingConfigurationId}/tm-rate-cards/save")
    public ResponseEntity<ApiResponse<BillingTMRateCardResponseDto>> saveRateCard(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingTMRateCardRequestDto request) {

        BillingTMRateCardResponseDto response =
                billingTMRateCardService.saveRateCard(
                        billingConfigurationId,
                        request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingTMRateCardResponseDto>builder()
                        .success(true)
                        .message("Rate Card saved successfully.")
                        .data(response)
                        .build());
    }
}
