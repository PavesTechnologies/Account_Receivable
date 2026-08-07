package com.AccountReceivableManagement.dto.project_tool_assignment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectToolAssignmentRequestDto {

    @NotNull(message = "Project is required.")
    private Long projectId;

    @NotNull(message = "Tool is required.")
    private UUID toolId;

    @NotNull(message = "Quantity is required.")
    @Min(value = 1, message = "Quantity must be greater than zero.")
    private Integer quantity;

    @Size(max = 500, message = "Remarks cannot exceed 500 characters.")
    private String remarks;

    @NotNull(message = "Start Date is required.")
    private LocalDate startDate;

    private LocalDate endDate;
}
