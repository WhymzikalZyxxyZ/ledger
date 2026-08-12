package xyz.zyxwonderland.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import xyz.zyxwonderland.ledger.domain.AccountType;

public record CreateAccountRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull AccountType accountType
) {
}
