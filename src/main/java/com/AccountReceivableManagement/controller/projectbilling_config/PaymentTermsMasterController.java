package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.PaymentTermsRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.PaymentTermsResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.PaymentTermsMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment-terms")
@RequiredArgsConstructor
public class PaymentTermsMasterController {

    private final PaymentTermsMasterService paymentTermsService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentTermsResponseDto>> createPaymentTerm(
            @Valid @RequestBody PaymentTermsRequestDto request) {

        PaymentTermsResponseDto response = paymentTermsService.createPaymentTerm(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PaymentTermsResponseDto>builder()
                        .success(true)
                        .message("Payment Term created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{paymentTermId}")
    public ResponseEntity<ApiResponse<PaymentTermsResponseDto>> updatePaymentTerm(
            @PathVariable UUID paymentTermId,
            @Valid @RequestBody PaymentTermsRequestDto request) {

        PaymentTermsResponseDto response =
                paymentTermsService.updatePaymentTerm(paymentTermId, request);

        return ResponseEntity.ok(
                ApiResponse.<PaymentTermsResponseDto>builder()
                        .success(true)
                        .message("Payment Term updated successfully.")
                        .data(response)
                        .build()
        );
    }


    @GetMapping("/{paymentTermId}")
    public ResponseEntity<ApiResponse<PaymentTermsResponseDto>> getPaymentTermById(
            @PathVariable UUID paymentTermId) {

        PaymentTermsResponseDto response =
                paymentTermsService.getPaymentTermById(paymentTermId);

        return ResponseEntity.ok(
                ApiResponse.<PaymentTermsResponseDto>builder()
                        .success(true)
                        .message("Payment Term retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentTermsResponseDto>>> getAllPaymentTerms() {

        List<PaymentTermsResponseDto> response =
                paymentTermsService.getAllPaymentTerms();

        return ResponseEntity.ok(
                ApiResponse.<List<PaymentTermsResponseDto>>builder()
                        .success(true)
                        .message("Payment Terms retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PaymentTermsResponseDto>>> getActivePaymentTerms() {

        List<PaymentTermsResponseDto> response =
                paymentTermsService.getActivePaymentTerms();

        return ResponseEntity.ok(
                ApiResponse.<List<PaymentTermsResponseDto>>builder()
                        .success(true)
                        .message("Active Payment Terms retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping("/{paymentTermId}")
    public ResponseEntity<ApiResponse<Void>> deletePaymentTerm(
            @PathVariable UUID paymentTermId) {

        paymentTermsService.deletePaymentTerm(paymentTermId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Payment Term deleted successfully.")
                        .data(null)
                        .build()
        );
    }

}
