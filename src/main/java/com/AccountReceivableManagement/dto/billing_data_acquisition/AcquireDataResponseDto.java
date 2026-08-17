package com.AccountReceivableManagement.dto.billing_data_acquisition;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcquireDataResponseDto {

    private UUID id;

    private Long projectId;

    private UUID snapshotId;

    private String status;
}
