package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_type_master")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillingTypeMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "billing_type_id")
    private UUID billingTypeId;

    @Column(name = "billing_type_name", nullable = false, unique = true, length = 100)
    private String billingTypeName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
