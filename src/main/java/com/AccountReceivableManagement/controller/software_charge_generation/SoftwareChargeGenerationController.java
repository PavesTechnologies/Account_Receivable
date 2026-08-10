package com.AccountReceivableManagement.controller.software_charge_generation;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_software_selection.InvoiceSoftwareSelectionResponseDto;
import com.AccountReceivableManagement.dto.software_charge_generation.SoftwareChargeLineDto;
import com.AccountReceivableManagement.service_interface.software_charge_generation.SoftwareChargeGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Turns Finance's selected software (Phase 3 output) into runtime charge
 * lines. Nothing is persisted here - Invoice Snapshot / Invoice Draft
 * persistence is a later phase.
 */
@RestController
@RequestMapping("/api/invoice/software-charge-generation")
@RequiredArgsConstructor
public class SoftwareChargeGenerationController {

    private final SoftwareChargeGenerationService softwareChargeGenerationService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<SoftwareChargeLineDto>>> generateChargeLines(
            @RequestBody List<InvoiceSoftwareSelectionResponseDto> selectedAssets) {

        List<SoftwareChargeLineDto> response =
                softwareChargeGenerationService.generateChargeLines(selectedAssets);

        return ResponseEntity.ok(
                ApiResponse.<List<SoftwareChargeLineDto>>builder()
                        .success(true)
                        .message("Software charge lines generated successfully.")
                        .data(response)
                        .build());
    }
}
