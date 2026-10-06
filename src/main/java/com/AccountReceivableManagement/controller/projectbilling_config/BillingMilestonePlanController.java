package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.BillingMilestonePlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing-milestone-plan")
@RequiredArgsConstructor
public class BillingMilestonePlanController {

    private final BillingMilestonePlanService billingMilestonePlanService;

    @PostMapping("/{billingConfigurationId}/milestone-plan")
    public ResponseEntity<ApiResponse<BillingMilestonePlanResponseDto>> create(
            @PathVariable UUID billingConfigurationId,
            @Valid @RequestBody BillingMilestonePlanRequestDto request) {

        BillingMilestonePlanResponseDto response =
                billingMilestonePlanService.create(
                        billingConfigurationId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.<BillingMilestonePlanResponseDto>builder()
                                .success(true)
                                .message(
                                        "Milestone Plan configuration created successfully."
                                )
                                .data(response)
                                .build()
                );
    }

    @PutMapping("/milestone-plan/{milestonePlanId}")
    public ResponseEntity<ApiResponse<BillingMilestonePlanResponseDto>> update(
            @PathVariable UUID milestonePlanId,
            @Valid @RequestBody BillingMilestonePlanRequestDto request) {

        BillingMilestonePlanResponseDto response =
                billingMilestonePlanService.update(
                        milestonePlanId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.<BillingMilestonePlanResponseDto>builder()
                        .success(true)
                        .message(
                                "Milestone Plan configuration updated successfully."
                        )
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/milestone-plan/{milestonePlanId}")
    public ResponseEntity<ApiResponse<BillingMilestonePlanResponseDto>> get(
            @PathVariable UUID milestonePlanId) {

        BillingMilestonePlanResponseDto response =
                billingMilestonePlanService.get(
                        milestonePlanId
                );

        return ResponseEntity.ok(
                ApiResponse.<BillingMilestonePlanResponseDto>builder()
                        .success(true)
                        .message(
                                "Milestone Plan configuration fetched successfully."
                        )
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{billingConfigurationId}/milestone-plan")
    public ResponseEntity<ApiResponse<BillingMilestonePlanResponseDto>> getByBillingConfiguration(
            @PathVariable UUID billingConfigurationId) {

        BillingMilestonePlanResponseDto response =
                billingMilestonePlanService.getByBillingConfiguration(
                        billingConfigurationId
                );

        return ResponseEntity.ok(
                ApiResponse.<BillingMilestonePlanResponseDto>builder()
                        .success(true)
                        .message(
                                "Milestone Plan configuration fetched successfully."
                        )
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping("/milestone-plan/{milestonePlanId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID milestonePlanId) {

        billingMilestonePlanService.delete(
                milestonePlanId
        );

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message(
                                "Milestone Plan configuration deleted successfully."
                        )
                        .build()
        );
    }

}
