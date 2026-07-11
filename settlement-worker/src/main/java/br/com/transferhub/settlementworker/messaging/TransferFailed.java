package br.com.transferhub.settlementworker.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Evento PUBLICADO quando a liquidação é rejeitada (regra de limite). */
public record TransferFailed(
        UUID transferId,
        String reason,
        OffsetDateTime occurredAt
) {
}
