package xyz.zyxwonderland.ledger.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Bounds found missing in a security audit: an unbounded entries list let a
 * single unauthenticated request hold row locks (via TransactionWriter's
 * applyBalanceDeltas) across arbitrarily many accounts inside one DB
 * transaction — a single-request resource-exhaustion vector, not just a
 * theoretical one. idempotencyKey/description had no upper bound either,
 * against TEXT (unbounded) columns.
 */
public record SubmitTransactionRequest(
        @NotBlank @Size(max = 255) String idempotencyKey,
        @Size(max = 2000) String description,
        @NotEmpty @Size(max = 100) @Valid List<EntryRequest> entries
) {
}
