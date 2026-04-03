package com.marioreis.ledger.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IdempotencyRepository {

    private static final RowMapper<IdempotencyRecord> ROW_MAPPER = new RowMapper<>() {
        @Override
        public IdempotencyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new IdempotencyRecord(
                    rs.getObject("idempotency_key", UUID.class),
                    rs.getString("operation"),
                    rs.getString("request_hash"),
                    (Integer) rs.getObject("response_status"),
                    rs.getString("response_body"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("completed_at", OffsetDateTime.class)
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<IdempotencyRecord> findByKey(UUID idempotencyKey) {
        List<IdempotencyRecord> records = jdbcTemplate.query(
                """
                select idempotency_key, operation, request_hash, response_status, response_body, created_at, completed_at
                from idempotency_records
                where idempotency_key = ?
                """,
                ROW_MAPPER,
                idempotencyKey
        );
        return records.stream().findFirst();
    }

    public void reserve(UUID idempotencyKey, String operation, String requestHash, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                insert into idempotency_records (idempotency_key, operation, request_hash, created_at)
                values (?, ?, ?, ?)
                """,
                idempotencyKey,
                operation,
                requestHash,
                createdAt
        );
    }

    public void complete(UUID idempotencyKey, int responseStatus, String responseBody, OffsetDateTime completedAt) {
        jdbcTemplate.update(
                """
                update idempotency_records
                set response_status = ?, response_body = ?, completed_at = ?
                where idempotency_key = ?
                """,
                responseStatus,
                responseBody,
                completedAt,
                idempotencyKey
        );
    }
}

