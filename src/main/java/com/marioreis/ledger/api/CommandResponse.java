package com.marioreis.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(
        name = "CommandResponse",
        description = "Response envelope for all command operations",
        example = "{\"commandId\": \"550e8400-e29b-41d4-a716-446655440000\", \"idempotencyKey\": \"550e8400-e29b-41d4-a716-446655440001\", \"operation\": \"CREATE_ACCOUNT\", \"status\": \"ACCEPTED\", \"accountId\": \"550e8400-e29b-41d4-a716-446655440002\", \"events\": [], \"processedAt\": \"2024-01-15T10:30:00Z\"}"
)
public record CommandResponse(
        @Schema(description = "Unique command ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID commandId,

        @Schema(description = "Idempotency key used for the request", example = "550e8400-e29b-41d4-a716-446655440001")
        UUID idempotencyKey,

        @Schema(description = "Operation type", example = "CREATE_ACCOUNT")
        String operation,

        @Schema(description = "Command status (ACCEPTED, COMPLETED, REJECTED)", example = "ACCEPTED")
        String status,

        @Schema(description = "Account Id", example = "550e8400-e29b-41d4-a716-446655440002")
        UUID accountId,

        @Schema(description = "List of events emitted during command processing")
        List<EventReceipt> events,

        @Schema(description = "Timestamp when the command was processed", example = "2024-01-15T10:30:00Z")
        OffsetDateTime processedAt
) {
}

