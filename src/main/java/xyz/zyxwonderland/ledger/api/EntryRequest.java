package xyz.zyxwonderland.ledger.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Positive amount = debit, negative = credit — see docs/architecture/overview.md.
 *
 * <p>{@code @Digits} matches the DB column's actual precision (NUMERIC(19,4) —
 * see V1__init_schema.sql). Without it, an amount with more digits than the
 * column allows bypassed validation entirely and failed only at the JDBC
 * layer with a generic 500 — a security-audit finding, since a client input
 * error was surfacing as an opaque server error. {@code @Pattern} on
 * currency rejects anything that isn't uppercase ISO-4217-shaped (the old
 * length-only check accepted "usd" and "USD" as silently distinct
 * currencies).
 */
public record EntryRequest(
        @NotNull UUID accountId,
        @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotNull @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Z]{3}$") String currency
) {
}
