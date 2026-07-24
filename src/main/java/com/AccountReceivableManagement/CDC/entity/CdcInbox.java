package com.AccountReceivableManagement.CDC.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cdc_inbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdcInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Connector name (e.g., RMS)
     */
    @Column(name = "connector_name", nullable = false)
    private String connectorName;

    /**
     * Entity type (e.g., RMS-client)
     */
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    /**
     * Entity ID
     */
    @Column(name = "entity_id", nullable = false)
    private String entityId;

    /**
     * Operation type: c (create), u (update), d (delete)
     */
    @Column(name = "operation", nullable = false)
    private String operation;

    /**
     * Serialized event payload (JSON)
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * Processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProcessingStatus status = ProcessingStatus.PENDING;

    /**
     * Number of retry attempts
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Error message if processing failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Timestamp when event was received
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Timestamp when event was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Timestamp when event was successfully processed
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ProcessingStatus {
        PENDING,
        PROCESSED,
        FAILED
    }
}
