package com.marioreis.ledger.api;

import com.marioreis.ledger.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
        name = "UpdateAccountStatusRequest",
        description = "Request to update account status",
        example = "{\"status\": \"FROZEN\"}"
)
public record UpdateAccountStatusRequest(
        @NotNull
        @Schema(
                description = "New account status",
                example = "ACTIVE",
                allowableValues = {"ACTIVE", "FROZEN", "CLOSED"}
        )
        AccountStatus status
) {
}

