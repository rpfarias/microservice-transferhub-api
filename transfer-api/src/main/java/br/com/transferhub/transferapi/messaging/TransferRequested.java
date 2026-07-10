package br.com.transferhub.transferapi.messaging;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento publicado quando uma transferência é aceita (origem já debitada, PENDING).
 * O settlement-worker consome isto para aplicar as regras de limite.
 *
 * Contrato de fio (JSON). O settlement-worker terá uma cópia equivalente deste
 * record — serviços independentes não compartilham jar de domínio de propósito.
 */
public record TransferRequested(
        UUID transferId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        OffsetDateTime occurredAt
) {
}
