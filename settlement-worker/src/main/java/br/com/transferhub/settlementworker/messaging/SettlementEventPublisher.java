package br.com.transferhub.settlementworker.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica o resultado da liquidação APÓS o commit da transação que o gravou.
 * Mesmo padrão do transfer-api (Etapa 5): evita publicar um resultado que não
 * chegou a ser persistido. A mesma janela de dual write se aplica (Outbox seria a cura).
 */
@Component
public class SettlementEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SettlementEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettled(TransferSettled event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_TRANSFER_SETTLED, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFailed(TransferFailed event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_TRANSFER_FAILED, event);
    }
}
