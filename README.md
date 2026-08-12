# LEDGER

A double-entry, event-sourced transaction ledger — Java, Spring Boot, PostgreSQL.

This exists to prove out one specific set of claims with working code rather than resume prose: designing high-throughput backend services for real-time transaction data, under strict reliability and latency requirements, with real PostgreSQL schema design and query optimization behind them.

> ⚠️ **This is a portfolio demonstration, not audited financial infrastructure.** It must never process real financial transactions or be represented as production-ready without a real security review, correctness audit, and (for real money) regulatory/compliance review entirely out of scope for a personal project. See [`docs/RISKS.md`](docs/RISKS.md).

## Status

This repository currently contains:
- Full design documentation (this README, four ADRs, an architecture overview, a risk register)
- A Flyway migration implementing the schema in [`docs/architecture/overview.md`](docs/architecture/overview.md)
- Entities, repositories, services, and a REST API (`POST /accounts`, `GET /accounts/{id}`, `GET /accounts/{id}/balance`, `POST /transactions`) implementing the transaction-submission flow described there
- Integration tests against a real PostgreSQL instance (via Testcontainers), including concurrency tests that deliberately provoke the exact races ADR-002 and ADR-003 are meant to prevent — see [ADR-004](docs/adr/004-correctness-verification.md)

Not yet built: deployment to Neon/Fly.io, and a database-level trigger enforcing the double-entry invariant as defense-in-depth (currently service-layer only — see [`docs/RISKS.md`](docs/RISKS.md)).

## Why these choices — and what each one is proving

| Decision | Choice | What it's proving |
|---|---|---|
| Stack | Java + Spring Boot | The most literal match to the resume's listed framework and to how enterprise financial-services backends actually get built — not an approximation ([ADR-001](docs/adr/001-stack-and-hosting.md)) |
| Balance reads | A transactionally-maintained `account_balances` table, not summed on read | The actual "PostgreSQL query optimization" claim — O(1) indexed reads instead of scanning transaction history ([ADR-002](docs/adr/002-data-model-and-consistency.md)) |
| Duplicate-submission safety | A database-level `UNIQUE` constraint on idempotency key, not an app-level check-then-act | "Strict reliability" under retries — race-condition-proof by construction, not by careful code that a future change could break ([ADR-003](docs/adr/003-idempotency-and-exactly-once.md)) |
| Correctness verification | Integration tests against real PostgreSQL (Testcontainers) that deliberately provoke concurrent races | The difference between *designed* correct and *proven* correct — closed by `TransactionServiceConcurrencyTest` ([ADR-004](docs/adr/004-correctness-verification.md)) |

## Architecture

See [`docs/architecture/overview.md`](docs/architecture/overview.md) for the schema and the transaction-submission flow end to end.

## Risks & known gaps

See [`docs/RISKS.md`](docs/RISKS.md) — read this before treating any correctness claim here as more than what's actually been tested. The concurrency-correctness claims in ADR-002/003 are now backed by passing tests (`TransactionServiceConcurrencyTest`), not just design intent — but this remains a portfolio demonstration, not audited financial infrastructure.

## Building

```
git clone https://github.com/WhymzikalZyxxyZ/ledger.git
cd ledger
mvn compile
```

Requires JDK 21 and Maven. Running the app (`mvn spring-boot:run`) requires a PostgreSQL instance and `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` set — no defaults are provided, by design (see [`application.yml`](src/main/resources/application.yml)). Running the test suite (`mvn test`) requires Docker, for Testcontainers.

## License

MIT — see [LICENSE](LICENSE).
