package xyz.zyxwonderland.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import xyz.zyxwonderland.ledger.domain.AccountType;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull AccountType accountType
) {
}
