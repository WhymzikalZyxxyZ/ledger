package xyz.zyxwonderland.ledger.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import xyz.zyxwonderland.ledger.domain.Transaction;
import xyz.zyxwonderland.ledger.domain.TransactionStatus;

public record TransactionResponse(
        UUID id,
        String idempotencyKey,
        String description,
        TransactionStatus status,
        Instant createdAt,
        List<EntryRequest> entries
) {
    public static TransactionResponse from(Transaction transaction, List<EntryRequest> entries) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                entries
        );
    }
}
