package br.com.transferhub.transferapi;

import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.account.dto.AccountResponse;
import br.com.transferhub.transferapi.account.dto.CreateAccountRequest;
import br.com.transferhub.transferapi.messaging.RabbitConfig;
import br.com.transferhub.transferapi.messaging.TransferFailed;
import br.com.transferhub.transferapi.messaging.TransferSettled;
import br.com.transferhub.transferapi.transfer.TransferStatus;
import br.com.transferhub.transferapi.transfer.dto.CreateTransferRequest;
import br.com.transferhub.transferapi.transfer.dto.TransferResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E na FRONTEIRA HTTP: exatamente o que um cliente real faz.
 * POST /transfers -> 202 -> (liquidação assíncrona) -> GET /transfers/{id}.
 *
 * O settlement-worker é outro módulo Maven (não está neste classpath); seu papel
 * é simulado publicando TransferSettled/TransferFailed no broker real. O contrato
 * JSON entre os serviços é pinado por testes de contrato em ambos os lados.
 * O E2E de processos reais acontece via docker-compose (Etapa 10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferFlowE2ETest {

    // Porta aleatória do servidor real, via property clássica (estável entre versões).
    @Value("${local.server.port}")
    int port;

    private RestClient rest() {
        return RestClient.create("http://localhost:" + port);
    }

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    /** Cria a conta pela API (exercita POST /accounts) e semeia saldo via domínio. */
    private UUID createAccountWithBalance(String document, String balance) {
        ResponseEntity<AccountResponse> created = rest().post()
                .uri("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest(document, "Titular " + document))
                .retrieve()
                .toEntity(AccountResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();

        UUID id = created.getBody().id();
        if (new BigDecimal(balance).signum() > 0) {
            Account account = accountRepository.findById(id).orElseThrow();
            account.credit(new BigDecimal(balance)); // não há endpoint de depósito (limitação documentada)
            accountRepository.save(account);
        }
        return id;
    }

    private ResponseEntity<TransferResponse> postTransfer(String key, UUID source, UUID target, String amount) {
        return rest().post()
                .uri("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .body(new CreateTransferRequest(source, target, new BigDecimal(amount)))
                .retrieve()
                .toEntity(TransferResponse.class);
    }

    private TransferResponse getTransfer(UUID id) {
        return rest().get()
                .uri("/api/v1/transfers/" + id)
                .retrieve()
                .body(TransferResponse.class);
    }

    private BigDecimal balanceOf(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    @Test
    void cenarioAprovado_postRecebe202_eTransferenciaCompleta() {
        UUID source = createAccountWithBalance("11122233344", "1000.00");
        UUID target = createAccountWithBalance("55566677788", "0.00");

        // 202 Accepted: aceita, NÃO concluída — a liquidação é assíncrona.
        ResponseEntity<TransferResponse> response = postTransfer("e2e-aprovada", source, target, "400.00");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.PENDING);
        UUID transferId = response.getBody().id();

        // Worker (simulado) aprova a liquidação.
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_TRANSFER_SETTLED,
                new TransferSettled(transferId, UUID.randomUUID(), OffsetDateTime.now()));

        // O cliente consulta o status até a conclusão — o mesmo polling do mundo real.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getTransfer(transferId).status()).isEqualTo(TransferStatus.COMPLETED));

        assertThat(balanceOf(source)).usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("600.00"));
        assertThat(balanceOf(target)).usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("400.00"));
    }

    @Test
    void cenarioRejeitadoPorLimite_falhaComEstorno() {
        UUID source = createAccountWithBalance("99988877766", "1000.00");
        UUID target = createAccountWithBalance("44433322211", "0.00");

        ResponseEntity<TransferResponse> response = postTransfer("e2e-rejeitada", source, target, "400.00");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID transferId = response.getBody().id();

        // Débito imediato: origem já está com 600 enquanto PENDING.
        assertThat(balanceOf(source)).usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("600.00"));

        // Worker (simulado) rejeita por limite.
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_TRANSFER_FAILED,
                new TransferFailed(transferId, "Limite diario de 100000.00 para a conta destino excedido",
                        OffsetDateTime.now()));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TransferResponse t = getTransfer(transferId);
            assertThat(t.status()).isEqualTo(TransferStatus.FAILED);
            assertThat(t.failureReason()).contains("Limite diario");
        });

        // Transação compensatória: origem restaurada; destino nunca recebeu.
        assertThat(balanceOf(source)).usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("1000.00"));
        assertThat(balanceOf(target)).usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("0.00"));
    }
}
