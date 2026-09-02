package com.AccountReceivableManagement.entity.projectbilling_config;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tax_type_master",
        indexes = {
                @Index(
                        name = "idx_tax_type_code",
                        columnList = "tax_type_code"
                ),
                @Index(
                        name = "idx_tax_type_active",
                        columnList = "is_active"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxTypeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tax_type_id")
    private UUID taxTypeId;

    @Column(
            name = "tax_type_code",
            nullable = false,
            unique = true,
            length = 50
    )
    private String taxTypeCode;

    @Column(
            name = "tax_type_name",
            nullable = false,
            length = 100
    )
    private String taxTypeName;

    @Column(
            name = "description",
            length = 500
    )
    private String description;

    @Column(
            name = "is_active",
            nullable = false
    )
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
