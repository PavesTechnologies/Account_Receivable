package com.AccountReceivableManagement.entity.concurrency_approval;


import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import com.AccountReceivableManagement.entity_enums.common.LockResourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(
        name = "record_action_lock",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_record_action_lock_resource",
                        columnNames = {
                                "resource_type",
                                "resource_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_record_action_lock_expiry",
                        columnList = "expires_at"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordActionLock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "resource_type",
            nullable = false,
            length = 50
    )
    private LockResourceType resourceType;

    @Column(
            name = "resource_id",
            nullable = false
    )
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "action_type",
            nullable = false,
            length = 30
    )
    private LockActionType actionType;

    @Column(
            name = "locked_by_user_id",
            nullable = false
    )
    private Long lockedByUserId;

    @Column(
            name = "locked_by_username",
            nullable = false
    )
    private String lockedByUsername;

    @Column(
            name = "locked_by_display_name"
    )
    private String lockedByDisplayName;

    @Column(
            name = "acquired_at",
            nullable = false
    )
    private LocalDateTime acquiredAt;

    @Column(
            name = "last_heartbeat_at",
            nullable = false
    )
    private LocalDateTime lastHeartbeatAt;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private LocalDateTime expiresAt;

}
