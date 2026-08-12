package xyz.zyxwonderland.ledger.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import xyz.zyxwonderland.ledger.api.SubmitTransactionRequest;
import xyz.zyxwonderland.ledger.repository.TransactionRepository;

/**
 * See docs/architecture/overview.md for the full submission flow and
 * docs/adr/002-004 for why each step is shaped the way it is. The actual
 * transactional insert/lock work lives in {@link TransactionWriter} — kept
 * as a separate bean specifically so its {@code @Transactional} boundary is
 * real (see that class's Javadoc for why a call from here matters).
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionWriter transactionWriter;

    public TransactionService(TransactionRepository transactionRepository, TransactionWriter transactionWriter) {
        this.transactionRepository = transactionRepository;
        this.transactionWriter = transactionWriter;
    }

    public SubmissionResult submit(SubmitTransactionRequest request) {
        // Fast path: if this idempotency key was already processed, return
        // its result without attempting any work. This is an optimization,
        // not the safety mechanism — the catch below is that.
        var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new SubmissionResult(transactionWriter.toResponse(existing.get()), false);
        }

        try {
            return new SubmissionResult(transactionWriter.write(request), true);
        } catch (DataIntegrityViolationException e) {
            // The idempotency_key UNIQUE constraint is the actual enforcement
            // point (ADR-003): a concurrent request with the same key beat us
            // to the insert inside write()'s transaction, which has already
            // rolled back by the time this exception reaches us. That's
            // success, not an error — fetch and return its result.
            return transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(t -> new SubmissionResult(transactionWriter.toResponse(t), false))
                    .orElseThrow(() -> e);
        }
    }
}
