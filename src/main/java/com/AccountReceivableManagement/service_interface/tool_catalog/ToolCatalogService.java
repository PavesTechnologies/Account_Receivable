package com.AccountReceivableManagement.service_interface.tool_catalog;

import com.AccountReceivableManagement.dto.tool_catalog.ToolCatalogRequestDto;
import com.AccountReceivableManagement.dto.tool_catalog.ToolCatalogResponseDto;

import java.util.List;
import java.util.UUID;

public interface ToolCatalogService {

    ToolCatalogResponseDto create(ToolCatalogRequestDto request);

    ToolCatalogResponseDto update(UUID toolId, ToolCatalogRequestDto request);

    ToolCatalogResponseDto getById(UUID toolId);

    List<ToolCatalogResponseDto> getAll();

    List<ToolCatalogResponseDto> getActive();

    void delete(UUID toolId);
}
