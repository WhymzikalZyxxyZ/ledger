package xyz.zyxwonderland.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.zyxwonderland.ledger.api.AccountNotFoundException;
import xyz.zyxwonderland.ledger.domain.Account;
import xyz.zyxwonderland.ledger.domain.AccountBalance;
import xyz.zyxwonderland.ledger.domain.AccountBalanceId;
import xyz.zyxwonderland.ledger.domain.AccountType;
import xyz.zyxwonderland.ledger.repository.AccountBalanceRepository;
import xyz.zyxwonderland.ledger.repository.AccountRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public AccountService(AccountRepository accountRepository, AccountBalanceRepository accountBalanceRepository) {
        this.accountRepository = accountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
    }

    /**
     * Creates the account and its zero-balance row together, in the same
     * transaction. This closes a real race: {@link AccountBalanceRepository#findForUpdate}
     * locks an existing row — it can't lock a row that doesn't exist yet. By
     * guaranteeing the balance row exists from the moment the account does,
     * no transaction submission is ever the "first" to touch this account's
     * balance, so there's no window where two concurrent first-transactions
     * could race to insert the initial row. Entries are required to match
     * their account's own currency (see TransactionService) specifically so
     * this single eagerly-created row is always the right one to lock —
     * accounts are single-currency in this model, not a set of balances.
     */
    @Transactional
    public Account createAccount(String name, String currency, AccountType accountType) {
        Account account = accountRepository.save(new Account(name, currency, accountType));
        AccountBalance balance = new AccountBalance(
                new AccountBalanceId(account.getId(), currency),
                BigDecimal.ZERO,
                Instant.now()
        );
        accountBalanceRepository.save(balance);
        return account;
    }

    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional
    public AccountBalance getBalance(UUID accountId, String currency) {
        return accountBalanceRepository.findById(new AccountBalanceId(accountId, currency))
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
