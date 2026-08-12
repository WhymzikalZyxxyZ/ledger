# LEDGER — Risk & Gap Register

Living document. Update as decisions are made or new risks surface.

## This is a portfolio demonstration, not audited financial infrastructure — CRITICAL

Everything in this repository exists to demonstrate engineering competence, not to move real money. This is the single most important entry in this document, same posture as this portfolio's other risk registers (MEND's allergen-data caution, CHART's synthetic-data-only boundary).

**Mitigation stance:** the README must state this plainly, not bury it. **This system must never process real financial transactions or be represented as production-ready without a real security review, a real correctness audit beyond this project's own test suite, and almost certainly regulatory/compliance review that is entirely out of scope for a personal project.** This isn't a someday caveat — pointing this at real money without that review would be a serious problem, not a technical nitpick.

## Double-entry invariant enforced at the service layer, not the database layer — MEDIUM (accepted gap)

Per `docs/architecture/overview.md`, the "entries sum to zero" invariant is checked in application code inside the transaction boundary, not by a Postgres `CHECK` constraint (which can't reference sibling rows) or a trigger.

**Mitigation stance:** accepted for now — the application-level check runs inside the same DB transaction as the writes, so it's still atomic with respect to what actually gets committed. **Gap:** a database-level trigger as defense-in-depth (so the invariant holds even against a future bug in application code, or a direct SQL write that bypasses the service layer) is a real hardening step not yet built. Worth doing before this is ever treated as anything more than a demonstration.

## Concurrency correctness is designed but not yet proven — HIGH (tracked explicitly, not hidden)

ADR-002 and ADR-003 both make specific claims about correctness under concurrent load (no lost balance updates, no duplicate posts under concurrent identical idempotency keys). ADR-004 commits to proving these with real concurrent-execution tests against actual PostgreSQL.

**Mitigation stance:** **as of this documentation phase, those tests don't exist yet, because no entities/controllers exist yet either.** Until they're written and passing, treat the concurrency claims in ADR-002/003 as design intent, not verified fact. This is stated explicitly here specifically so it can't be quietly forgotten once implementation starts.

## Free-tier limits (Neon, Fly.io) — LOW

Same accepted tradeoff as every other free-tier dependency in this portfolio (Overpass in MEND, the SMART sandbox in CHART) — no SLA, could throttle or change terms.

**Mitigation stance:** none needed beyond awareness; acceptable at demo scale.

## Idempotency-key lifecycle undecided — MEDIUM (open item)

`transactions.idempotency_key` is `UNIQUE` forever, with no retention or expiry policy decided yet. A real system needs an answer for whether/when keys can be reused or archived.

**Mitigation stance:** **Gap, not yet resolved.** Not blocking for the documentation phase, but needs an answer before this claims to model a real production ingestion API.

## No message-broker-based ingestion pipeline — LOW (explicitly out of scope, not a hidden gap)

ADR-003 is explicit: this demonstrates exactly-once *effect* at a synchronous REST API layer, not a full asynchronous event-streaming ingestion architecture (no Kafka/SQS-equivalent). A real "real-time cash flow" system at Amex's scale almost certainly involves one.

**Mitigation stance:** accepted scope boundary for a project sized to prove the point, not replicate an entire production system. Named explicitly in the README so it reads as a deliberate scope decision, not an oversight.
