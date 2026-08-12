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
Tests run against an actual Postgres database (a Neon branch for CI, matching ADR-001's hosting choice) and include tests that fire N concurrent requests at the same account/idempotency key and assert the invariants still hold afterward.

**Pros:** Tests the actual thing being claimed — real row-level locking, real unique-constraint behavior, real transaction isolation — not a simulation of it. Neon's branching feature (a fresh, isolated Postgres branch per CI run) fits this without needing to stand up throwaway Postgres containers in CI.
**Cons:** Slower and more infrastructure-dependent than Option A; requires care that concurrency tests are deterministic enough not to be flaky (a real risk with any test that intentionally races operations against each other).

## Decision

**Chosen option: Option B — integration tests against real PostgreSQL, including deliberate concurrency-provoking tests**, once the schema and API exist to test. Unit tests with mocks still have a place for pure business logic that doesn't touch the database, but they are not the mechanism that proves the concurrency claims — only Option B is.

## Consequences

**Positive:**
- The specific claims in ADR-002 (no lost updates on concurrent balance writes) and ADR-003 (no duplicate posts under concurrent identical idempotency keys) get a test that would actually fail if either were broken, not just a design document asserting they're fine
- Reusing Neon (already chosen in ADR-001) for CI branches avoids introducing a second database dependency just for testing

**Negative / accepted tradeoffs:**
- Slower CI than pure unit tests — accepted, since speed isn't the point here, correctness under the exact conditions that break naive implementations is
- Concurrency tests are inherently harder to write deterministically than sequential ones; will need real care to avoid flakiness undermining trust in the suite itself

**Risks:**
- **Not built yet.** This ADR documents the target verification strategy — no entities, controllers, or tests exist in this repository yet (see `docs/RISKS.md`). Until the concurrency tests described here actually exist and pass, ADR-002's and ADR-003's correctness claims are design intent, not proven fact, and should be read that way.

## Notes

- This is arguably the most important ADR in the repo precisely because it's the one whose promise isn't fulfilled yet — the next implementation pass should treat writing these concurrency tests as inseparable from writing the entities/controllers themselves, not a follow-up task that could slip.
