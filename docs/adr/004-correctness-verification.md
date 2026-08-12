# ADR-004: Correctness Verification Strategy

**Date:** 2026-08-12
**Status:** Accepted
**Deciders:** Zyxxyz

---

## Context

ADR-002 and ADR-003 both make specific concurrency-correctness claims (the double-entry invariant holds under concurrent writes; idempotency holds under concurrent retries). An ADR asserting a design is correct is not the same thing as proving it — and for a project whose entire purpose is demonstrating rigor around financial correctness, the gap between "designed to be correct" and "tested under the exact conditions that break naive implementations" is the whole point.

## Decision Drivers

- Unit tests of individual methods in isolation cannot catch race conditions — they need to be actively provoked with real concurrent execution to be caught
- The tests should target the specific failure modes each ADR's decision was chosen to prevent (lost updates on `account_balances`, duplicate posts from concurrent identical idempotency keys), not just exercise the happy path
- Verification needs to run against a real PostgreSQL instance — the concurrency guarantees being tested are PostgreSQL's actual locking/isolation behavior, not something a mocked repository layer could ever validate

## Options Considered

### Option A — Standard unit tests with a mocked repository layer
Test service-layer logic in isolation, mocking the database.

**Pros:** Fast, no infrastructure dependency, standard practice for most application logic.
**Cons:** Structurally incapable of catching the actual risk here — a mock can't race against itself. Would give a false sense of correctness on exactly the claims that matter most for this project.

### Option B — Integration tests against a real PostgreSQL instance, including concurrent-execution tests that deliberately provoke races
Tests run against an actual Postgres database and include tests that fire N concurrent requests at the same account/idempotency key and assert the invariants still hold afterward. Originally scoped as a Neon branch per CI run, matching ADR-001's hosting choice; implemented instead with **Testcontainers** spinning up a disposable `postgres:16-alpine` container per test run (see Decision).

**Pros:** Tests the actual thing being claimed — real row-level locking, real unique-constraint behavior, real transaction isolation — not a simulation of it.
**Cons:** Slower and more infrastructure-dependent than Option A; requires care that concurrency tests are deterministic enough not to be flaky (a real risk with any test that intentionally races operations against each other).

## Decision

**Chosen option: Option B — integration tests against real PostgreSQL, including deliberate concurrency-provoking tests**, backed by **Testcontainers** rather than the Neon-branch-per-CI-run approach originally sketched in this ADR. Unit tests with mocks still have a place for pure business logic that doesn't touch the database, but they are not the mechanism that proves the concurrency claims — only Option B is.

The Neon-branch plan assumed CI would authenticate against a Neon account to provision a branch per run; that account wiring was never available in the environment this repo was built in. Testcontainers was substituted because it needs no external account or credentials — it only needs Docker, which GitHub Actions' hosted runners already have preinstalled — while still exercising the same real PostgreSQL engine (`postgres:16-alpine`) that production runs on, which is what these tests actually need to prove. Since Testcontainers gives every CI run its own throwaway, fully isolated database with zero external dependency, it is a reasonable long-term choice on its own merits, not just a workaround — a future move to Neon branches for CI would be about matching the exact hosted-Postgres version/config more closely, not about correctness.

## Consequences

**Positive:**
- The specific claims in ADR-002 (no lost updates on concurrent balance writes) and ADR-003 (no duplicate posts under concurrent identical idempotency keys) are proven by `TransactionServiceConcurrencyTest`, which fires 20 concurrent submissions at both scenarios and asserts the invariants hold — not just a design document asserting they're fine
- Testcontainers requires no external account, no secrets in CI, and no dependency on Neon-specific branching semantics — a CI run is fully self-contained and reproducible on any machine with Docker
- `TransactionServiceIntegrationTest` covers the sequential happy-path and validation cases against the same real-Postgres base class (`AbstractIntegrationTest`)

**Negative / accepted tradeoffs:**
- Slower CI than pure unit tests — accepted, since speed isn't the point here, correctness under the exact conditions that break naive implementations is
- Concurrency tests are inherently harder to write deterministically than sequential ones; addressed with a `CountDownLatch` releasing all threads simultaneously to maximize contention, and a per-account `SELECT ... FOR UPDATE` lock-ordering scheme (sorted by account id) in `TransactionWriter` to keep the same tests deadlock-free
- Testcontainers-based CI doesn't validate against Neon's specific Postgres build/config the way a Neon-branch approach would have — accepted as a minor gap, since the properties under test (row locking, unique constraints, transaction isolation) are standard PostgreSQL engine behavior, not Neon-specific

**Risks:**
- None outstanding for this ADR. The concurrency tests described here exist, run in CI, and pass — ADR-002's and ADR-003's correctness claims are now backed by tests that would fail if either were broken, not just design intent. See `docs/RISKS.md` for what remains out of scope (e.g., no independent security/correctness audit).

## Notes

- This was the most important ADR in the repo to fulfill, precisely because it was the one whose promise wasn't met yet at design time — the implementation pass treated writing these concurrency tests as inseparable from writing the entities/controllers themselves, per the original intent below.
