package xyz.zyxwonderland.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.zyxwonderland.ledger.AbstractIntegrationTest;
import xyz.zyxwonderland.ledger.api.EntryRequest;
import xyz.zyxwonderland.ledger.api.SubmitTransactionRequest;
import xyz.zyxwonderland.ledger.api.UnbalancedTransactionException;
import xyz.zyxwonderland.ledger.domain.Account;
import xyz.zyxwonderland.ledger.domain.AccountType;

class TransactionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    private Account cash;
    private Account revenue;

    @BeforeEach
    void setUp() {
        cash = accountService.createAccount("Test Cash", "USD", AccountType.ASSET);
        revenue = accountService.createAccount("Test Revenue", "USD", AccountType.REVENUE);
    }

    @Test
    void postsABalancedTransactionAndUpdatesBothBalances() {
        var result = transactionService.submit(transfer("txn-1", new BigDecimal("10.00")));

        assertThat(result.created()).isTrue();
        assertThat(accountService.getBalance(cash.getId(), "USD").getBalance())
                .isEqualByComparingTo("10.00");
        assertThat(accountService.getBalance(revenue.getId(), "USD").getBalance())
                .isEqualByComparingTo("-10.00");
    }

    @Test
    void rejectsATransactionWhoseEntriesDoNotNetToZero() {
        var request = new SubmitTransactionRequest(
                "txn-unbalanced",
                "should fail",
                List.of(new EntryRequest(cash.getId(), new BigDecimal("10.00"), "USD"))
        );

        assertThatThrownBy(() -> transactionService.submit(request))
                .isInstanceOf(UnbalancedTransactionException.class);

        // And nothing was persisted — the whole point of validating before
        // the insert, inside the same transaction as everything else.
        assertThat(accountService.getBalance(cash.getId(), "USD").getBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void resubmittingTheSameIdempotencyKeyReturnsTheOriginalResultWithoutDoublePosting() {
        var request = transfer("txn-retry", new BigDecimal("25.00"));

        var first = transactionService.submit(request);
        var second = transactionService.submit(request);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.transaction().id()).isEqualTo(first.transaction().id());

        // Balance reflects ONE transfer, not two — this is the actual claim
        // being tested, not just "the API returned success both times."
        assertThat(accountService.getBalance(cash.getId(), "USD").getBalance())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void rejectsAnEntryWhoseCurrencyDoesNotMatchItsAccount() {
        var request = new SubmitTransactionRequest(
                "txn-currency-mismatch",
                "wrong currency",
                List.of(
                        new EntryRequest(cash.getId(), new BigDecimal("10.00"), "EUR"),
                        new EntryRequest(revenue.getId(), new BigDecimal("-10.00"), "EUR")
                )
        );

        assertThatThrownBy(() -> transactionService.submit(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SubmitTransactionRequest transfer(String idempotencyKey, BigDecimal amount) {
        return new SubmitTransactionRequest(
                idempotencyKey,
                "test transfer",
                List.of(
                        new EntryRequest(cash.getId(), amount, "USD"),
                        new EntryRequest(revenue.getId(), amount.negate(), "USD")
                )
        );
    }
}
