package com.AccountReceivableManagement.controller.company_profile;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.company_profile.CompanyProfileRequestDto;
import com.AccountReceivableManagement.dto.company_profile.CompanyProfileResponseDto;
import com.AccountReceivableManagement.service_interface.company_profile.CompanyProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The seller/issuing-company counterpart of the Client API - previously
 * nonexistent anywhere in this backend. {@code GET /api/v1/company-profile}
 * is the authoritative source an Invoice Preview screen should call instead
 * of hardcoding seller details.
 */
@RestController
@RequestMapping("/api/v1/company-profile")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyProfileResponseDto>> create(
            @Valid @RequestBody CompanyProfileRequestDto request
    ) {

        CompanyProfileResponseDto response = companyProfileService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CompanyProfileResponseDto>builder()
                        .success(true)
                        .message("Company profile created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{companyProfileId}")
    public ResponseEntity<ApiResponse<CompanyProfileResponseDto>> update(
            @PathVariable UUID companyProfileId,
            @Valid @RequestBody CompanyProfileRequestDto request
    ) {

        CompanyProfileResponseDto response =
                companyProfileService.update(companyProfileId, request);

        return ResponseEntity.ok(ApiResponse.<CompanyProfileResponseDto>builder()
                .success(true)
                .message("Company profile updated successfully.")
                .data(response)
                .build());
    }

    @GetMapping("/{companyProfileId}")
    public ResponseEntity<ApiResponse<CompanyProfileResponseDto>> getById(
            @PathVariable UUID companyProfileId
    ) {

        CompanyProfileResponseDto response =
                companyProfileService.getById(companyProfileId);

        return ResponseEntity.ok(ApiResponse.<CompanyProfileResponseDto>builder()
                .success(true)
                .message("Company profile retrieved successfully.")
                .data(response)
                .build());
    }

    /**
     * The active seller/company profile for invoice generation and
     * Invoice Preview.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CompanyProfileResponseDto>> getActive() {

        CompanyProfileResponseDto response = companyProfileService.getActive();

        return ResponseEntity.ok(ApiResponse.<CompanyProfileResponseDto>builder()
                .success(true)
                .message("Company profile retrieved successfully.")
                .data(response)
                .build());
    }
}
