# LEDGER — Risk & Gap Register

Living document. Update as decisions are made or new risks surface.

## This is a portfolio demonstration, not audited financial infrastructure — CRITICAL

Everything in this repository exists to demonstrate engineering competence, not to move real money. This is the single most important entry in this document, same posture as this portfolio's other risk registers (MEND's allergen-data caution, CHART's synthetic-data-only boundary).

**Mitigation stance:** the README must state this plainly, not bury it. **This system must never process real financial transactions or be represented as production-ready without a real security review, a real correctness audit beyond this project's own test suite, and almost certainly regulatory/compliance review that is entirely out of scope for a personal project.** This isn't a someday caveat — pointing this at real money without that review would be a serious problem, not a technical nitpick.

## Double-entry invariant enforced at the service layer, not the database layer — MEDIUM (accepted gap)

Per `docs/architecture/overview.md`, the "entries sum to zero" invariant is checked in application code inside the transaction boundary, not by a Postgres `CHECK` constraint (which can't reference sibling rows) or a trigger.

**Mitigation stance:** accepted for now — the application-level check runs inside the same DB transaction as the writes, so it's still atomic with respect to what actually gets committed. **Gap:** a database-level trigger as defense-in-depth (so the invariant holds even against a future bug in application code, or a direct SQL write that bypasses the service layer) is a real hardening step not yet built. Worth doing before this is ever treated as anything more than a demonstration.

## Concurrency correctness is now proven by tests, not just designed — RESOLVED (was HIGH)

ADR-002 and ADR-003 both make specific claims about correctness under concurrent load (no lost balance updates, no duplicate posts under concurrent identical idempotency keys). ADR-004 committed to proving these with real concurrent-execution tests against actual PostgreSQL.

**Mitigation stance:** **Resolved.** `TransactionServiceConcurrencyTest` fires 20 concurrent transactions at both scenarios (same account, same idempotency key) against a real Testcontainers-backed PostgreSQL instance and asserts the invariants hold. This is still a test suite, not an independent audit — see the CRITICAL entry above — but the specific concurrency claims are no longer unverified design intent.

## Testcontainers substituted for Neon-branch-per-CI-run — LOW (documented divergence)

ADR-004 originally scoped CI concurrency tests against a Neon branch provisioned per run. That was never wired up because this repository was built without access to a Neon account/credentials in the working environment.

**Mitigation stance:** accepted, and arguably an improvement — Testcontainers needs no external account or secrets, only Docker (preinstalled on GitHub Actions hosted runners), and still runs against the real `postgres:16-alpine` engine, so the properties under test (row locking, unique constraints, isolation) are unaffected. See [ADR-004](adr/004-correctness-verification.md) for the full reasoning. Revisiting Neon branches for CI would be about matching the exact hosted-Postgres build more closely, not about correctness.

## Free-tier limits (Neon, Fly.io) — LOW

Same accepted tradeoff as every other free-tier dependency in this portfolio (Overpass in MEND, the SMART sandbox in CHART) — no SLA, could throttle or change terms.

**Mitigation stance:** none needed beyond awareness; acceptable at demo scale.

## Idempotency-key lifecycle undecided — MEDIUM (open item)

`transactions.idempotency_key` is `UNIQUE` forever, with no retention or expiry policy decided yet. A real system needs an answer for whether/when keys can be reused or archived.

**Mitigation stance:** **Gap, not yet resolved.** Not blocking for the documentation phase, but needs an answer before this claims to model a real production ingestion API.

## No message-broker-based ingestion pipeline — LOW (explicitly out of scope, not a hidden gap)

ADR-003 is explicit: this demonstrates exactly-once *effect* at a synchronous REST API layer, not a full asynchronous event-streaming ingestion architecture (no Kafka/SQS-equivalent). A real "real-time cash flow" system at Amex's scale almost certainly involves one.

**Mitigation stance:** accepted scope boundary for a project sized to prove the point, not replicate an entire production system. Named explicitly in the README so it reads as a deliberate scope decision, not an oversight.
