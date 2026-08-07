package com.AccountReceivableManagement.service_Imple.project_tool_billing;

import com.AccountReceivableManagement.dto.project_tool_billing.ProjectToolBillingConfigRequestDto;
import com.AccountReceivableManagement.dto.project_tool_billing.ProjectToolBillingConfigResponseDto;
import com.AccountReceivableManagement.entity.project_tool_billing.ProjectToolBillingConfig;
import com.AccountReceivableManagement.entity.projectbilling_config.ProrationRuleMaster;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project_tool_billing.ProjectToolBillingConfigRepository;
import com.AccountReceivableManagement.repo.projectbilling_config.ProrationRuleMasterRepository;
import com.AccountReceivableManagement.service_interface.project_tool_billing.ProjectToolBillingConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectToolBillingConfigServiceImpl implements ProjectToolBillingConfigService {

    private final ProjectToolBillingConfigRepository projectToolBillingConfigRepository;
    private final ProrationRuleMasterRepository prorationRuleMasterRepository;

    @Override
    public ProjectToolBillingConfigResponseDto getByProjectId(Long projectId) {

        ProjectToolBillingConfig config =
                projectToolBillingConfigRepository.findByProjectId(projectId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Tool Billing Configuration not found for this project."));

        return mapToResponse(config);
    }

    @Override
    public ProjectToolBillingConfigResponseDto save(
            Long projectId,
            ProjectToolBillingConfigRequestDto request) {

        if (projectToolBillingConfigRepository.existsByProjectId(projectId)) {
            throw new GlobalExceptionHandler.DuplicateResourceException(
                    "Tool Billing Configuration already exists for this project.");
        }

        ProrationRuleMaster prorationRule =
                prorationRuleMasterRepository.findById(request.getDefaultProrationRuleId())
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Proration Rule not found."));

        ProjectToolBillingConfig config = ProjectToolBillingConfig.builder()
                .projectId(projectId)
                .toolBillingEnabled(request.isToolBillingEnabled())
                .defaultProrationRule(prorationRule)
                .build();

        applyChargeEligibilityRule(config, request);

        ProjectToolBillingConfig saved =
                projectToolBillingConfigRepository.save(config);

        return mapToResponse(saved);
    }

    @Override
    public ProjectToolBillingConfigResponseDto update(
            Long projectId,
            ProjectToolBillingConfigRequestDto request) {

        ProjectToolBillingConfig config =
                projectToolBillingConfigRepository.findByProjectId(projectId)
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Tool Billing Configuration not found for this project."));

        ProrationRuleMaster prorationRule =
                prorationRuleMasterRepository.findById(request.getDefaultProrationRuleId())
                        .orElseThrow(() ->
                                new GlobalExceptionHandler.ResourceNotFoundException(
                                        "Proration Rule not found."));

        config.setToolBillingEnabled(request.isToolBillingEnabled());
        config.setDefaultProrationRule(prorationRule);

        applyChargeEligibilityRule(config, request);

        ProjectToolBillingConfig updated =
                projectToolBillingConfigRepository.save(config);

        return mapToResponse(updated);
    }

    private void applyChargeEligibilityRule(
            ProjectToolBillingConfig config,
            ProjectToolBillingConfigRequestDto request) {

        if (!request.isToolBillingEnabled()) {
            config.setAllowOneTimeCharges(false);
            config.setAllowRecurringCharges(false);
            return;
        }

        config.setAllowOneTimeCharges(request.isAllowOneTimeCharges());
        config.setAllowRecurringCharges(request.isAllowRecurringCharges());
    }

    private ProjectToolBillingConfigResponseDto mapToResponse(
            ProjectToolBillingConfig config) {

        ProrationRuleMaster prorationRule = config.getDefaultProrationRule();

        return ProjectToolBillingConfigResponseDto.builder()
                .id(config.getId())
                .projectId(config.getProjectId())
                .toolBillingEnabled(config.isToolBillingEnabled())
                .allowOneTimeCharges(config.isAllowOneTimeCharges())
                .allowRecurringCharges(config.isAllowRecurringCharges())
                .defaultProrationRuleId(prorationRule.getProrationRuleId())
                .defaultProrationRuleCode(prorationRule.getProrationRuleCode())
                .defaultProrationRuleName(prorationRule.getProrationRuleName())
                .createdBy(config.getCreatedBy())
                .createdAt(config.getCreatedAt())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
