package com.AccountReceivableManagement.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** A queue that needs action. Always returned, even at zero, so clients can show an all-clear state. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorklistItemDto {

    /** overdue-critical, setup-approvals, tax-ready, draft-queue, awaiting-send, delivery-failed */
    private String id;
    private long count;
    private List<CurrencyAmountDto> amounts;
    /** Up to three client names, most overdue first (overdue item only). */
    private List<String> clientNames;
    private Long oldestDays;
}
