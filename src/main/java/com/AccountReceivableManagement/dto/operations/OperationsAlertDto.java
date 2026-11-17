package com.AccountReceivableManagement.dto.operations;

import com.AccountReceivableManagement.entity_enums.operations.OperationsAlertCondition;

/** One configured condition, its current value and whether it exceeds its threshold. */
public record OperationsAlertDto(OperationsAlertCondition condition, long value, long threshold, boolean raised) {
}
