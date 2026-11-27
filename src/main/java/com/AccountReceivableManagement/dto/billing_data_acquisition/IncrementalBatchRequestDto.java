package com.AccountReceivableManagement.dto.billing_data_acquisition;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/billing-data-acquisition/configurations/{id}/batches}. The period must
 * be exactly one of the configuration's billing periods (as returned by period discovery).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncrementalBatchRequestDto {

    @NotNull(message = "billingPeriodStart is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodStart;

    @NotNull(message = "billingPeriodEnd is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate billingPeriodEnd;

    /**
     * Optional but recommended: a client-generated key (e.g. a UUID per user action). Re-sending the same
     * key returns the batch the first request created instead of acquiring again.
     */
    @Size(max = 100, message = "idempotencyKey must be at most 100 characters")
    private String idempotencyKey;
}
