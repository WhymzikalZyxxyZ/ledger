# ADR-002: Data Model & Consistency Strategy

**Date:** 2026-08-12
**Status:** Accepted
**Deciders:** Zyxxyz

---

## Context

A ledger's entire credibility rests on one invariant: money never appears or disappears, it only moves between accounts. Every design decision here is downstream of protecting that invariant under real concurrent load, while also answering the specific claim this project exists to prove: "PostgreSQL schema design and query optimization for financial analytics."

## Decision Drivers

- The double-entry invariant (every transaction's entries net to zero per currency) must be enforced by the schema and transaction boundary, not just application-level discipline that a future bug could quietly violate
- Balance reads need to be fast at scale — this is where "query optimization" actually gets demonstrated, not just claimed
- Schema must support "financial analytics and regulatory reporting" style queries (historical entries by account, by date range) without requiring a separate analytics pipeline for a project this size

## Options Considered

### Option A — Three-table model: `accounts`, `transactions`, `ledger_entries`; balance computed on read
`ledger_entries` is the append-only source of truth (transaction_id, account_id, amount, currency). Balance for an account is `SUM(amount)` over its entries at query time.

**Pros:** Simplest possible model — one source of truth, no risk of a cached balance drifting from the entries that produced it. Full history always available directly from the entries table.
**Cons:** Balance reads get slower as an account accumulates history — exactly the "doesn't scale" problem `mend`'s and `chart`'s own risk registers warn against pretending doesn't exist. Wrong choice if balance reads are the hot path, which for a ledger they always are.

### Option B — Add a maintained `account_balances` table, updated transactionally alongside entry inserts
Same three tables as Option A, plus a `account_balances(account_id, currency, balance, updated_at)` row per account/currency pair, updated inside the same DB transaction that inserts the entries.

**Pros:** O(1) balance reads via a single indexed row lookup instead of scanning/summing history — this is the actual "query optimization" decision, not a vague claim. Still keeps `ledger_entries` as the append-only audit trail for history/regulatory-style queries; the balance table is a derived, transactionally-consistent cache, not a second source of truth that can silently diverge (it's updated in the same transaction as the entries, so it can't observably drift under PostgreSQL's transactional guarantees).
**Cons:** More schema and more write-path complexity than Option A. Requires `SELECT ... FOR UPDATE` (row-level locking) on the balance row during concurrent writes to the same account to prevent lost updates — a real concurrency-correctness detail that has to be gotten right, not just declared.

### Option C — Event-sourced with a separate read-model projection (CQRS-style)
Entries are the write-side event log; a fully separate, asynchronously-updated read model serves balance/analytics queries.

**Pros:** Scales furthest, cleanly separates write and read concerns, closest to how a very large real system might actually be built.
**Cons:** Asynchronous projection means the read model can lag the write model — direct tension with "strict reliability" if a balance can be briefly stale right after a transaction posts. Meaningfully more infrastructure (a projection mechanism, eventual-consistency handling) than a project at this scale needs to prove the point.

## Decision

**Chosen option: Option B — `ledger_entries` as the append-only source of truth, plus a transactionally-maintained `account_balances` table for O(1) reads.** This is the option that actually answers "query optimization for financial analytics" with a concrete mechanism (an indexed, transactionally-consistent balance row) rather than either ignoring the read-scaling problem (Option A) or reaching for more infrastructure than a project this size needs to justify (Option C).

## Consequences

**Positive:**
- Balance reads are O(1) via an indexed lookup, not O(n) over transaction history
- `account_balances` can never observably diverge from `ledger_entries`, because both are written in the same DB transaction — PostgreSQL's ACID guarantees do the consistency work, not application-level reconciliation logic
- `ledger_entries` remains a clean, append-only audit trail for the "regulatory reporting" style queries (by account, by date range) without any separate analytics pipeline

**Negative / accepted tradeoffs:**
- Every transaction write touches two tables instead of one, and requires row-level locking (`SELECT ... FOR UPDATE`) on the balance row to stay correct under concurrent writes to the same account — more write-path complexity than Option A, accepted because read-path performance is the actual point being demonstrated
- `account_balances` needs a backfill/reconciliation story if it were ever manually edited or corrupted — not a risk under normal operation (it's only ever written transactionally alongside entries) but worth naming

**Risks:**
- Row-level locking on `account_balances` under high concurrent write volume to the *same* account is a real contention point — this needs to be load-tested, not just asserted, before any claim about "high-throughput" is fully earned. Tracked in `docs/RISKS.md`.

## Notes

- See [ADR-003](003-idempotency-and-exactly-once.md) for how transaction submission avoids double-posting under retries.
- See [ADR-004](004-correctness-verification.md) for how the concurrent-write correctness claim above actually gets tested, not just designed.
