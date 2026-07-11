package br.com.transferhub.settlementworker.settlement;

/**
 * Violação de regra de NEGÓCIO na liquidação (limites). É DETERMINÍSTICA:
 * reprocessar a mesma mensagem produz o mesmo resultado, então retry é inútil.
 * O listener captura este tipo e converte em rejeição imediata (REJECTED +
 * TransferFailed), SEM retry e SEM DLQ. Qualquer outra exceção é considerada
 * técnica (transiente) e segue o caminho retry -> DLQ.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
