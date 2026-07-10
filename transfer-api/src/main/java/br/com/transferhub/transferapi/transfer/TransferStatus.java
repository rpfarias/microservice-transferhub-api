package br.com.transferhub.transferapi.transfer;

/**
 * Ciclo de vida da transferência.
 * Na Etapa 2 (síncrona) só usamos COMPLETED. PENDING e FAILED entram em cena
 * a partir da Etapa 5, quando a liquidação vira assíncrona.
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED
}
