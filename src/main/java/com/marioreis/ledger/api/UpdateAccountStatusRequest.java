package com.marioreis.ledger.api;

import com.marioreis.ledger.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        @NotNull AccountStatus status
) {
}

