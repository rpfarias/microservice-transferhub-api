package br.com.transferhub.settlementworker.messaging;

import br.com.transferhub.settlementworker.settlement.SettlementService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome TransferRequested da fila e delega a decisão de liquidação ao service.
 * Fino de propósito: a lógica vive no domínio/service, não no adaptador de mensageria.
 */
@Component
public class SettlementListener {

    private final SettlementService settlementService;

    public SettlementListener(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @RabbitListener(queues = RabbitConfig.Q_TRANSFER_REQUESTED)
    public void onTransferRequested(TransferRequested event) {
        settlementService.process(event);
    }
}
