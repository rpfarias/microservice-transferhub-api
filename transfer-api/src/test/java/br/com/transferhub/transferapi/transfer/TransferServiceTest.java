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
 * há banco envolvido. Testamos apenas a orquestração (validações, ordem das
 * chamadas, o que NÃO deve ser salvo em caso de erro).
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    TransferRepository transferRepository;

    @InjectMocks
    TransferService service;

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
    @DisplayName("mesma conta de origem e destino é rejeitada antes de tocar o repositório")
    void mesmaConta_rejeitada() {
        assertThatExceptionOfType(SameAccountException.class)
                .isThrownBy(() -> service.transfer(sourceId, sourceId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("conta de origem inexistente lança e não persiste nada")
    void origemInexistente_lanca() {
        when(accountRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.transfer(sourceId, targetId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("conta de destino inexistente lança e não persiste nada")
    void destinoInexistente_lanca() {
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(contaCom("100.00")));
        when(accountRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> service.transfer(sourceId, targetId, new BigDecimal("10.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("saldo insuficiente aborta e não persiste a transferência")
    void saldoInsuficiente_naoPersiste() {
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(contaCom("50.00")));
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(contaCom("0.00")));

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> service.transfer(sourceId, targetId, new BigDecimal("500.00")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("transferência válida debita, credita e salva como COMPLETED")
    void transferenciaValida_salvaCompleted() {
        Account origem = contaCom("1000.00");
        Account destino = contaCom("0.00");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(origem));
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(destino));
        // devolve a própria Transfer que o service mandar salvar
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer resultado = service.transfer(sourceId, targetId, new BigDecimal("300.00"));

        assertThat(resultado.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(origem.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("700.00"));
        assertThat(destino.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("300.00"));
        verify(transferRepository).save(any(Transfer.class));
    }
}
