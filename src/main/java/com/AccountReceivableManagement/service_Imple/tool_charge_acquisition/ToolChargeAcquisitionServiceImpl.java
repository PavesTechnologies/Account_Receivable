package com.AccountReceivableManagement.service_Imple.tool_charge_acquisition;

import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargeAcquisitionRequestDto;
import com.AccountReceivableManagement.dto.tool_charge_acquisition.ToolChargePreviewDto;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.project_tool_assignment.ProjectToolAssignment;
import com.AccountReceivableManagement.entity.project_tool_billing.ProjectToolBillingConfig;
import com.AccountReceivableManagement.entity.projectbilling_config.CurrencyMaster;
import com.AccountReceivableManagement.entity.tool_catalog.ToolCatalog;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.repo.project_tool_assignment.ProjectToolAssignmentRepository;
import com.AccountReceivableManagement.repo.project_tool_billing.ProjectToolBillingConfigRepository;
import com.AccountReceivableManagement.service_interface.tool_charge_acquisition.ToolChargeAcquisitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolChargeAcquisitionServiceImpl implements ToolChargeAcquisitionService {

    private final ProjectToolBillingConfigRepository projectToolBillingConfigRepository;
    private final ProjectToolAssignmentRepository projectToolAssignmentRepository;

    @Override
    public List<ToolChargePreviewDto> acquireCharges(ToolChargeAcquisitionRequestDto request) {

        if (request.getBillingPeriodEnd().isBefore(request.getBillingPeriodStart())) {
            throw new GlobalExceptionHandler.ValidationException(
                    "Billing Period End cannot be earlier than Billing Period Start.");
        }

        Optional<ProjectToolBillingConfig> config =
                projectToolBillingConfigRepository.findByProjectId(request.getProjectId());

        if (config.isEmpty() || !config.get().isToolBillingEnabled()) {
            return Collections.emptyList();
        }

        List<ProjectToolAssignment> assignments =
                projectToolAssignmentRepository.findActiveAssignmentsForBillingPeriod(
                        request.getProjectId(),
                        request.getBillingPeriodStart(),
                        request.getBillingPeriodEnd());

        return assignments.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getTool().getIsActive()))
                .map(assignment -> mapToPreview(assignment, request))
                .collect(Collectors.toList());
    }

    private ToolChargePreviewDto mapToPreview(
            ProjectToolAssignment assignment,
            ToolChargeAcquisitionRequestDto request) {

        ProjectMasterReference project = assignment.getProject();
        ToolCatalog tool = assignment.getTool();
        CurrencyMaster currency = tool.getCurrency();

        BigDecimal calculatedAmount = BigDecimal.valueOf(assignment.getQuantity())
                .multiply(tool.getUnitPrice());

        return ToolChargePreviewDto.builder()
                .assignmentId(assignment.getAssignmentId())
                .projectId(project.getPmsProjectId())
                .projectName(project.getProjectName())
                .toolId(tool.getToolId())
                .assetCode(tool.getAssetCode())
                .assetName(tool.getAssetName())
                .billingBasis(tool.getBillingBasis())
                .currencyId(currency.getCurrencyId())
                .currencyCode(currency.getCurrencyCode())
                .currencyName(currency.getCurrencyName())
                .quantity(assignment.getQuantity())
                .unitPrice(tool.getUnitPrice())
                .calculatedAmount(calculatedAmount)
                .billingPeriodStart(request.getBillingPeriodStart())
                .billingPeriodEnd(request.getBillingPeriodEnd())
                .assignmentStartDate(assignment.getStartDate())
                .assignmentEndDate(assignment.getEndDate())
                .build();
    }
}
