package com.AccountReceivableManagement.dto.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Why an operator performs a recovery action - recorded with the action. */
@Getter
@Setter
@NoArgsConstructor
public class RecoveryRequestDto {

    @NotBlank(message = "A reason is required for a recovery action.")
    @Size(max = 500, message = "The reason must be at most 500 characters.")
    private String reason;
}
