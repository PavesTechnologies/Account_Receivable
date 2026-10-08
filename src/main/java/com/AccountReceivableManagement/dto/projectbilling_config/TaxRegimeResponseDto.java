package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRegimeResponseDto {

    private UUID taxRegimeId;

    private String taxRegimeCode;

    private String taxRegimeName;

    private String description;

    private Boolean isActive;

    private List<TaxComponentResponseDto> components;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
