package br.com.transferhub.settlementworker.messaging;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento CONSUMIDO (publicado pelo transfer-api). Cópia própria do contrato —
 * os serviços não compartilham jar. O conversor desserializa pelo tipo deste
 * parâmetro (TypePrecedence.INFERRED), ignorando o nome da classe do remetente.
 */
public record TransferRequested(
        UUID transferId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        OffsetDateTime occurredAt
) {
}
