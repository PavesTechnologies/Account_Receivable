package com.AccountReceivableManagement.dto.invoice_generation;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceGenerationWorkspaceResponseDto {

    private InvoiceGenerationWorkspaceSummaryDto summary;

    private List<InvoiceGenerationWorkspaceRowDto> rows;
}
