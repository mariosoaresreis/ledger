package com.marioreis.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(
        name = "MoneyCommandRequest",
        description = "Request for debit or credit operations",
        example = "{\"amount\": \"100.50\", \"currency\": \"USD\", \"reference\": \"PAYMENT-REF-001\"}"
)
public record MoneyCommandRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        @Schema(description = "Amount to transfer (minimum 0.01)", example = "100.50")
        BigDecimal amount,

        @NotBlank
        @Schema(description = "Currency code (ISO 4217)", example = "USD")
        String currency,

        @NotBlank
        @Schema(description = "Reference or description for the transaction", example = "PAYMENT-REF-001")
        String reference
) {
}

