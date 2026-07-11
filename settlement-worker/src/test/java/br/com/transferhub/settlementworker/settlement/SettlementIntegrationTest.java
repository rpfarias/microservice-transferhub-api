package br.com.transferhub.settlementworker.settlement;

import br.com.transferhub.settlementworker.TestcontainersConfiguration;
import br.com.transferhub.settlementworker.messaging.RabbitConfig;
import br.com.transferhub.settlementworker.messaging.TransferFailed;
import br.com.transferhub.settlementworker.messaging.TransferRequested;
import br.com.transferhub.settlementworker.messaging.TransferSettled;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integração real: publica TransferRequested na exchange, o @RabbitListener do
 * worker consome, aplica as regras, persiste e publica o resultado. Filas de
 * teste capturam TransferSettled/TransferFailed.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, SettlementIntegrationTest.TestQueueConfig.class})
class SettlementIntegrationTest {

    @TestConfiguration
    static class TestQueueConfig {
        @Bean
        Queue testSettledQueue() {
            return new Queue("test.q.settled");
        }

        @Bean
        Queue testFailedQueue() {
            return new Queue("test.q.failed");
        }

        @Bean
        Binding testSettledBinding(Queue testSettledQueue, TopicExchange transfersExchange) {
            return BindingBuilder.bind(testSettledQueue).to(transfersExchange).with(RabbitConfig.RK_TRANSFER_SETTLED);
        }

        @Bean
        Binding testFailedBinding(Queue testFailedQueue, TopicExchange transfersExchange) {
            return BindingBuilder.bind(testFailedQueue).to(transfersExchange).with(RabbitConfig.RK_TRANSFER_FAILED);
        }
    }

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    SettlementRecordRepository repository;

    /** Spy no service real: os demais testes usam o comportamento real; o teste de
     *  retry o instrui a falhar 2x (falha técnica simulada) e funcionar na 3ª. */
    @MockitoSpyBean
    SettlementService settlementService;

    @Autowired
    @Qualifier("testSettledQueue")
    Queue testSettledQueue;

    @Autowired
    @Qualifier("testFailedQueue")
    Queue testFailedQueue;

    private void publish(UUID transferId, UUID targetId, String amount) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_TRANSFER_REQUESTED,
                new TransferRequested(transferId, UUID.randomUUID(), targetId, new BigDecimal(amount), OffsetDateTime.now()));
    }

    @Test
    void mensagemDentroDoLimite_liquidaEPublicaSettled() {
        UUID transferId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        publish(transferId, targetId, "10000.00");

        // Bloqueia até o worker processar e publicar (após o commit).
        Object out = rabbitTemplate.receiveAndConvert(testSettledQueue.getName(), 10_000);
        assertThat(out).isInstanceOf(TransferSettled.class);
        assertThat(((TransferSettled) out).transferId()).isEqualTo(transferId);

        assertThat(repository.existsByTransferId(transferId)).isTrue();
    }

    // ---------- Etapa 8: retry (falha técnica) e DLQ (mensagem venenosa) ----------

    @Test
    void falhaTecnicaTransiente_retryRecupera() {
        UUID transferId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        // Simula falha TÉCNICA transiente (ex.: banco fora) nas 2 primeiras tentativas.
        doThrow(new RuntimeException("banco fora (simulado)"))
                .doThrow(new RuntimeException("banco fora (simulado)"))
                .doCallRealMethod()
                .when(settlementService).process(any());

        publish(transferId, targetId, "10000.00");

        // O retry (max-attempts=3, backoff 1s/2s) recupera na 3ª tentativa.
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(repository.existsByTransferId(transferId)).isTrue());
        verify(settlementService, times(3)).process(any());

        // E a mensagem NÃO foi para a DLQ (foi processada com sucesso).
        assertThat(rabbitTemplate.receive(RabbitConfig.Q_TRANSFER_REQUESTED_DLQ, 500)).isNull();
    }

    @Test
    void mensagemVenenosa_caiNaDlq() {
        // Corpo que NÃO desserializa como TransferRequested: falha em toda tentativa
        // e, esgotado o retry, é rejeitada sem requeue -> DLX -> DLQ.
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.RK_TRANSFER_REQUESTED,
                new Message("isto-nao-e-json-valido".getBytes(), props));

        // Recebe CRU da DLQ (converter de novo lançaria a mesma exceção).
        Message dead = rabbitTemplate.receive(RabbitConfig.Q_TRANSFER_REQUESTED_DLQ, 15_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody())).isEqualTo("isto-nao-e-json-valido");
    }

    @Test
    void mensagemAcimaDoLimitePorTransacao_rejeitaEPublicaFailed() {
        UUID transferId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        publish(transferId, targetId, "60000.00"); // > 50.000

        Object out = rabbitTemplate.receiveAndConvert(testFailedQueue.getName(), 10_000);
        assertThat(out).isInstanceOf(TransferFailed.class);
        assertThat(((TransferFailed) out).transferId()).isEqualTo(transferId);
        assertThat(((TransferFailed) out).reason()).isNotBlank();

        assertThat(repository.existsByTransferId(transferId)).isTrue();
    }
}
