package xyz.zyxwonderland.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.zyxwonderland.ledger.api.AccountNotFoundException;
import xyz.zyxwonderland.ledger.api.EntryRequest;
import xyz.zyxwonderland.ledger.api.SubmitTransactionRequest;
import xyz.zyxwonderland.ledger.api.TransactionResponse;
import xyz.zyxwonderland.ledger.api.UnbalancedTransactionException;
import xyz.zyxwonderland.ledger.domain.Account;
import xyz.zyxwonderland.ledger.domain.AccountBalance;
import xyz.zyxwonderland.ledger.domain.LedgerEntry;
import xyz.zyxwonderland.ledger.domain.Transaction;
import xyz.zyxwonderland.ledger.repository.AccountBalanceRepository;
import xyz.zyxwonderland.ledger.repository.AccountRepository;
import xyz.zyxwonderland.ledger.repository.LedgerEntryRepository;
import xyz.zyxwonderland.ledger.repository.TransactionRepository;

/**
 * A separate bean from {@link TransactionService} on purpose: {@code @Transactional}
 * is implemented via a Spring AOP proxy, which only intercepts calls that
 * arrive from <em>outside</em> the bean — a method calling another
 * {@code @Transactional} method on {@code this} bypasses the proxy entirely
 * and silently runs with no transaction at all. Keeping the actual
 * transactional write in its own bean, called from {@link TransactionService}
 * as an external call through the proxy, is what makes {@link #write} really
 * transactional rather than appearing to be.
 */
@Component
class TransactionWriter {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    TransactionWriter(
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountRepository accountRepository,
            AccountBalanceRepository accountBalanceRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    @Transactional
    TransactionResponse write(SubmitTransactionRequest request) {
        validateEntriesNetToZeroPerCurrency(request.entries());

        Map<UUID, Account> accountsById = new HashMap<>();
        for (EntryRequest entry : request.entries()) {
            Account account = accountsById.computeIfAbsent(entry.accountId(), this::loadAccount);
            if (!account.getCurrency().equals(entry.currency())) {
                throw new IllegalArgumentException(
                        "Entry currency " + entry.currency() + " does not match account "
                                + account.getId() + "'s currency " + account.getCurrency());
            }
        }

        // The idempotency_key UNIQUE constraint (ADR-003) does the actual
        // enforcement here — this insert either succeeds, or throws
        // DataIntegrityViolationException which TransactionService catches
        // and turns into "fetch the original result instead."
        Transaction transaction = transactionRepository.save(
                new Transaction(request.idempotencyKey(), request.description()));

        for (EntryRequest entry : request.entries()) {
            ledgerEntryRepository.save(new LedgerEntry(
                    transaction, accountsById.get(entry.accountId()), entry.amount(), entry.currency()));
        }

        applyBalanceDeltas(request.entries());

        return toResponse(transaction);
    }

    /**
     * Locks every (account, currency) touched by this transaction and applies
     * its net delta. Locks are acquired in a fixed order (sorted by account
     * id) regardless of the order entries appear in the request — the
     * standard technique to prevent deadlocks: if two concurrent
     * transactions both touch accounts A and B, locking in inconsistent
     * orders (one A-then-B, the other B-then-A) is exactly how two
     * transactions deadlock against each other. A fixed order means every
     * transaction agrees on which lock to take first.
     */
    private void applyBalanceDeltas(List<EntryRequest> entries) {
        Map<String, BigDecimal> netByAccountCurrency = new HashMap<>();
        for (EntryRequest entry : entries) {
            netByAccountCurrency.merge(entry.accountId() + ":" + entry.currency(), entry.amount(), BigDecimal::add);
        }

        List<EntryRequest> distinctTargets = new ArrayList<>(entries);
        distinctTargets.sort(Comparator.comparing((EntryRequest e) -> e.accountId().toString())
                .thenComparing(EntryRequest::currency));

        Instant now = Instant.now();
        Set<String> applied = new HashSet<>();
        for (EntryRequest entry : distinctTargets) {
            String key = entry.accountId() + ":" + entry.currency();
            if (!applied.add(key)) {
                continue;
            }
            AccountBalance balance = accountBalanceRepository.findForUpdate(entry.accountId(), entry.currency())
                    .orElseThrow(() -> new AccountNotFoundException(entry.accountId()));
            balance.applyDelta(netByAccountCurrency.get(key), now);
        }
    }

    private void validateEntriesNetToZeroPerCurrency(List<EntryRequest> entries) {
        Map<String, BigDecimal> netByCurrency = new HashMap<>();
        for (EntryRequest entry : entries) {
            netByCurrency.merge(entry.currency(), entry.amount(), BigDecimal::add);
        }
        netByCurrency.forEach((currency, net) -> {
            if (net.compareTo(BigDecimal.ZERO) != 0) {
                throw new UnbalancedTransactionException(currency, net);
            }
        });
    }

    private Account loadAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    TransactionResponse toResponse(Transaction transaction) {
        List<EntryRequest> entries = ledgerEntryRepository.findByTransaction_Id(transaction.getId()).stream()
                .map(e -> new EntryRequest(e.getAccount().getId(), e.getAmount(), e.getCurrency()))
                .toList();
        return TransactionResponse.from(transaction, entries);
    }
}
