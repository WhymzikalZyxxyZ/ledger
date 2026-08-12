-- See docs/architecture/overview.md for the full design and docs/adr/002
-- for why each of these choices was made.

CREATE TABLE accounts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT NOT NULL,
    currency     CHAR(3) NOT NULL,
    account_type TEXT NOT NULL
        CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- The actual idempotency enforcement point (ADR-003) — an insert that
    -- violates this constraint means "already processed," handled as a
    -- first-class case in TransactionService, not treated as a generic error.
    idempotency_key  TEXT NOT NULL UNIQUE,
    description      TEXT,
    status           TEXT NOT NULL DEFAULT 'POSTED'
        CHECK (status IN ('POSTED', 'REVERSED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    -- Signed: positive = debit, negative = credit. Entries for a single
    -- transaction must net to zero per currency — enforced in the service
    -- layer inside the same DB transaction as this insert (see
    -- docs/RISKS.md — a DB-level trigger enforcing this too is a named,
    -- not-yet-built hardening step, not silently assumed equivalent).
    amount          NUMERIC(19, 4) NOT NULL,
    currency        CHAR(3) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Powers both the balance-history query path and the "regulatory reporting"
-- style range queries described in docs/architecture/overview.md.
CREATE INDEX idx_ledger_entries_account_created
    ON ledger_entries (account_id, created_at);

-- ADR-002: a transactionally-maintained read cache, not a second source of
-- truth — always written inside the same DB transaction as the entries that
-- produced it, so it can never observably diverge from ledger_entries.
CREATE TABLE account_balances (
    account_id  UUID NOT NULL REFERENCES accounts(id),
    currency    CHAR(3) NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, currency)
);
