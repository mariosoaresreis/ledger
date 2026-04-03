package com.marioreis.ledger.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID targetAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency
) {
}

