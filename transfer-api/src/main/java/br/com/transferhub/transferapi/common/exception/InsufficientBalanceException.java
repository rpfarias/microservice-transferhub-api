package br.com.transferhub.transferapi.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Débito maior que o saldo disponível. Invariante de domínio: uma conta
 * nunca fica com saldo negativo.
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(UUID accountId, BigDecimal balance, BigDecimal amount) {
        super("Saldo insuficiente na conta " + accountId
                + ": saldo=" + balance + ", débito solicitado=" + amount);
    }
}
