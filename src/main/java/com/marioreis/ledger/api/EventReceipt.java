package com.marioreis.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(
        name = "EventReceipt",
        description = "Receipt of an event emitted during command processing",
        example = "{\"aggregateId\": \"550e8400-e29b-41d4-a716-446655440000\", \"eventType\": \"ACCOUNT_CREATED\", \"version\": 1}"
)
public record EventReceipt(
        @Schema(description = "Aggregate ID (account ID)", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID aggregateId,

        @Schema(description = "Event type", example = "ACCOUNT_CREATED")
        String eventType,

        @Schema(description = "Event version (optimistic lock)", example = "1")
        long version
) {
}

