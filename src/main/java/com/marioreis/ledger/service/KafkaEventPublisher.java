package com.marioreis.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publishes domain events to the Kafka ledger.events topic after each outbox write.
 * Publishing is best-effort: failures are logged but do not roll back the command transaction.
 * The outbox table is the authoritative source; this is a convenience fast-path for the query side.
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String eventsTopic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper,
                               @Value("${ledger.kafka.topics.events}") String eventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.eventsTopic = eventsTopic;
    }

    public void publish(UUID accountId, String eventType, String payload, OffsetDateTime occurredAt) {
        LedgerEventMessage message = new LedgerEventMessage(
                UUID.randomUUID(),
                accountId,
                eventType,
                occurredAt,
                payload
        );
        try {
            String json = objectMapper.writeValueAsString(message);
            // Use accountId as partition key to preserve per-account ordering
            kafkaTemplate.send(eventsTopic, accountId.toString(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish event {} for account {}: {}", eventType, accountId, ex.getMessage());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize Kafka event {} for account {}: {}", eventType, accountId, e.getMessage());
        }
    }
}

