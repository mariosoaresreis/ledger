package com.marioreis.ledger.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Envelope published to Kafka for every domain event.
 * Both command and query services share this wire format.
 */
public record LedgerEventMessage(
        UUID eventId,
        UUID accountId,
        String eventType,
        OffsetDateTime occurredAt,
        String payload   // raw JSON of the event-specific payload
) {}

