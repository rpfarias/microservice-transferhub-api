package br.com.transferhub.transferapi.transfer;

/**
 * Resultado de uma solicitação de transferência.
 *
 * @param transfer a transferência (nova ou pré-existente)
 * @param created  true se foi criada agora (-> 201); false se a chave de
 *                 idempotência já existia e devolvemos a anterior (-> 200)
 */
public record TransferResult(Transfer transfer, boolean created) {
}
