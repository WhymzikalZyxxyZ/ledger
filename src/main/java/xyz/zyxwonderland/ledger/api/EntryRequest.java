package xyz.zyxwonderland.ledger.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Positive amount = debit, negative = credit — see docs/architecture/overview.md. */
public record EntryRequest(
        @NotNull UUID accountId,
        @NotNull BigDecimal amount,
        @NotNull @Size(min = 3, max = 3) String currency
) {
}
