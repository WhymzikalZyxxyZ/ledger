package xyz.zyxwonderland.ledger.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.zyxwonderland.ledger.service.TransactionService;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Safe to retry with the same idempotencyKey any number of times — see
     * docs/adr/003-idempotency-and-exactly-once.md. A retry returns 200 with
     * the original result rather than creating a duplicate; a genuinely new
     * idempotencyKey returns 201.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> submit(@Valid @RequestBody SubmitTransactionRequest request) {
        var result = transactionService.submit(request);
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.transaction());
    }
}
