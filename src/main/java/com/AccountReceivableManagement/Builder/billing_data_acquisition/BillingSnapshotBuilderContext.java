package com.AccountReceivableManagement.Builder.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.Entity_Enums.billing_data_acquisition.BillingSnapshotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Everything {@code BillingSnapshotBuilder} needs to construct a
 * {@link com.AccountReceivableManagement.Entity.billing_data_acquisition.BillingSnapshot}.
 * Assembled entirely by the Service, which owns every business decision
 * (totals, status, snapshot numbering) the Builder itself must not make.
 * Carries the already-validated {@link BillingAcquisitionResultDto} directly —
 * not {@code ValidationResultDto} — since validation is finished by the
 * time the Builder runs and shouldn't be a concept it knows about.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshotBuilderContext {

    private BillingConfigurationResponseDto configuration;

    private BillingSnapshotCreateRequestDto request;

    private BillingAcquisitionResultDto acquisitionResult;

    private UUID clientId;

    private String snapshotNumber;

    private String createdBy;

    private BillingSnapshotStatus status;

    private BigDecimal subtotal;

    private BigDecimal expenseAmount;

    private BigDecimal totalAmount;
}
