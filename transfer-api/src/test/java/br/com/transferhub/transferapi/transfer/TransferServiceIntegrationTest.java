package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.TestcontainersConfiguration;
import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.common.exception.InsufficientBalanceException;
import br.com.transferhub.transferapi.common.exception.SameAccountException;
import br.com.transferhub.transferapi.messaging.RabbitConfig;
import br.com.transferhub.transferapi.messaging.TransferRequested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integração contra Postgres + RabbitMQ reais (Testcontainers). A partir da Etapa 5:
 * a transferência fica PENDING, o destino NÃO é creditado, e um evento
 * TransferRequested é publicado na exchange após o commit.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TransferServiceIntegrationTest.TestQueueConfig.class})
class TransferServiceIntegrationTest {

    /** Fila de teste ligada à exchange, para capturarmos o evento publicado. */
    @TestConfiguration
    static class TestQueueConfig {
        @Bean
        Queue testRequestedQueue() {
            // Durável, não-exclusiva, não auto-delete. O RabbitMQ 4 removeu filas
            // transientes não-exclusivas, então precisa ser durável.
            return new Queue("test.q.transfer.requested");
        }

        @Bean
        Binding testRequestedBinding(Queue testRequestedQueue, TopicExchange transfersExchange) {
            return BindingBuilder.bind(testRequestedQueue).to(transfersExchange)
                    .with(RabbitConfig.RK_TRANSFER_REQUESTED);
        }
    }

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    Queue testRequestedQueue;

    private Account newAccount(String document, String balance) {
        Account account = new Account(document, "Titular " + document);
        if (new BigDecimal(balance).signum() > 0) {
            account.credit(new BigDecimal(balance));
        }
        return accountRepository.save(account);
    }

    private void drenarFila() {
        // receiveAndConvert sem timeout = basicGet não-bloqueante; drena sem criar consumidor.
        while (rabbitTemplate.receiveAndConvert(testRequestedQueue.getName()) != null) {
            // descarta mensagens de testes anteriores
        }
    }

    @Test
    void transferenciaAceita_debitaOrigem_destinoIntacto_ficaPending() {
        Account source = newAccount("11111111111", "1000.00");
        Account target = newAccount("22222222222", "0.00");

        TransferResult result = transferService.transfer(
                "key-aceita", source.getId(), target.getId(), new BigDecimal("300.00"));

        assertThat(result.created()).isTrue();
        // Assíncrono: PENDING (não COMPLETED).
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.PENDING);
        // Origem debitada; destino NÃO creditado (isso é a Etapa 7).
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("700.00"));
        assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void publicaTransferRequested_aposCommit_serializadoCorretamente() {
        drenarFila();
        Account source = newAccount("88888888888", "1000.00");
        Account target = newAccount("99999999999", "0.00");

        TransferResult result = transferService.transfer(
                "key-evento", source.getId(), target.getId(), new BigDecimal("250.0000"));

        // O @TransactionalEventListener(AFTER_COMMIT) já enviou ao broker.
        Object received = rabbitTemplate.receiveAndConvert(testRequestedQueue.getName(), 5000);
        assertThat(received).isInstanceOf(TransferRequested.class);
        TransferRequested event = (TransferRequested) received;
        assertThat(event.transferId()).isEqualTo(result.transfer().getId());
        assertThat(event.sourceAccountId()).isEqualTo(source.getId());
        assertThat(event.targetAccountId()).isEqualTo(target.getId());
        assertThat(event.amount()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("250.00"));
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void saldoInsuficiente_abortaTransacao_saldosIntactos() {
        Account source = newAccount("33333333333", "100.00");
        Account target = newAccount("44444444444", "0.00");

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> transferService.transfer(
                        "key-insuf", source.getId(), target.getId(), new BigDecimal("500.00")));

        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void mesmaContaOrigemEDestino_rejeitada() {
        Account account = newAccount("55555555555", "100.00");

        assertThatExceptionOfType(SameAccountException.class)
                .isThrownBy(() -> transferService.transfer(
                        "key-mesma", account.getId(), account.getId(), new BigDecimal("10.00")));
    }

    @Test
    void mesmaChaveDeIdempotencia_naoCriaDuplicata() {
        Account source = newAccount("66666666666", "1000.00");
        Account target = newAccount("77777777777", "0.00");
        String key = "idem-dup";

        TransferResult first = transferService.transfer(key, source.getId(), target.getId(), new BigDecimal("100.00"));
        TransferResult second = transferService.transfer(key, source.getId(), target.getId(), new BigDecimal("100.00"));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());
        // Saldo debitado UMA vez só (900, não 800).
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("900.00"));
        assertThat(transferRepository.findByIdempotencyKey(key)).isPresent();
    }
}
