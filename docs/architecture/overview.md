# LEDGER — Architecture Overview

See `docs/adr/` for the reasoning behind each decision referenced here.

## Schema

```
accounts
  id            UUID PRIMARY KEY
  name          TEXT NOT NULL
  currency      CHAR(3) NOT NULL          -- ISO 4217
  account_type  TEXT NOT NULL             -- asset | liability | equity | revenue | expense
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()

transactions
  id               UUID PRIMARY KEY
  idempotency_key  TEXT NOT NULL UNIQUE   -- ADR-003: enforcement point, not app-level check
  description      TEXT
  status           TEXT NOT NULL          -- posted | reversed
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()

ledger_entries
  id              UUID PRIMARY KEY
  transaction_id  UUID NOT NULL REFERENCES transactions(id)
  account_id      UUID NOT NULL REFERENCES accounts(id)
  amount          NUMERIC(19,4) NOT NULL  -- signed: positive = debit, negative = credit
  currency        CHAR(3) NOT NULL
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
  -- INDEX (account_id, created_at) — powers balance-history / regulatory-report queries

account_balances                          -- ADR-002: transactionally-maintained read cache
  account_id  UUID NOT NULL REFERENCES accounts(id)
  currency    CHAR(3) NOT NULL
  balance     NUMERIC(19,4) NOT NULL
  updated_at  TIMESTAMPTZ NOT NULL
  PRIMARY KEY (account_id, currency)
```

The double-entry invariant — `ledger_entries` for a given `transaction_id` must sum to zero per currency — is enforced at the service layer inside the same DB transaction as the insert, not by a database `CHECK` constraint (Postgres `CHECK` constraints can't reference other rows). This is a deliberate, named gap: a database-level trigger enforcing the invariant is a real hardening option for a future pass, tracked in `docs/RISKS.md` rather than silently assumed to be equivalent to what's actually built.

## Transaction submission flow

1. Client `POST /transactions` with a body of `{ idempotencyKey, description, entries: [{ accountId, amount, currency }, ...] }`.
2. `TransactionService.submit()` does a fast-path read on `idempotency_key`; if a matching transaction already exists, it returns that result immediately without touching the write path.
3. Otherwise it delegates to `TransactionWriter.write()` — a separate `@Component` bean, deliberately not a method on `TransactionService` itself, so that Spring's `@Transactional` AOP proxy actually intercepts the call (a same-class self-invocation would silently bypass the proxy and run outside any transaction). Inside a single database transaction:
   a. Validate the entries sum to zero per currency (application-level check — Postgres `CHECK` constraints can't reference other rows).
   b. For each entry, load its `Account` and confirm the entry's currency matches the account's currency (accounts are single-currency by design).
   c. Insert into `transactions` (the `UNIQUE` constraint on `idempotency_key` is the actual enforcement point — see ADR-003).
   d. Insert all `ledger_entries` rows for this transaction.
   e. For each distinct `(account_id, currency)` touched, sorted into a fixed order (by account id, then currency) to prevent deadlocks between concurrent transactions touching overlapping accounts, `SELECT ... FOR UPDATE` its `account_balances` row (row-level lock, preventing lost updates from concurrent writes to the same account — see ADR-002's "Risks"), then apply the net delta.
4. If the insert in 3c races with a concurrent submission of the same idempotency key and loses (`DataIntegrityViolationException` from the unique constraint), `TransactionService` catches it and falls back to fetching and returning the already-committed result, instead of erroring.
5. Commit. Return the transaction result, along with whether this call created it (HTTP 201) or a prior call already had (HTTP 200).

Every account is created with a zero-balance `account_balances` row in the same transaction as the account itself (`AccountService.createAccount()`), so step 3e's `FOR UPDATE` always has an existing row to lock — no transaction is ever "first" to touch a given account's balance row.

## Balance / history reads

- `GET /accounts/{id}/balance` — single indexed lookup on `account_balances`, O(1) regardless of transaction history depth.
- `GET /accounts/{id}/transactions` — paginated query over `ledger_entries` filtered by `account_id`, ordered by `created_at`, using the `(account_id, created_at)` index — this is the "regulatory reporting" style query path.

## What's built

Entities, repositories, services, and controllers implementing the flow above all exist (`src/main/java/xyz/zyxwonderland/ledger/`), backed by a Flyway migration (`V1__init_schema.sql`) matching the schema above. `TransactionServiceIntegrationTest` and `TransactionServiceConcurrencyTest` (Testcontainers-backed, real `postgres:16-alpine`) prove the sequential and concurrent-correctness claims respectively — see ADR-004 for why Testcontainers rather than the originally-planned Neon-branch-per-CI-run.

## Explicitly not built

No database-level trigger enforcing the double-entry invariant (currently service-layer only, per the note above — a real hardening option for a future pass, tracked in `docs/RISKS.md`). No deployment to Neon/Fly.io yet. No message-broker-based ingestion pipeline (out of scope per ADR-003).
