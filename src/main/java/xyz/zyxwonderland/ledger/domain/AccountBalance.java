package xyz.zyxwonderland.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * ADR-002: a transactionally-maintained read cache, not a second source of
 * truth. Always written inside the same DB transaction as the
 * ledger_entries that produced the change, so it can never observably
 * diverge from them — PostgreSQL's ACID guarantees do that work, not
 * application-level reconciliation.
 */
@Entity
@Table(name = "account_balances")
public class AccountBalance {

    @EmbeddedId
    private AccountBalanceId id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountBalance() {
        // for JPA
    }

    public AccountBalance(AccountBalanceId id, BigDecimal balance, Instant updatedAt) {
        this.id = id;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public AccountBalanceId getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void applyDelta(BigDecimal delta, Instant now) {
        this.balance = this.balance.add(delta);
        this.updatedAt = now;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
