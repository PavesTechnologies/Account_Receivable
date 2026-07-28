package com.AccountReceivableManagement.entity.project_entity;

import com.AccountReceivableManagement.entity_enums.client.DeliveryModel;
import com.AccountReceivableManagement.entity_enums.client.PriorityLevel;
import com.AccountReceivableManagement.entity_enums.project.ProjectDataStatus;
import com.AccountReceivableManagement.entity_enums.project.ProjectStage;
import com.AccountReceivableManagement.entity_enums.project.ProjectStatus;
import com.AccountReceivableManagement.entity_enums.project.RiskLevel;
import com.AccountReceivableManagement.cdc.serialization.CdcAwareEnumDeserializer;
import com.AccountReceivableManagement.cdc.serialization.UuidFromStringDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_master_reference")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ProjectMasterReference {
    
    @Id
    @Column(name = "pms_project_id")
    @JsonProperty("pms_project_id")
    private Long pmsProjectId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "client_id")
    @JsonProperty("client_id")
    @JsonDeserialize(using = UuidFromStringDeserializer.class)
    private UUID clientId;

    @Column(name = "project_manager_id")
    @JsonProperty("project_manager_id")
    private Long projectManagerId;

    @Column(name = "resource_manager_id")
    @JsonProperty("resource_manager_id")
    private Long resourceManagerId;

    @Column(name = "delivery_owner_id")
    @JsonProperty("delivery_owner_id")
    private Long deliveryOwnerId;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private DeliveryModel deliveryModel;

    @Column(name = "primary_location")
    private String primaryLocation;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private PriorityLevel priorityLevel;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private RiskLevel riskLevel;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "project_budget")
    private BigDecimal projectBudget;

    @Column(name = "project_budget_currency")
    private String projectBudgetCurrency;

    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private ProjectStatus projectStatus;

    @Column(name = "lifecycle_stage")
    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private ProjectStage lifecycleStage;

    @Column(name = "data_status")
    @Enumerated(EnumType.STRING)
    @JsonDeserialize(using = CdcAwareEnumDeserializer.class)
    private ProjectDataStatus dataStatus;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
