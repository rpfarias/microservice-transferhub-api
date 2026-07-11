package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.common.exception.AccountNotFoundException;
import br.com.transferhub.transferapi.common.exception.InsufficientBalanceException;
import br.com.transferhub.transferapi.common.exception.SameAccountException;
import br.com.transferhub.transferapi.messaging.TransferRequested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste UNITÁRIO do service com Mockito (sem banco). Cobre idempotência, validações
 * e — a partir da Etapa 5 — que a transferência fica PENDING e um evento
 * TransferRequested é publicado (o crédito no destino NÃO acontece mais aqui).
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    TransferRepository transferRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    TransferService service;

    private final String key = "idem-key-1";
    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private Account contaCom(String saldo) {
        Account conta = new Account("00000000000", "Titular");
        BigDecimal valor = new BigDecimal(saldo);
        if (valor.signum() > 0) {
            conta.credit(valor);
        }
        return conta;
    }

    @Test
    @DisplayName("chave de idempotência já existente devolve a anterior sem criar nem publicar")
    void chaveExistente_devolveExistenteSemCriar() {
        Transfer existente = new Transfer(sourceId, targetId, new BigDecimal("10.00"), key);
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existente));

        TransferResult result = service.transfer(key, sourceId, targetId, new BigDecimal("10.00"));

        assertThat(result.created()).isFalse();
        assertThat(result.transfer()).isSameAs(existente);
        verify(accountRepository, never()).findById(any());
        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("mesma conta de origem e destino é rejeitada")
    void mesmaConta_rejeitada() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SameAccountException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, sourceId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("conta de origem inexistente lança e não persiste nem publica")
    void origemInexistente_lanca() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, targetId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("conta de destino inexistente lança e não persiste nem publica")
    void destinoInexistente_lanca() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(contaCom("100.00")));
        when(accountRepository.existsById(targetId)).thenReturn(false);

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, targetId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("saldo insuficiente aborta e não persiste nem publica")
    void saldoInsuficiente_naoPersiste() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(contaCom("50.00")));
        when(accountRepository.existsById(targetId)).thenReturn(true);

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, targetId, new BigDecimal("500.00")));

        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("transferência válida debita a origem, fica PENDING e publica TransferRequested")
    void transferenciaValida_debitaEPublica() {
        Account origem = contaCom("1000.00");
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(origem));
        when(accountRepository.existsById(targetId)).thenReturn(true);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = service.transfer(key, sourceId, targetId, new BigDecimal("300.00"));

        assertThat(result.created()).isTrue();
        // Assíncrono: a transferência NÃO é COMPLETED aqui, fica PENDING.
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(result.transfer().getIdempotencyKey()).isEqualTo(key);
        // Origem debitada; destino NÃO é creditado nesta etapa.
        assertThat(origem.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("700.00"));
        verify(transferRepository).save(any(Transfer.class));
        verify(eventPublisher).publishEvent(any(TransferRequested.class));
    }

    // ---------- Etapa 7: consumers (settle / fail) ----------

    @Test
    @DisplayName("settle credita o destino e marca COMPLETED")
    void settle_creditaDestinoECompleta() {
        Transfer pending = new Transfer(sourceId, targetId, new BigDecimal("300.00"), key);
        Account destino = contaCom("0.00");
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(pending));
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(destino));

        service.settle(transferId);

        assertThat(pending.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(destino.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("settle duplicado (status != PENDING) ignora — não credita duas vezes")
    void settle_duplicado_ignora() {
        Transfer jaConcluida = new Transfer(sourceId, targetId, new BigDecimal("300.00"), key);
        jaConcluida.complete(); // primeira entrega já processou
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(jaConcluida));

        service.settle(transferId);

        // Nem toca na conta destino: crédito duplo evitado.
        verify(accountRepository, never()).findById(any());
    }

    @Test
    @DisplayName("fail estorna a origem (transação compensatória) e marca FAILED com motivo")
    void fail_estornaOrigem() {
        Transfer pending = new Transfer(sourceId, targetId, new BigDecimal("300.00"), key);
        Account origem = contaCom("700.00"); // origem após o débito original de 300
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(pending));
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(origem));

        service.fail(transferId, "limite diario excedido");

        assertThat(pending.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(pending.getFailureReason()).isEqualTo("limite diario excedido");
        // Estorno: origem volta ao saldo pré-débito.
        assertThat(origem.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("fail duplicado (status != PENDING) ignora — não estorna duas vezes")
    void fail_duplicado_ignora() {
        Transfer jaFalhada = new Transfer(sourceId, targetId, new BigDecimal("300.00"), key);
        jaFalhada.fail("motivo original");
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(jaFalhada));

        service.fail(transferId, "segunda entrega");

        verify(accountRepository, never()).findById(any());
        assertThat(jaFalhada.getFailureReason()).isEqualTo("motivo original");
    }
}
