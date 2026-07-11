package br.com.transferhub.transferapi.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento CONSUMIDO (publicado pelo settlement-worker): liquidação aprovada.
 * Cópia própria do contrato de fio — sem jar compartilhado entre serviços.
 */
public record TransferSettled(
        UUID transferId,
        UUID settlementId,
        OffsetDateTime occurredAt
) {
}
