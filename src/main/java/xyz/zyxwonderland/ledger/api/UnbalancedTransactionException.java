package xyz.zyxwonderland.ledger.api;

/** Thrown when a transaction's entries don't net to zero for some currency — never persisted. */
public class UnbalancedTransactionException extends RuntimeException {
    public UnbalancedTransactionException(String currency, java.math.BigDecimal netAmount) {
        super("Entries for currency " + currency + " net to " + netAmount + ", not zero");
    }
}
