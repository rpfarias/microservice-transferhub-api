package br.com.transferhub.settlementworker.messaging;

import br.com.transferhub.settlementworker.settlement.BusinessRuleException;
import br.com.transferhub.settlementworker.settlement.SettlementService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome TransferRequested. AQUI mora a separação exceção de negócio vs. técnica:
 *
 * - BusinessRuleException (determinística): capturada e convertida em rejeição
 *   imediata (REJECTED + TransferFailed). Retentar não mudaria o resultado —
 *   retry seria desperdício e atrasaria a resposta ao usuário.
 *
 * - Qualquer outra exceção (técnica/transiente: banco fora, timeout): PROPAGA.
 *   O container faz retry (3x, backoff exponencial) e, esgotado, envia a
 *   mensagem para a DLQ (q.transfer.requested.dlq) para investigação humana.
 */
@Component
public class SettlementListener {

    private final SettlementService settlementService;

    public SettlementListener(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @RabbitListener(queues = RabbitConfig.Q_TRANSFER_REQUESTED)
    public void onTransferRequested(TransferRequested event) {
        try {
            settlementService.process(event);
        } catch (BusinessRuleException e) {
            settlementService.reject(event, e.getMessage());
        }
    }
}
