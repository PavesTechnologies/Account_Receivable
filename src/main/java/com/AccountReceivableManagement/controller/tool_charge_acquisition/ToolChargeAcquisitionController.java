package com.AccountReceivableManagement.controller.tool_charge_acquisition;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargeAcquisitionRequestDto;
import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargePreviewDto;
import com.AccountReceivableManagement.service_interface.tool_charge_acquisition.ToolChargeAcquisitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tool-charge-acquisition")
@RequiredArgsConstructor
public class ToolChargeAcquisitionController {

    private final ToolChargeAcquisitionService toolChargeAcquisitionService;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<List<ToolChargePreviewDto>>> preview(
            @Valid @RequestBody ToolChargeAcquisitionRequestDto request) {

        List<ToolChargePreviewDto> response = toolChargeAcquisitionService.acquireCharges(request);

        return ResponseEntity.ok(
                ApiResponse.<List<ToolChargePreviewDto>>builder()
                        .success(true)
                        .message("Tool Charge Preview generated successfully.")
                        .data(response)
                        .build());
    }
}
