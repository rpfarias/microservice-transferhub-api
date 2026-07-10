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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Verificação de integração contra Postgres real (Testcontainers). Cobre a
 * atomicidade (Etapa 2) e a idempotência (Etapa 4).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferServiceIntegrationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRepository transferRepository;

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

        TransferResult result = transferService.transfer(
                "key-aprovada", source.getId(), target.getId(), new BigDecimal("300.00"));

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
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
                .isThrownBy(() -> transferService.transfer(
                        "key-insuf", source.getId(), target.getId(), new BigDecimal("500.00")));

        // Atomicidade: rollback -> nenhum saldo mudou.
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("100.00"));
        assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("0.00"));
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
        // A segunda chamada devolve exatamente a mesma transferência.
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());
        // E o saldo foi debitado UMA vez só (900, não 800).
        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal("900.00"));
        assertThat(transferRepository.findByIdempotencyKey(key)).isPresent();
    }
}
