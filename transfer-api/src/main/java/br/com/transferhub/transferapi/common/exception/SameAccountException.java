package br.com.transferhub.transferapi.common.exception;

/**
 * Origem e destino são a mesma conta — transferência sem sentido de negócio.
 */
public class SameAccountException extends RuntimeException {

    public SameAccountException() {
        super("Conta de origem e destino não podem ser a mesma");
    }
}
