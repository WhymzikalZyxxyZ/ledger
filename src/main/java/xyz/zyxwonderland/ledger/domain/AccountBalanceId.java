package xyz.zyxwonderland.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AccountBalanceId implements Serializable {

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "currency", length = 3)
    private String currency;

    protected AccountBalanceId() {
        // for JPA
    }

    public AccountBalanceId(UUID accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountBalanceId that)) return false;
        return Objects.equals(accountId, that.accountId) && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, currency);
    }
}
