package xyz.zyxwonderland.ledger.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.zyxwonderland.ledger.domain.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}
