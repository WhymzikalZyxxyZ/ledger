package xyz.zyxwonderland.ledger.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.zyxwonderland.ledger.domain.AccountBalance;
import xyz.zyxwonderland.ledger.domain.AccountBalanceId;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, AccountBalanceId> {

    /**
     * ADR-002's concurrency-correctness mechanism: PESSIMISTIC_WRITE
     * translates to a real {@code SELECT ... FOR UPDATE} against PostgreSQL,
     * row-locking this specific (account_id, currency) balance for the
     * duration of the enclosing transaction so concurrent submissions to the
     * same account can't produce a lost update. This is the query
     * ADR-004's concurrency tests exist to actually exercise, not just trust.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM AccountBalance b WHERE b.id.accountId = :accountId AND b.id.currency = :currency")
    Optional<AccountBalance> findForUpdate(@Param("accountId") UUID accountId, @Param("currency") String currency);
}
