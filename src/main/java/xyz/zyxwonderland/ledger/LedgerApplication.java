package xyz.zyxwonderland.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. No entities, repositories, or controllers exist yet — see
 * docs/adr/ for the target design and docs/RISKS.md for what's explicitly
 * not built. This is a documentation-and-verified-build phase, matching the
 * precedent set by this portfolio's other apps (mend, chart) before their
 * first feature-implementation pass.
 */
@SpringBootApplication
public class LedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
