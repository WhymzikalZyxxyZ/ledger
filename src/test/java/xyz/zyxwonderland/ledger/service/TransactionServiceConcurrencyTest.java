package xyz.zyxwonderland.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.zyxwonderland.ledger.AbstractIntegrationTest;
import xyz.zyxwonderland.ledger.api.EntryRequest;
import xyz.zyxwonderland.ledger.api.SubmitTransactionRequest;
import xyz.zyxwonderland.ledger.domain.Account;
import xyz.zyxwonderland.ledger.domain.AccountType;

/**
 * This is the class docs/adr/004-correctness-verification.md exists to
 * justify: ADR-002 and ADR-003 both make specific claims about behavior
 * under real concurrent load, and a claim that's only ever been exercised
 * sequentially hasn't actually been tested. Both tests here deliberately
 * provoke the exact race each ADR's design was chosen to prevent.
 */
class TransactionServiceConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    private Account cash;
    private Account revenue;

    @BeforeEach
    void setUp() {
        cash = accountService.createAccount("Concurrency Test Cash", "USD", AccountType.ASSET);
        revenue = accountService.createAccount("Concurrency Test Revenue", "USD", AccountType.REVENUE);
    }

    /**
     * Proves ADR-002's claim: SELECT ... FOR UPDATE row-locking on
     * account_balances prevents lost updates. Without it, N concurrent
     * read-modify-write cycles on the same balance row would race and the
     * final balance would undercount some of them — this test would be
     * flaky-in-the-wrong-direction (silently wrong, not just occasionally
     * failing) on a naive read-then-write implementation.
     */
    @Test
    void concurrentTransactionsToTheSameAccountDoNotLoseUpdates() throws InterruptedException {
        BigDecimal amountPerTransaction = new BigDecimal("10.00");
        var latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

        try {
            List<Future<?>> futures = IntStream.range(0, THREAD_COUNT)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(latch);
                        transactionService.submit(transfer("concurrent-distinct-" + i, amountPerTransaction));
                    }))
                    .toList();

            latch.countDown(); // release all threads at once, maximizing overlap
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }

        BigDecimal expected = amountPerTransaction.multiply(BigDecimal.valueOf(THREAD_COUNT));
        assertThat(accountService.getBalance(cash.getId(), "USD").getBalance())
                .as("no lost updates: every one of %d concurrent transactions must be reflected", THREAD_COUNT)
                .isEqualByComparingTo(expected);
        assertThat(accountService.getBalance(revenue.getId(), "USD").getBalance())
                .isEqualByComparingTo(expected.negate());
    }

    /**
     * Proves ADR-003's claim: the idempotency_key UNIQUE constraint prevents
     * double-posting under real concurrent retries, not just sequential
     * ones. All threads submit the identical request at once — a
     * check-then-act implementation (Option A, rejected in the ADR) would
     * let more than one through here.
     */
    @Test
    void concurrentSubmissionsWithTheSameIdempotencyKeyPostExactlyOnce() throws InterruptedException {
        var request = transfer("concurrent-same-key", new BigDecimal("15.00"));
        var latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

        List<UUID> resultingTransactionIds;
        try {
            List<Future<UUID>> futures = IntStream.range(0, THREAD_COUNT)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(latch);
                        return transactionService.submit(request).transaction().id();
                    }))
                    .toList();

            latch.countDown();
            resultingTransactionIds = new ArrayList<>();
            for (Future<UUID> f : futures) {
                resultingTransactionIds.add(f.get(30, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }

        Set<UUID> distinctIds = resultingTransactionIds.stream().collect(Collectors.toSet());
        assertThat(distinctIds)
                .as("every concurrent submission of the same idempotency key must converge on one transaction")
                .hasSize(1);
        assertThat(accountService.getBalance(cash.getId(), "USD").getBalance())
                .as("balance reflects exactly one posting, not %d", THREAD_COUNT)
                .isEqualByComparingTo("15.00");
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private SubmitTransactionRequest transfer(String idempotencyKey, BigDecimal amount) {
        return new SubmitTransactionRequest(
                idempotencyKey,
                "concurrency test transfer",
                List.of(
                        new EntryRequest(cash.getId(), amount, "USD"),
                        new EntryRequest(revenue.getId(), amount.negate(), "USD")
                )
        );
    }
}
