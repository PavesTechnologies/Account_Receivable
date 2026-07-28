package com.AccountReceivableManagement.dto.billing_data_acquisition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outcome of {@code BillingAcquisitionValidator}. When {@code success} is
 * true, {@code acquisitionResult} carries the data that passed every
 * Story 2.1 rule; when false, {@code validationMessage} explains the first
 * rule that failed (fail-fast — validation stops at the first violation).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResultDto {

    private boolean success;

    private String validationMessage;

    private BillingAcquisitionResultDto acquisitionResult;

    public static ValidationResultDto success(BillingAcquisitionResultDto acquisitionResult) {
        return ValidationResultDto.builder()
                .success(true)
                .acquisitionResult(acquisitionResult)
                .build();
    }

    public static ValidationResultDto failure(String validationMessage) {
        return ValidationResultDto.builder()
                .success(false)
                .validationMessage(validationMessage)
                .build();
    }
}
