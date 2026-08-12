package xyz.zyxwonderland.ledger.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SubmitTransactionRequest(
        @NotBlank String idempotencyKey,
        String description,
        @NotEmpty @Valid List<EntryRequest> entries
) {
}
