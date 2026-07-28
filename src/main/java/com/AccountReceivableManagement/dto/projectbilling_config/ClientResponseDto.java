package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientResponseDto {

    private UUID clientId;

    private String clientName;
}
