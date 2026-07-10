package br.com.transferhub.transferapi.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica no RabbitMQ APÓS o commit da transação que originou o evento.
 *
 * O service dispara um evento de APLICAÇÃO (in-process) dentro da transação;
 * este listener só roda quando a transação COMITA de fato (AFTER_COMMIT). Assim
 * nunca publicamos um evento para uma transferência que não foi persistida
 * (sem "evento fantasma" por rollback).
 *
 * Limitação conhecida (dual write): se o processo morrer ENTRE o commit e este
 * envio, a transferência fica PENDING e o evento se perde. A solução correta é o
 * padrão Outbox — documentado no README, não implementado aqui de propósito.
 */
@Component
public class TransferEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TransferEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferRequested(TransferRequested event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.RK_TRANSFER_REQUESTED,
                event
        );
    }
}
