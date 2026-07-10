package br.com.transferhub.transferapi.common.exception;

import java.math.BigDecimal;

/**
 * Valor de operação inválido (nulo, zero ou negativo). Invariante de domínio:
 * dinheiro movimentado é sempre estritamente positivo.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(BigDecimal amount) {
        super("Valor deve ser positivo, recebido: " + amount);
    }
}
