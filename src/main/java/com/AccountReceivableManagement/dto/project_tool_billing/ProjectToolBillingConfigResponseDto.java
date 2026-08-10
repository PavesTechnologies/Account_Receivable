package com.AccountReceivableManagement.dto.project_tool_billing;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectToolBillingConfigResponseDto {

    private UUID id;

    private Long projectId;

    private boolean toolBillingEnabled;

    private boolean allowOneTimeCharges;

    private boolean allowRecurringCharges;

    private UUID defaultProrationRuleId;

    private String defaultProrationRuleCode;

    private String defaultProrationRuleName;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
