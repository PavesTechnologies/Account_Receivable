package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks individual field changes when an APPROVED+ACTIVE configuration is edited.
 * Stores previous and new values for comparison during approval.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "billing_configuration_change_detail")
@Entity
public class BillingConfigurationChangeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "change_detail_id")
    private UUID changeDetailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "snapshot_id",
            referencedColumnName = "snapshot_id",
            nullable = false
    )
    private BillingConfigurationSnapshot snapshot;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_display_name", nullable = false, length = 200)
    private String fieldDisplayName;

    @Column(name = "field_type", length = 50)
    private String fieldType;

    @Column(name = "previous_value", length = 1000)
    private String previousValue;

    @Column(name = "new_value", length = 1000)
    private String newValue;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
