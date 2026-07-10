package br.com.transferhub.transferapi.account;

import br.com.transferhub.transferapi.common.exception.InsufficientBalanceException;
import br.com.transferhub.transferapi.common.exception.InvalidAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Teste UNITÁRIO puro do domínio: sem Spring, sem banco, sem mocks.
 * Só instancia a Account e exercita as invariantes. Roda em milissegundos.
 */
class AccountTest {

    private Account contaCom(String saldo) {
        Account conta = new Account("11111111111", "Fulano");
        conta.credit(new BigDecimal(saldo)); // semeia saldo pelo próprio domínio
        return conta;
    }

    @Test
    @DisplayName("débito reduz o saldo")
    void debita_reduzSaldo() {
        Account conta = contaCom("100.00");

        conta.debit(new BigDecimal("30.00"));

        assertThat(conta.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("débito igual ao saldo é permitido (zera a conta)")
    void debita_exatamenteOSaldo_ok() {
        Account conta = contaCom("100.00");

        conta.debit(new BigDecimal("100.00"));

        assertThat(conta.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("saldo insuficiente lança exceção E não altera o saldo")
    void debita_saldoInsuficiente_lancaENaoAltera() {
        Account conta = contaCom("100.00");

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> conta.debit(new BigDecimal("100.01")));

        // A invariante protege o estado: nada mudou.
        assertThat(conta.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("valor negativo, zero ou null é rejeitado em débito e crédito")
    void valorInvalido_rejeitado() {
        Account conta = contaCom("100.00");

        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> conta.debit(new BigDecimal("-1")));
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> conta.debit(BigDecimal.ZERO));
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> conta.debit(null));
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> conta.credit(new BigDecimal("-5")));
    }

    @Test
    @DisplayName("crédito aumenta o saldo")
    void credita_aumentaSaldo() {
        Account conta = contaCom("100.00");

        conta.credit(new BigDecimal("50.50"));

        assertThat(conta.getBalance()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("150.50"));
    }
}
