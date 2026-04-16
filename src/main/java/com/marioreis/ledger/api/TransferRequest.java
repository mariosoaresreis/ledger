package com.marioreis.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(
        name = "TransferRequest",
        description = "Request to initiate a transfer between two accounts",
        example = "{\"sourceAccountId\": \"550e8400-e29b-41d4-a716-446655440000\", \"targetAccountId\": \"660e8400-e29b-41d4-a716-446655440111\", \"amount\": \"50.00\", \"currency\": \"USD\"}"
)
public record TransferRequest(
        @NotNull
        @Schema(description = "UUID of the source account", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID sourceAccountId,

        @NotNull
        @Schema(description = "UUID of the target account", example = "660e8400-e29b-41d4-a716-446655440111")
        UUID targetAccountId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Schema(description = "Amount to transfer (minimum 0.01)", example = "50.00")
        BigDecimal amount,

        @NotBlank
        @Schema(description = "Currency code (ISO 4217)", example = "USD")
        String currency
) {
}

