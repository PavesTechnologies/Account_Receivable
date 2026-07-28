package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTermsResponseDto {

    private UUID paymentTermId;

    private String paymentTermName;

    private Integer paymentDays;

    private String description;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
