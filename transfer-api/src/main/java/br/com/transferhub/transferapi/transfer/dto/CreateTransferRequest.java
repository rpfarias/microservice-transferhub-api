package br.com.transferhub.transferapi.transfer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload de criação de transferência. Primeira linha de defesa (400 se inválido);
 * o domínio (Account.debit/credit) é a segunda linha.
 */
public record CreateTransferRequest(

        @NotNull(message = "sourceAccountId é obrigatório")
        UUID sourceAccountId,

        @NotNull(message = "targetAccountId é obrigatório")
        UUID targetAccountId,

        @NotNull(message = "amount é obrigatório")
        @Positive(message = "amount deve ser positivo")
        @Digits(integer = 15, fraction = 4, message = "amount deve caber em NUMERIC(19,4)")
        BigDecimal amount
) {
}
