package xyz.zyxwonderland.ledger.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.zyxwonderland.ledger.domain.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * The read side of ADR-003's idempotency handling: after an insert fails
     * on the unique constraint, this is how the service layer fetches the
     * original transaction to return instead of erroring.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
