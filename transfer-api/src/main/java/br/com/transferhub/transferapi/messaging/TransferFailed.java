package br.com.transferhub.transferapi.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento CONSUMIDO (publicado pelo settlement-worker): liquidação rejeitada.
 * Dispara a transação compensatória (estorno da origem).
 */
public record TransferFailed(
        UUID transferId,
        String reason,
        OffsetDateTime occurredAt
) {
}
