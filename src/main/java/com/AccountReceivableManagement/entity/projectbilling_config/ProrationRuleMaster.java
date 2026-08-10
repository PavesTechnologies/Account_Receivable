package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "proration_rule_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProrationRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "proration_rule_id")
    private UUID prorationRuleId;

    @Column(name = "proration_rule_code", nullable = false, unique = true, length = 20)
    private String prorationRuleCode;

    @Column(name = "proration_rule_name", nullable = false, length = 100)
    private String prorationRuleName;

    @Column(name = "description")
    private String description;

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
