package com.AccountReceivableManagement.dto.project_tool_billing;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectToolBillingConfigRequestDto {

    private boolean toolBillingEnabled;

    private boolean allowOneTimeCharges;

    private boolean allowRecurringCharges;

    @NotNull(message = "Default Proration Rule is required.")
    private UUID defaultProrationRuleId;
}
