package xyz.zyxwonderland.ledger.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.zyxwonderland.ledger.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** The transaction-history / "regulatory reporting" style query path. */
    Page<LedgerEntry> findByAccount_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    List<LedgerEntry> findByTransaction_Id(UUID transactionId);
}
