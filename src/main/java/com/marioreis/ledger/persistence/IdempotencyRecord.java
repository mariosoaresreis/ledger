package com.marioreis.ledger.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdempotencyRecord(
        UUID idempotencyKey,
        String operation,
        String requestHash,
        Integer responseStatus,
        String responseBody,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
    public boolean completed() {
        return responseStatus != null && responseBody != null;
    }
}

