package com.AccountReceivableManagement.dto.project_tool_assignment;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

/**
 * Epic 4 - Tool / Software / License Billing (Phase 6, Story 4.5).
 * Input for renewing an existing {@code ProjectToolAssignment}: only the new
 * effective period is supplied - tool, quantity and remarks are carried over
 * from the assignment being renewed, which itself is left unmodified.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectToolAssignmentRenewalRequestDto {

    @NotNull(message = "Start Date is required.")
    private LocalDate startDate;

    private LocalDate endDate;
}
