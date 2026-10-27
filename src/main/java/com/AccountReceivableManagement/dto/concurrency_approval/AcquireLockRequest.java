package com.AccountReceivableManagement.dto.concurrency_approval;

import com.AccountReceivableManagement.entity_enums.common.LockActionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcquireLockRequest {

    @NotNull(message = "Action type is required")
    private LockActionType actionType;
}
