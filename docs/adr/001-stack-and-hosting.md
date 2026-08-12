# ADR-001: Stack & Hosting

**Date:** 2026-08-12
**Status:** Accepted
**Deciders:** Zyxxyz

---

## Context

LEDGER exists specifically to demonstrate one claim: designing high-throughput, reliable backend services for real-time transaction data with PostgreSQL schema design and query optimization behind them. The stack choice isn't neutral here — it's part of what's being proven, since it should credibly resemble how this kind of system actually gets built in production financial-services environments, not just be "a language I know."

## Decision Drivers

- Must directly and literally match skills already claimed elsewhere (resume, this portfolio) rather than introduce a new, unverifiable claim
- Free to run — no paid infrastructure required to build, test, or demo
- Should resemble a credible enterprise financial-services stack, not a hobbyist choice, since the audience for this specific piece is technical reviewers evaluating fintech/backend seniority

## Options Considered

### Option A — Java + Spring Boot
The most common stack for enterprise financial-services backends; also explicitly listed on the resume this project exists to back up.

**Pros:** The most literal possible match to the claim being demonstrated — "I can build the kind of system a bank actually runs," not an approximation of it. Spring Data JPA and Spring's transaction management are mature, well-understood tools for exactly this domain (transactional consistency, repository patterns).
**Cons:** More ceremony/boilerplate than Go or Kotlin for the same functionality; a slower "hello world" than either alternative.

### Option B — Go
Also on the resume; well-suited to the "high-throughput, strict latency" framing specifically, and several real payment/exchange platforms are Go-heavy in practice.

**Pros:** Excellent concurrency primitives (goroutines, channels) map naturally onto "process transactions reliably under load." Smaller, faster builds than JVM-based options.
**Cons:** Less literally tied to "Spring Boot" as a named resume skill; a strong but slightly less direct match to the specific claim being proven.

### Option C — Kotlin
Consistent with this portfolio's two most recent pieces (MEND, CHART).

**Pros:** Reuses familiar tooling/conventions from recent work.
**Cons:** Weakest match of the three to "enterprise financial-services backend" as a genre — Kotlin backend services exist, but it's a less expected choice for this specific domain than Java/Spring or Go, and would read as "the language I know" rather than "the stack this domain actually uses."

## Decision

**Chosen option: Option A — Java + Spring Boot**, backed by PostgreSQL, for the most literal possible match between the stack and the specific claim it's built to demonstrate.

**Hosting: Neon (serverless Postgres, free tier) for the database, Fly.io for compute.** Neon's free tier is built for exactly this kind of project (generous limits, database branching for testing). Fly.io already hosts this portfolio's other Go service (`editor-service`), so this extends a free-tier pattern already proven rather than introducing a new one.

## Consequences

**Positive:**
- Directly, literally matches the resume claim being demonstrated — a technical reviewer can trace "Spring Boot" straight from the resume to working code
- Zero-cost hosting via Neon + Fly.io free tiers, consistent with every other piece in this portfolio

**Negative / accepted tradeoffs:**
- More boilerplate/ceremony than Go or Kotlin would have required for the same functionality — accepted, since the point is fidelity to the claimed stack, not minimizing lines of code
- JVM cold-start and memory footprint are heavier than Go on Fly.io's free tier — worth monitoring once actually deployed, not yet a problem at skeleton stage

**Risks:**
- None beyond what ADR-level stack choices typically carry — this is a scope-definition decision, not a new risk surface

## Notes

- No database connection is wired up yet in this skeleton — see `src/main/resources/application.yml`. That arrives with the first entities/migrations, not before.
- See [ADR-002](002-data-model-and-consistency.md) for how PostgreSQL is actually used once wired up.
