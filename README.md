# Ledger command service

Spring Boot 21 command-side implementation of a CQRS ledger system.

## What is included
- Command/write API only
- PostgreSQL-backed `accounts`, `ledger_events`, `outbox`, and `idempotency_records`
- Optimistic locking through `(aggregate_id, version)` uniqueness in the event store
- Idempotent mutating endpoints using the `Idempotency-Key` header
- Transfer command implemented as a saga-style write flow that records `TRANSFER_INITIATED`, `ACCOUNT_DEBITED`, and `ACCOUNT_CREDITED`
- Docker image and local `compose.yaml`
- Kubernetes manifests under `k8s/`

## What is intentionally not included
- Query/read side of CQRS
- Kafka consumer projections
- Debezium / CDC relay implementation

The write side is prepared for an outbox relay: every accepted command writes both the immutable event and an outbox row in the same transaction.

## API
All mutating endpoints require `Idempotency-Key: <uuid>`.

- `POST /api/v1/accounts`
- `POST /api/v1/accounts/{accountId}/credits`
- `POST /api/v1/accounts/{accountId}/debits`
- `POST /api/v1/transfers`
- `PATCH /api/v1/accounts/{accountId}/status`

## Local development
Run tests:

```bash
./mvnw test
```

Run the service locally against your own PostgreSQL/Kafka/Redis on localhost default ports:

```bash
./mvnw spring-boot:run
```

Start the full local stack with containers:

```bash
docker compose up --build
```

## Example requests
Create an account:

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-1111-1111-1111-111111111111' \
  -d '{"ownerId":"22222222-2222-2222-2222-222222222222","currency":"USD"}'
```

Credit an account:

```bash
curl -X POST http://localhost:8080/api/v1/accounts/<account-id>/credits \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 33333333-3333-3333-3333-333333333333' \
  -d '{"amount":100.00,"currency":"USD","reference":"initial-funding"}'
```

## Kubernetes
Apply the manifests in this order:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/kafka.yaml
kubectl apply -f k8s/app.yaml
```

Build and load/push the app image before applying `k8s/app.yaml`.

