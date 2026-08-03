package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.CurrencyRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.CurrencyResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.CurrencyMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/currency")
@RequiredArgsConstructor
public class CurrencyMasterController {
    private final CurrencyMasterService currencyService;

    @PostMapping
    public ResponseEntity<ApiResponse<CurrencyResponseDto>> createCurrency(
            @Valid @RequestBody CurrencyRequestDto request) {

        CurrencyResponseDto response = currencyService.createCurrency(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CurrencyResponseDto>builder()
                        .success(true)
                        .message("Currency created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{currencyId}")
    public ResponseEntity<CurrencyResponseDto> updateCurrency(
            @PathVariable UUID currencyId,
            @Valid @RequestBody CurrencyRequestDto request) {

        return ResponseEntity.ok(
                currencyService.updateCurrency(currencyId, request)
        );
    }

    @GetMapping("/{currencyId}")
    public ResponseEntity<ApiResponse<CurrencyResponseDto>> getCurrencyById(
            @PathVariable UUID currencyId) {

        CurrencyResponseDto response = currencyService.getCurrencyById(currencyId);

        return ResponseEntity.ok(
                ApiResponse.<CurrencyResponseDto>builder()
                        .success(true)
                        .message("Currency retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CurrencyResponseDto>>> getAllCurrencies() {

        List<CurrencyResponseDto> response = currencyService.getAllCurrencies();

        return ResponseEntity.ok(
                ApiResponse.<List<CurrencyResponseDto>>builder()
                        .success(true)
                        .message("Currencies retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/active")
    public ResponseEntity<List<CurrencyResponseDto>> getActiveCurrencies() {

        return ResponseEntity.ok(
                currencyService.getActiveCurrencies()
        );
    }

    @DeleteMapping("/{currencyId}")
    public ResponseEntity<ApiResponse<Object>> deleteCurrency(
            @PathVariable UUID currencyId) {

        currencyService.deleteCurrency(currencyId);

        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Currency deleted successfully.")
                        .data(null)
                        .build()
        );
    }
}
