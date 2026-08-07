package com.AccountReceivableManagement.entity.project_tool_assignment;

import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity.tool_catalog.ToolCatalog;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Epic 4 - Tool / Software / License Billing (Phase 3, Story 4.2).
 * Maps a Tool Catalog item to a project with quantity and an effective
 * period. Billing Basis is not stored here - it belongs to the Tool Catalog
 * definition and is read through the {@code tool} association. Consumed
 * later by Billing Data Acquisition - no charge calculation happens here.
 */
@Entity
@Table(name = "project_tool_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectToolAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id")
    private UUID assignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "pms_project_id", nullable = false)
    private ProjectMasterReference project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", referencedColumnName = "tool_id", nullable = false)
    private ToolCatalog tool;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
