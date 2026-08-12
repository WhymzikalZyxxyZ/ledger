package xyz.zyxwonderland.ledger.service;

import xyz.zyxwonderland.ledger.api.TransactionResponse;

/**
 * {@code created = false} means this call returned an already-existing
 * transaction (either the fast-path idempotency-key lookup, or the
 * unique-constraint-violation recovery path) rather than posting a new one —
 * the distinction the controller uses to pick 200 vs 201.
 */
public record SubmissionResult(TransactionResponse transaction, boolean created) {
}
