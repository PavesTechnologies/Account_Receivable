package com.AccountReceivableManagement.controller.tax_calculation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.tax_calculation.TaxCalculationResponseDto;
import com.AccountReceivableManagement.service_interface.tax_calculation.TaxCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing-snapshots/{snapshotId}/tax-calculation")
@RequiredArgsConstructor
public class TaxCalculationController {

    private final TaxCalculationService taxCalculationService;

    @PostMapping
    public ResponseEntity<
            ApiResponse<TaxCalculationResponseDto>
            > calculateTax(
            @PathVariable UUID snapshotId
    ) {

        TaxCalculationResponseDto response =
                taxCalculationService.calculateTax(
                        snapshotId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse
                                .<TaxCalculationResponseDto>builder()
                                .success(true)
                                .message(
                                        "Tax calculation completed successfully."
                                )
                                .data(response)
                                .build()
                );
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<TaxCalculationResponseDto>
            > getTaxCalculation(
            @PathVariable UUID snapshotId
    ) {

        return ResponseEntity.ok(
                ApiResponse
                        .<TaxCalculationResponseDto>builder()
                        .success(true)
                        .message(
                                "Tax calculation retrieved successfully."
                        )
                        .data(
                                taxCalculationService
                                        .getTaxCalculationBySnapshotId(
                                                snapshotId
                                        )
                        )
                        .build()
        );
    }
}
