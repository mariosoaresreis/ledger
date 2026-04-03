create table if not exists accounts (
    id uuid primary key,
    owner_id uuid not null,
    status varchar(20) not null,
    currency varchar(3) not null,
    created_at timestamp with time zone not null
);

create table if not exists ledger_events (
    id uuid primary key,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    payload jsonb not null,
    version bigint not null,
    occurred_at timestamp with time zone not null,
    idempotency_key uuid not null,
    constraint uq_ledger_events_aggregate_version unique (aggregate_id, version)
);

create table if not exists outbox (
    id uuid primary key,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    payload jsonb not null,
    published_at timestamp with time zone null
);

create table if not exists idempotency_records (
    idempotency_key uuid primary key,
    operation varchar(100) not null,
    request_hash varchar(64) not null,
    response_status integer null,
    response_body text null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone null
);

create index if not exists idx_ledger_events_aggregate on ledger_events (aggregate_id, occurred_at);
create index if not exists idx_outbox_published_at on outbox (published_at);

