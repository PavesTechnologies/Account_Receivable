package com.AccountReceivableManagement.dto.projectbilling_config;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponseDto {

    private Long projectId;

    private String projectName;
}
