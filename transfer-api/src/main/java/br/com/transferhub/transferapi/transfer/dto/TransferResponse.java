package br.com.transferhub.transferapi.transfer.dto;

import br.com.transferhub.transferapi.transfer.Transfer;
import br.com.transferhub.transferapi.transfer.TransferStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        TransferStatus status,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getSourceAccountId(),
                t.getTargetAccountId(),
                t.getAmount(),
                t.getStatus(),
                t.getFailureReason(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
