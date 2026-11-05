package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/** One stage of the AR billing cycle, in workflow order. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStageDto {

    /** setup, setupApproval, acquisition, tax, generation, invoiceApproval */
    private String id;
    private String label;
    private long count;
    private List<CurrencyAmountDto> amounts;
    /** Age in days of the longest-waiting item, or null when not measurable. */
    private Long oldestWaitingDays;
    /** Stage-specific sub-counts (for example active and draft configurations). */
    private Map<String, Long> breakdown;
}
