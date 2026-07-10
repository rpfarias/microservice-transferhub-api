package br.com.transferhub.transferapi.transfer;

import br.com.transferhub.transferapi.TestcontainersConfiguration;
import br.com.transferhub.transferapi.account.Account;
import br.com.transferhub.transferapi.account.AccountRepository;
import br.com.transferhub.transferapi.common.exception.InsufficientBalanceException;
import br.com.transferhub.transferapi.common.exception.SameAccountException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Verificação da Etapa 2 contra Postgres real (Testcontainers). Foco: a
 * transferência síncrona é atômica — débito e crédito acontecem juntos, e uma
 * falha de regra deixa os saldos intactos (rollback).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferServiceIntegrationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    private Account newAccount(String document, String balance) {
        Account account = new Account(document, "Titular " + document);
        if (new BigDecimal(balance).signum() > 0) {
            account.credit(new BigDecimal(balance)); // semeia saldo (não há endpoint de depósito)
        }
        return accountRepository.save(account);
    }

    @Test
    void transferenciaAprovada_debitaOrigemECreditaDestino() {
        Account source = newAccount("11111111111", "1000.00");
        Account target = newAccount("22222222222", "0.00");

        Transfer transfer = transferService.transfer(source.getId(), target.getId(), new BigDecimal("300.00"));

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        // compareTo (não equals) para comparar valor ignorando a escala.
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("700.00"));
        assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    void saldoInsuficiente_abortaTransacao_saldosIntactos() {
        Account source = newAccount("33333333333", "100.00");
        Account target = newAccount("44444444444", "0.00");

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> transferService.transfer(source.getId(), target.getId(), new BigDecimal("500.00")));

        // Atomicidade: como a transação sofreu rollback, NENHUM saldo mudou.
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("100.00"));
        assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void mesmaContaOrigemEDestino_rejeitada() {
        Account account = newAccount("55555555555", "100.00");

        assertThatExceptionOfType(SameAccountException.class)
                .isThrownBy(() -> transferService.transfer(account.getId(), account.getId(), new BigDecimal("10.00")));
    }
}
