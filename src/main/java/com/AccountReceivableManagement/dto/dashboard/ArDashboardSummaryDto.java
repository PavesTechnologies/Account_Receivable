package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the AR dashboard needs in one response. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArDashboardSummaryDto {

    private LocalDate asOf;
    private DashboardPeriod period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate priorPeriodStart;
    private LocalDate priorPeriodEnd;
    private LocalDateTime generatedAt;

    private DashboardKpisDto kpis;
    private List<PipelineStageDto> pipeline;
    private List<TrendPointDto> trend;
    private List<AgingBucketDto> aging;
    /** Open receivables with no invoice date, which cannot be aged. */
    private long agingUnknownDateCount;
    private List<BillingModelSliceDto> billingModelMix;
    private List<WorklistItemDto> worklist;
    private List<ActivityEventDto> recentActivity;
    /** Plain-language caveats about what the figures do and do not include. */
    private List<String> notes;
}
