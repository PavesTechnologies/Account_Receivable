package com.AccountReceivableManagement.entity.project_tool_billing;

import com.AccountReceivableManagement.entity.projectbilling_config.ProrationRuleMaster;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Epic 4 - Tool / Software / License Billing (Phase 1).
 * Stores the project-level Step 4 ("Tools") configuration captured by the
 * Project Billing Setup wizard. Determines eligibility only - no actual
 * billing, invoice or charge data is persisted here.
 */
@Getter
@Setter
@Entity
@Table(
        name = "ar_project_tool_billing_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ar_project_tool_billing_config_project_id",
                columnNames = "project_id"
        )
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectToolBillingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(name = "tool_billing_enabled", nullable = false)
    private boolean toolBillingEnabled;

    @Column(name = "allow_one_time_charges", nullable = false)
    private boolean allowOneTimeCharges;

    @Column(name = "allow_recurring_charges", nullable = false)
    private boolean allowRecurringCharges;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "default_proration_rule_id",
            referencedColumnName = "proration_rule_id",
            nullable = false
    )
    private ProrationRuleMaster defaultProrationRule;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
