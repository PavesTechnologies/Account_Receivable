package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.projectbilling_config.ProrationRuleRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ProrationRuleResponseDto;
import com.AccountReceivableManagement.service_interface.projectbilling_config.ProrationRuleMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proration-rule")
@RequiredArgsConstructor
public class ProrationRuleMasterController {
    private final ProrationRuleMasterService prorationRuleService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProrationRuleResponseDto>> createProrationRule(
            @Valid @RequestBody ProrationRuleRequestDto request) {

        ProrationRuleResponseDto response = prorationRuleService.createProrationRule(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProrationRuleResponseDto>builder()
                        .success(true)
                        .message("Proration Rule created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{prorationRuleId}")
    public ResponseEntity<ApiResponse<ProrationRuleResponseDto>> updateProrationRule(
            @PathVariable UUID prorationRuleId,
            @Valid @RequestBody ProrationRuleRequestDto request) {

        ProrationRuleResponseDto response =
                prorationRuleService.updateProrationRule(prorationRuleId, request);

        return ResponseEntity.ok(
                ApiResponse.<ProrationRuleResponseDto>builder()
                        .success(true)
                        .message("Proration Rule updated successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{prorationRuleId}")
    public ResponseEntity<ApiResponse<ProrationRuleResponseDto>> getProrationRuleById(
            @PathVariable UUID prorationRuleId) {

        ProrationRuleResponseDto response = prorationRuleService.getProrationRuleById(prorationRuleId);

        return ResponseEntity.ok(
                ApiResponse.<ProrationRuleResponseDto>builder()
                        .success(true)
                        .message("Proration Rule retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProrationRuleResponseDto>>> getAllProrationRules() {

        List<ProrationRuleResponseDto> response = prorationRuleService.getAllProrationRules();

        return ResponseEntity.ok(
                ApiResponse.<List<ProrationRuleResponseDto>>builder()
                        .success(true)
                        .message("Proration Rules retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ProrationRuleResponseDto>>> getActiveProrationRules() {

        List<ProrationRuleResponseDto> response = prorationRuleService.getActiveProrationRules();

        return ResponseEntity.ok(
                ApiResponse.<List<ProrationRuleResponseDto>>builder()
                        .success(true)
                        .message("Active Proration Rules retrieved successfully.")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping("/{prorationRuleId}")
    public ResponseEntity<ApiResponse<Object>> deleteProrationRule(
            @PathVariable UUID prorationRuleId) {

        prorationRuleService.deleteProrationRule(prorationRuleId);

        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Proration Rule deleted successfully.")
                        .data(null)
                        .build()
        );
    }
}
