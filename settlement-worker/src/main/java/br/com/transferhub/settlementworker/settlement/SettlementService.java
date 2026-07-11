package br.com.transferhub.settlementworker.settlement;

import br.com.transferhub.settlementworker.messaging.TransferFailed;
import br.com.transferhub.settlementworker.messaging.TransferRequested;
import br.com.transferhub.settlementworker.messaging.TransferSettled;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class SettlementService {

    /** Limite por transação: R$ 50.000,00. */
    static final BigDecimal PER_TRANSACTION_LIMIT = new BigDecimal("50000.00");
    /** Limite acumulado por conta destino em 24h: R$ 100.000,00. */
    static final BigDecimal DAILY_TARGET_LIMIT = new BigDecimal("100000.00");

    private final SettlementRecordRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public SettlementService(SettlementRecordRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void process(TransferRequested event) {
        // IDEMPOTÊNCIA DO CONSUMER: entrega é "pelo menos uma vez". Se já processamos
        // este transfer_id, ignoramos — o UNIQUE também protege fisicamente.
        if (repository.existsByTransferId(event.transferId())) {
            return;
        }

        String rejectionReason = evaluate(event.targetAccountId(), event.amount());

        if (rejectionReason == null) {
            SettlementRecord record = repository.save(
                    SettlementRecord.settled(event.transferId(), event.targetAccountId(), event.amount()));
            eventPublisher.publishEvent(
                    new TransferSettled(event.transferId(), record.getId(), OffsetDateTime.now()));
        } else {
            repository.save(
                    SettlementRecord.rejected(event.transferId(), event.targetAccountId(), event.amount(), rejectionReason));
            eventPublisher.publishEvent(
                    new TransferFailed(event.transferId(), rejectionReason, OffsetDateTime.now()));
        }
    }

    /**
     * Aplica as regras de limite. Retorna null se aprovado, ou a razão da rejeição.
     * Rejeição é resultado de NEGÓCIO — não é exceção.
     */
    private String evaluate(UUID targetAccountId, BigDecimal amount) {
        if (amount.compareTo(PER_TRANSACTION_LIMIT) > 0) {
            return "Valor " + amount + " excede o limite por transacao de " + PER_TRANSACTION_LIMIT;
        }

        OffsetDateTime since = OffsetDateTime.now().minusHours(24);
        BigDecimal settledLast24h = repository.sumSettledToTargetSince(targetAccountId, since);

        if (settledLast24h.add(amount).compareTo(DAILY_TARGET_LIMIT) > 0) {
            return "Limite diario de " + DAILY_TARGET_LIMIT + " para a conta destino excedido"
                    + " (ja liquidado em 24h: " + settledLast24h + ")";
        }
        return null;
    }
}
