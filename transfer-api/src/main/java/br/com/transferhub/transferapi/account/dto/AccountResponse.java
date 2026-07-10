package br.com.transferhub.transferapi.account.dto;

import br.com.transferhub.transferapi.account.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representação de saída da conta. Note que NÃO expomos o campo `version`
 * (detalhe interno de concorrência, não faz parte do contrato da API).
 */
public record AccountResponse(
        UUID id,
        String document,
        String holderName,
        BigDecimal balance,
        OffsetDateTime createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getDocument(),
                account.getHolderName(),
                account.getBalance(),
                account.getCreatedAt()
        );
    }
}
