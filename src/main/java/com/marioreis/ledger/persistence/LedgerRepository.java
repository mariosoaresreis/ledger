package com.marioreis.ledger.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marioreis.ledger.domain.AccountAggregate;
import com.marioreis.ledger.domain.AccountStatus;
import com.marioreis.ledger.domain.LedgerEventType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LedgerRepository {

    private static final RowMapper<AccountRow> ACCOUNT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public AccountRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AccountRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("owner_id", UUID.class),
                    AccountStatus.valueOf(rs.getString("status")),
                    rs.getString("currency"),
                    rs.getObject("created_at", OffsetDateTime.class)
            );
        }
    };

    private static final RowMapper<LedgerEventRow> EVENT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public LedgerEventRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new LedgerEventRow(
                    LedgerEventType.valueOf(rs.getString("event_type")),
                    rs.getString("payload"),
                    rs.getLong("version")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LedgerRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<AccountAggregate> findAccount(UUID accountId) {
        List<AccountRow> accounts = jdbcTemplate.query(
                """
                select id, owner_id, status, currency, created_at
                from accounts
                where id = ?
                """,
                ACCOUNT_ROW_MAPPER,
                accountId
        );

        if (accounts.isEmpty()) {
            return Optional.empty();
        }

        AccountRow account = accounts.getFirst();
        List<LedgerEventRow> events = findEventsByAggregate(accountId);
        long version = events.stream().mapToLong(LedgerEventRow::version).max().orElse(0L);
        BigDecimal balance = events.stream()
                .map(this::extractDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Optional.of(new AccountAggregate(
                account.id(),
                account.ownerId(),
                account.status(),
                account.currency(),
                account.createdAt(),
                version,
                balance
        ));
    }

    public long nextVersion(UUID accountId) {
        Long currentVersion = jdbcTemplate.queryForObject(
                "select coalesce(max(version), 0) from ledger_events where account_id = ?",
                Long.class,
                accountId
        );
        return (currentVersion == null ? 0L : currentVersion) + 1L;
    }

    public void insertAccount(UUID id, UUID ownerId, AccountStatus status, String currency, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                insert into accounts (id, owner_id, status, currency, created_at)
                values (?, ?, ?, ?, ?)
                """,
                id,
                ownerId,
                status.name(),
                currency,
                createdAt
        );
    }

    public void updateAccountStatus(UUID id, AccountStatus status) {
        jdbcTemplate.update(
                "update accounts set status = ? where id = ?",
                status.name(),
                id
        );
    }

    public void appendEvent(UUID accountId,
                            LedgerEventType eventType,
                            String payload,
                            long version,
                            OffsetDateTime occurredAt,
                            UUID idempotencyKey) {
        jdbcTemplate.update(
                """
                insert into ledger_events (id, account_id, event_type, payload, version, occurred_at, idempotency_key)
                values (?, ?, ?, cast(? as JSONB), ?, ?, ?)
                """,
                UUID.randomUUID(),
                accountId,
                eventType.name(),
                payload,
                version,
                occurredAt,
                idempotencyKey
        );
    }

    public void appendOutbox(UUID accountId, LedgerEventType eventType, String payload) {
        jdbcTemplate.update(
                """
                insert into outbox (id, account_id, event_type, payload, published_at)
                values (?, ?, ?, cast(? as JSONB), null)
                """,
                UUID.randomUUID(),
                accountId,
                eventType.name(),
                payload
        );
    }

    private List<LedgerEventRow> findEventsByAggregate(UUID accountId) {
        return jdbcTemplate.query(
                """
                select event_type, cast(payload as varchar) as payload, version
                from ledger_events
                where account_id = ?
                order by version asc
                """,
                EVENT_ROW_MAPPER,
                accountId
        );
    }

    private BigDecimal extractDelta(LedgerEventRow eventRow) {
        try {
            JsonNode payload = readPayloadNode(eventRow.payload());
            return switch (eventRow.eventType()) {
                case ACCOUNT_CREDITED -> new BigDecimal(payload.get("amount").asText());
                case ACCOUNT_DEBITED -> new BigDecimal(payload.get("amount").asText()).negate();
                default -> BigDecimal.ZERO;
            };
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read event payload for aggregate replay", exception);
        }
    }

    private JsonNode readPayloadNode(String rawPayload) throws Exception {
        JsonNode payload = objectMapper.readTree(rawPayload);
        if (payload.isTextual()) {
            payload = objectMapper.readTree(payload.asText());
        }
        return payload;
    }

    private record AccountRow(
            UUID id,
            UUID ownerId,
            AccountStatus status,
            String currency,
            OffsetDateTime createdAt
    ) {
    }
}


