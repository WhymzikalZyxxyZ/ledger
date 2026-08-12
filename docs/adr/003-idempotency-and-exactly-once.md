# ADR-003: Idempotency & Exactly-Once Semantics

**Date:** 2026-08-12
**Status:** Accepted
**Deciders:** Zyxxyz

---

## Context

"Strict reliability" for transaction ingestion specifically means: a client that retries a submission (because a network call timed out, because a load balancer dropped the response, because *anything*) must never cause the same transaction to post twice. Real payment/ledger systems live or die on getting this exactly right — it's one of the more interview-relevant details a senior backend engineer is expected to get correct without prompting.

## Decision Drivers

- Retries are a given in any real distributed system — the design has to assume they'll happen, not treat them as an edge case
- "Exactly-once" is a term that gets used loosely; this ADR needs to be precise about what it actually means here
- The enforcement mechanism needs to be race-condition-proof under real concurrency, not just correct in the common case

## Options Considered

### Option A — Application-level check-then-act (query for existing idempotency key, insert if not found)
The API checks whether a transaction with the given idempotency key already exists before inserting a new one.

**Pros:** Simple to write.
**Cons:** A textbook TOCTOU (time-of-check-to-time-of-use) race: two concurrent requests with the same idempotency key can both pass the check before either has inserted, producing a duplicate. This is exactly the kind of subtle correctness bug a senior-level review should catch — using it here would undermine the point of the whole project.

### Option B — Database-level unique constraint on idempotency key, with the insert itself as the enforcement point
`transactions.idempotency_key` is a `UNIQUE NOT NULL` column. Submission always attempts the insert; a unique-constraint violation means "this was already processed," and the API returns the original transaction's result rather than erroring.

**Pros:** Race-condition-proof by construction — PostgreSQL's unique constraint is enforced atomically at the storage layer, so there's no window between "check" and "act" for two concurrent identical requests to both slip through. The database does the correctness work instead of application code trying to.
**Cons:** Requires the API layer to specifically catch the constraint-violation case and turn it into a "here's your original result" response rather than a generic 500 — a deliberate design point, not an incidental one.

## Decision

**Chosen option: Option B — a database-level unique constraint on `idempotency_key`, with duplicate-insert handling as first-class API behavior**, not an afterthought or an error case.

**What "exactly-once" means here, precisely:** this system guarantees exactly-once *effect* (a given idempotency key results in exactly one posted transaction, no matter how many times the request is retried) — not exactly-once *delivery* (which would require guarantees about the network/transport layer this API doesn't control). This distinction matters and is stated explicitly rather than left implied, since "exactly-once" is one of the most commonly overclaimed terms in distributed systems.

## Consequences

**Positive:**
- Correct under real concurrency by construction, not by careful application-code discipline that a future change could quietly break
- Clients can safely retry on any ambiguous failure (timeout, connection reset) without a "did that actually post?" gap — they resubmit with the same idempotency key and get the same answer either way

**Negative / accepted tradeoffs:**
- Requires clients to generate and track idempotency keys themselves — a real API design constraint, not free
- The unique constraint means idempotency keys are permanent (or need an explicit retention/expiry policy) — not yet decided, tracked as an open item in `docs/RISKS.md`

**Risks:**
- No message-broker-based ingestion pipeline exists (no Kafka/SQS-equivalent) — this project demonstrates exactly-once *effect* at the API layer, not a full event-streaming ingestion architecture. That's a real, larger system this doesn't claim to be; noted explicitly rather than implied by omission.

## Notes

- See [ADR-004](004-correctness-verification.md) for how this gets proven under concurrent load, not just designed correctly on paper.
