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
2. Service validates the entries sum to zero per currency (application-level check, before touching the database).
3. A single database transaction:
   a. Insert into `transactions` (the `UNIQUE` constraint on `idempotency_key` is the actual enforcement point — see ADR-003). If this insert fails on a unique-constraint violation, the transaction already existed: fetch and return its original result instead of erroring.
   b. Insert all `ledger_entries` rows for this transaction.
   c. For each distinct `account_id` touched, `SELECT ... FOR UPDATE` its `account_balances` row (row-level lock, preventing lost updates from concurrent writes to the same account — see ADR-002's "Risks"), then update the balance by the entry amount.
4. Commit. Return the transaction result.

## Balance / history reads

- `GET /accounts/{id}/balance` — single indexed lookup on `account_balances`, O(1) regardless of transaction history depth.
- `GET /accounts/{id}/transactions` — paginated query over `ledger_entries` filtered by `account_id`, ordered by `created_at`, using the `(account_id, created_at)` index — this is the "regulatory reporting" style query path.

## Explicitly not built yet

No entities, repositories, or controllers exist in this repository yet — this document describes the target design the ADRs commit to, not current functionality. No database-level trigger enforcing the double-entry invariant (currently service-layer only, per the note above). No concurrency tests yet (ADR-004 commits to writing them; they don't exist). No deployment to Neon/Fly.io yet.
