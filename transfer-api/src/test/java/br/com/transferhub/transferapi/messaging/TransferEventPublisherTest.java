package br.com.transferhub.transferapi.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Teste unitário do listener: garante que o evento é roteado para a exchange e
 * routing key corretas. Sem broker — RabbitTemplate mockado.
 */
class TransferEventPublisherTest {

    @Test
    void publicaNaExchangeComRoutingKeyCorreta() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        TransferEventPublisher publisher = new TransferEventPublisher(rabbitTemplate);

        TransferRequested event = new TransferRequested(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), OffsetDateTime.now());

        publisher.onTransferRequested(event);

        verify(rabbitTemplate).convertAndSend("transfers.exchange", "transfer.requested", event);
    }
}
