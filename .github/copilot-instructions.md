# Copilot Instructions for `ledger`

## Mission
This repository is a **CQRS write-side ledger command service** built with Spring Boot (Java 21).

Prioritize correctness, data consistency, and backward-compatible API behavior over refactoring style.

## Architecture Guardrails (Non-Negotiable)
- Implement **command-side only** behavior.
- Do **not** add query/read models, query endpoints, projection consumers, or read-service logic.
- Keep business writes in the command flow (`controller -> service -> repositories`) with clear transactional boundaries.
- Keep immutable event logging (`ledger_events`) and outbox (`outbox`) writes in the same command transaction.
- Preserve idempotency handling for all mutating endpoints using `Idempotency-Key`.

## API Contract Rules
- Keep existing endpoint paths under `/api/v1` unless explicitly requested.
- Keep mutating endpoints requiring `Idempotency-Key`.
- Preserve current response envelope (`CommandResponse`) and ProblemDetail error style.
- Prefer additive, backward-compatible changes to request/response payloads.
- Do not silently change HTTP status codes for existing success/error paths.

## Domain and Consistency Rules
- Enforce account state invariants (`ACTIVE`, `FROZEN`, `CLOSED`) and balance checks.
- Maintain optimistic versioning semantics in `ledger_events` (`aggregate_id`, `version`).
- Never bypass event append logic with direct balance/materialized-state writes.
- Avoid dual-write behavior; use outbox writes together with event append.
- Keep transfer flow eventful (`TRANSFER_INITIATED`, debit, credit), never as a hidden direct balance update.

## Persistence and Migration Rules
- Database changes must go through Flyway migrations in `src/main/resources/db/migration`.
- Do not edit old migration files after they are committed; add new migrations.
- Keep SQL compatible with existing local/test strategy (PostgreSQL + H2 compatibility migration).
- Avoid introducing JPA entities for ledger core writes; this project is JDBC-based for write control.

## Testing Expectations
- For non-trivial command behavior, add or update integration tests in `src/test/java/com/marioreis/ledger`.
- Validate:
  - API status + payload contract
  - event store writes
  - outbox writes
  - idempotency replay behavior
  - business invariant failures
- Keep tests deterministic and isolated (database cleanup per test).
- Ensure Swagger UI endpoint remains available at `/swagger-ui/index.html`.

## Code Style Preferences
- Keep classes focused and explicit; avoid framework-heavy magic.
- Prefer clear names over compact code.
- Use records for DTO-like immutable payloads where already idiomatic.
- Keep comments minimal and high-value only.
- Do not introduce new libraries unless they are justified by a concrete requirement.

## Deployment and Runtime Constraints
- Preserve localhost defaults for local dependencies (PostgreSQL 5432, Kafka 9092, Redis 6379).
- Keep Docker and Kubernetes manifests aligned with application configuration.
- Do not add infrastructure components that imply a read-side service unless explicitly requested.

## Forbidden Changes (Unless Explicitly Requested)
- Adding query-side CQRS service/endpoints/projections.
- Removing idempotency checks or changing key behavior.
- Replacing event-store + outbox writes with direct state mutations.
- Breaking existing API paths, status codes, or payload shapes.
- Removing integration tests that cover command-side invariants.

## When Unsure
- Choose the safer, more explicit implementation.
- Preserve existing behavior and add tests first.
- Ask for clarification only when a change would alter API or consistency guarantees.
