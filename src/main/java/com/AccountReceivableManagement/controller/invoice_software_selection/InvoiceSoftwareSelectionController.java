package com.AccountReceivableManagement.controller.invoice_software_selection;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.invoice_software_selection.InvoiceSoftwareSelectionResponseDto;
import com.AccountReceivableManagement.service_interface.invoice_software_selection.InvoiceSoftwareSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Software/Tools/Licenses available for selection under Invoice Draft →
 * Additional Charges. Merges RMS assignment data with Tool Pricing; does not
 * calculate charges, persist a selection, or touch Billing History (Phase 6).
 */
@RestController
@RequestMapping("/api/invoice/software-selection")
@RequiredArgsConstructor
public class InvoiceSoftwareSelectionController {

    private final InvoiceSoftwareSelectionService invoiceSoftwareSelectionService;

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<List<InvoiceSoftwareSelectionResponseDto>>> getSelectableAssets(
            @PathVariable Long projectId) {

        List<InvoiceSoftwareSelectionResponseDto> response =
                invoiceSoftwareSelectionService.getSelectableAssets(projectId);

        return ResponseEntity.ok(
                ApiResponse.<List<InvoiceSoftwareSelectionResponseDto>>builder()
                        .success(true)
                        .message("Selectable software retrieved successfully.")
                        .data(response)
                        .build());
    }
}
