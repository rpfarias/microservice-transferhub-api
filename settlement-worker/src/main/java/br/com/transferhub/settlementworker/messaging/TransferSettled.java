package br.com.transferhub.settlementworker.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Evento PUBLICADO quando a liquidação é aprovada. */
public record TransferSettled(
        UUID transferId,
        UUID settlementId,
        OffsetDateTime occurredAt
) {
}
