package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.common.exception.AccountNotFoundException;
import br.com.transferhub.transferapi.common.exception.InsufficientBalanceException;
import br.com.transferhub.transferapi.common.exception.SameAccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Teste UNITÁRIO do service com Mockito: os repositórios são mocks, então não
 * há banco. Testamos a orquestração — incluindo a idempotência e o que NÃO deve
 * ser salvo em caso de erro.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    TransferRepository transferRepository;

    @InjectMocks
    TransferService service;

    private final String key = "idem-key-1";
    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private Account contaCom(String saldo) {
        Account conta = new Account("00000000000", "Titular");
        BigDecimal valor = new BigDecimal(saldo);
        if (valor.signum() > 0) { // credit() rejeita zero/negativo; conta nova já nasce com saldo 0
            conta.credit(valor);
        }
        return conta;
    }

    @Test
    @DisplayName("chave de idempotência já existente devolve a anterior sem criar nova")
    void chaveExistente_devolveExistenteSemCriar() {
        Transfer existente = new Transfer(sourceId, targetId, new BigDecimal("10.00"), key);
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existente));

        TransferResult result = service.transfer(key, sourceId, targetId, new BigDecimal("10.00"));

        assertThat(result.created()).isFalse();
        assertThat(result.transfer()).isSameAs(existente);
        // Nem toca nas contas, nem salva de novo.
        verify(accountRepository, never()).findById(any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("mesma conta de origem e destino é rejeitada")
    void mesmaConta_rejeitada() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SameAccountException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, sourceId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("conta de origem inexistente lança e não persiste nada")
    void origemInexistente_lanca() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, targetId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("conta de destino inexistente lança e não persiste nada")
    void destinoInexistente_lanca() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(contaCom("100.00")));
        when(accountRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, targetId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("saldo insuficiente aborta e não persiste a transferência")
    void saldoInsuficiente_naoPersiste() {
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(contaCom("50.00")));
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(contaCom("0.00")));

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> service.transfer(key, sourceId, targetId, new BigDecimal("500.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("transferência válida cria, debita, credita e marca COMPLETED")
    void transferenciaValida_criaCompleted() {
        Account origem = contaCom("1000.00");
        Account destino = contaCom("0.00");
        when(transferRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(origem));
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(destino));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = service.transfer(key, sourceId, targetId, new BigDecimal("300.00"));

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.transfer().getIdempotencyKey()).isEqualTo(key);
        assertThat(origem.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("700.00"));
        assertThat(destino.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("300.00"));
        verify(transferRepository).save(any(Transfer.class));
    }
}
