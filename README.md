# LEDGER

A double-entry, event-sourced transaction ledger — Java, Spring Boot, PostgreSQL.

This exists to prove out one specific set of claims with working code rather than resume prose: designing high-throughput backend services for real-time transaction data, under strict reliability and latency requirements, with real PostgreSQL schema design and query optimization behind them.

> ⚠️ **This is a portfolio demonstration, not audited financial infrastructure.** It must never process real financial transactions or be represented as production-ready without a real security review, correctness audit, and (for real money) regulatory/compliance review entirely out of scope for a personal project. See [`docs/RISKS.md`](docs/RISKS.md).

> **This repo is currently in its design/documentation phase.** No entities, controllers, or database wiring exist yet — see [Status](#status) below.

## Status

This repository currently contains:
- A minimal, buildable Spring Boot skeleton (`src/main/java/`) with no entities, repositories, or controllers
- Full design documentation (this README, four ADRs, an architecture overview, a risk register)

Not yet built: the actual schema/migrations, the transaction-submission API, the concurrency tests that would prove the correctness claims below, and deployment to Neon/Fly.io.

## Why these choices — and what each one is proving

| Decision | Choice | What it's proving |
|---|---|---|
| Stack | Java + Spring Boot | The most literal match to the resume's listed framework and to how enterprise financial-services backends actually get built — not an approximation ([ADR-001](docs/adr/001-stack-and-hosting.md)) |
| Balance reads | A transactionally-maintained `account_balances` table, not summed on read | The actual "PostgreSQL query optimization" claim — O(1) indexed reads instead of scanning transaction history ([ADR-002](docs/adr/002-data-model-and-consistency.md)) |
| Duplicate-submission safety | A database-level `UNIQUE` constraint on idempotency key, not an app-level check-then-act | "Strict reliability" under retries — race-condition-proof by construction, not by careful code that a future change could break ([ADR-003](docs/adr/003-idempotency-and-exactly-once.md)) |
| Correctness verification | Integration tests against real PostgreSQL that deliberately provoke concurrent races | The difference between *designed* correct and *proven* correct — the gap this project is explicit about not having closed yet ([ADR-004](docs/adr/004-correctness-verification.md)) |

## Architecture

See [`docs/architecture/overview.md`](docs/architecture/overview.md) for the schema and the transaction-submission flow end to end.

## Risks & known gaps

See [`docs/RISKS.md`](docs/RISKS.md) — read this before treating any correctness claim here as more than design intent. The concurrency-correctness claims in ADR-002/003 are **not yet proven** by tests that don't exist yet; that gap is stated on purpose, not hidden.

## Building

```
git clone https://github.com/WhymzikalZyxxyZ/ledger.git
cd ledger
mvn compile
```

Requires JDK 21 and Maven. No database connection is configured yet, so `mvn spring-boot:run` will not yet start a working application — that arrives with the first entities/migrations.

## License

MIT — see [LICENSE](LICENSE).
