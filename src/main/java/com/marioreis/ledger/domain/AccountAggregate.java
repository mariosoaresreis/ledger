package com.marioreis.ledger.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountAggregate(
        UUID id,
        UUID ownerId,
        AccountStatus status,
        String currency,
        OffsetDateTime createdAt,
        long version,
        BigDecimal balance
) {
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}

