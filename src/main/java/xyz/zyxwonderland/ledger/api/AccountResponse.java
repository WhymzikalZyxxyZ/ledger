package xyz.zyxwonderland.ledger.api;

import java.time.Instant;
import java.util.UUID;
import xyz.zyxwonderland.ledger.domain.Account;
import xyz.zyxwonderland.ledger.domain.AccountType;

public record AccountResponse(
        UUID id,
        String name,
        String currency,
        AccountType accountType,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getCurrency(),
                account.getAccountType(),
                account.getCreatedAt()
        );
    }
}
