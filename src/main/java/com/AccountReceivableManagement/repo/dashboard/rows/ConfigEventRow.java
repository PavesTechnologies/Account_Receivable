package com.AccountReceivableManagement.repo.dashboard.rows;

import com.AccountReceivableManagement.entity_enums.projectbilling_config.ApprovalStatus;

import java.time.LocalDateTime;

/** A billing configuration's current approval status and when it last changed. */
public record ConfigEventRow(ApprovalStatus approvalStatus, String projectName, LocalDateTime updatedAt) {
}
