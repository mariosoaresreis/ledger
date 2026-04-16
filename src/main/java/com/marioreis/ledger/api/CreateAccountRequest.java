package com.marioreis.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(
        name = "CreateAccountRequest",
        description = "Request to create a new account",
        example = "{\"ownerId\": \"550e8400-e29b-41d4-a716-446655440000\", \"currency\": \"USD\"}"
)
public record CreateAccountRequest(
        @NotNull
        @Schema(description = "UUID of the account owner", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID ownerId,

        @NotBlank
        @Schema(description = "Currency code (ISO 4217)", example = "USD")
        String currency
) {
}

