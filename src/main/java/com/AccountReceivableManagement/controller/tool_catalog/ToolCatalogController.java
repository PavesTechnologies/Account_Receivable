package com.AccountReceivableManagement.controller.tool_catalog;

import com.AccountReceivableManagement.dto.centralizeddto.ApiResponse;
import com.AccountReceivableManagement.dto.tool_catalog.ToolCatalogRequestDto;
import com.AccountReceivableManagement.dto.tool_catalog.ToolCatalogResponseDto;
import com.AccountReceivableManagement.service_interface.tool_catalog.ToolCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tool-catalog")
@RequiredArgsConstructor
public class ToolCatalogController {

    private final ToolCatalogService toolCatalogService;

    @PostMapping
    public ResponseEntity<ApiResponse<ToolCatalogResponseDto>> create(
            @Valid @RequestBody ToolCatalogRequestDto request) {

        ToolCatalogResponseDto response = toolCatalogService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ToolCatalogResponseDto>builder()
                        .success(true)
                        .message("Tool Catalog entry created successfully.")
                        .data(response)
                        .build());
    }

    @PutMapping("/{toolId}")
    public ResponseEntity<ApiResponse<ToolCatalogResponseDto>> update(
            @PathVariable UUID toolId,
            @Valid @RequestBody ToolCatalogRequestDto request) {

        ToolCatalogResponseDto response = toolCatalogService.update(toolId, request);

        return ResponseEntity.ok(
                ApiResponse.<ToolCatalogResponseDto>builder()
                        .success(true)
                        .message("Tool Catalog entry updated successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/{toolId}")
    public ResponseEntity<ApiResponse<ToolCatalogResponseDto>> getById(
            @PathVariable UUID toolId) {

        ToolCatalogResponseDto response = toolCatalogService.getById(toolId);

        return ResponseEntity.ok(
                ApiResponse.<ToolCatalogResponseDto>builder()
                        .success(true)
                        .message("Tool Catalog entry retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ToolCatalogResponseDto>>> getAll() {

        List<ToolCatalogResponseDto> response = toolCatalogService.getAll();

        return ResponseEntity.ok(
                ApiResponse.<List<ToolCatalogResponseDto>>builder()
                        .success(true)
                        .message("Tool Catalog entries retrieved successfully.")
                        .data(response)
                        .build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ToolCatalogResponseDto>>> getActive() {

        List<ToolCatalogResponseDto> response = toolCatalogService.getActive();

        return ResponseEntity.ok(
                ApiResponse.<List<ToolCatalogResponseDto>>builder()
                        .success(true)
                        .message("Active Tool Catalog entries retrieved successfully.")
                        .data(response)
                        .build());
    }

    @DeleteMapping("/{toolId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID toolId) {

        toolCatalogService.delete(toolId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Tool Catalog entry deleted successfully.")
                        .data(null)
                        .build());
    }
}
