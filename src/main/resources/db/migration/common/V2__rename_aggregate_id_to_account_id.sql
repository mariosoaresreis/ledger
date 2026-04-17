-- Rename aggregate_id to account_id in ledger_events
alter table ledger_events rename column aggregate_id to account_id;

-- Rename unique constraint to keep naming consistent
alter table ledger_events rename constraint uq_ledger_events_aggregate_version to uq_ledger_events_account_version;

-- Rename index to keep naming consistent
alter index idx_ledger_events_aggregate rename to idx_ledger_events_account;

-- Rename aggregate_id to account_id in outbox
alter table outbox rename column aggregate_id to account_id;

