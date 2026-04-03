package com.marioreis.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccountRequest(
        @NotNull UUID ownerId,
        @NotBlank String currency
) {
}

