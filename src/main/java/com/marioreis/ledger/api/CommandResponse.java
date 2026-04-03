package com.marioreis.ledger.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CommandResponse(
        UUID commandId,
        UUID idempotencyKey,
        String operation,
        String status,
        UUID primaryResourceId,
        List<EventReceipt> events,
        OffsetDateTime processedAt
) {
}

