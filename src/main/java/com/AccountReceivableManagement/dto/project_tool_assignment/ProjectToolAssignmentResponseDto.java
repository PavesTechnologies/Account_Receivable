package com.AccountReceivableManagement.dto.project_tool_assignment;

import com.AccountReceivableManagement.entity_enums.tool_catalog.BillingBasis;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectToolAssignmentResponseDto {

    private UUID assignmentId;

    private Long projectId;

    private String projectName;

    private UUID toolId;

    private String assetCode;

    private String assetName;

    private UUID currencyId;

    private String currencyCode;

    private String currencyName;

    private Integer quantity;

    private BillingBasis billingBasis;

    private String remarks;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
