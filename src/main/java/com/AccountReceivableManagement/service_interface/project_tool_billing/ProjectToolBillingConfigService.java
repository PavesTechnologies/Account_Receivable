package com.AccountReceivableManagement.service_interface.project_tool_billing;

import com.AccountReceivableManagement.dto.project_tool_billing.ProjectToolBillingConfigRequestDto;
import com.AccountReceivableManagement.dto.project_tool_billing.ProjectToolBillingConfigResponseDto;

public interface ProjectToolBillingConfigService {

    ProjectToolBillingConfigResponseDto getByProjectId(Long projectId);

    ProjectToolBillingConfigResponseDto save(
            Long projectId,
            ProjectToolBillingConfigRequestDto request);

    ProjectToolBillingConfigResponseDto update(
            Long projectId,
            ProjectToolBillingConfigRequestDto request);
}
