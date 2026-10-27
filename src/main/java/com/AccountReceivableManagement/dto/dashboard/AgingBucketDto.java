package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Open receivables whose age (days since invoice date) falls in [minDays, maxDays]. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgingBucketDto {

    private String key;
    private String label;
    private int minDays;
    /** Null for the open-ended last bucket. */
    private Integer maxDays;
    private long count;
    private List<CurrencyAmountDto> amounts;
}
