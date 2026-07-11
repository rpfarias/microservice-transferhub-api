package br.com.transferhub.settlementworker.messaging;

import br.com.transferhub.settlementworker.settlement.BusinessRuleException;
import br.com.transferhub.settlementworker.settlement.SettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A separação exceção de negócio vs. técnica, testada no ponto onde ela mora:
 * - negócio  -> capturada -> reject() imediato (sem retry)
 * - técnica  -> propaga    -> container faz retry -> DLQ
 */
@ExtendWith(MockitoExtension.class)
class SettlementListenerTest {

    @Mock
    SettlementService settlementService;

    @InjectMocks
    SettlementListener listener;

    private final TransferRequested event = new TransferRequested(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("60000.00"), OffsetDateTime.now());

    @Test
    @DisplayName("BusinessRuleException é capturada e vira reject imediato (sem retry)")
    void excecaoDeNegocio_viraRejectImediato() {
        doThrow(new BusinessRuleException("limite excedido")).when(settlementService).process(event);

        listener.onTransferRequested(event); // NÃO propaga -> container não faz retry

        verify(settlementService).reject(event, "limite excedido");
    }

    @Test
    @DisplayName("exceção técnica propaga (container fará retry e, esgotado, DLQ)")
    void excecaoTecnica_propaga() {
        doThrow(new RuntimeException("banco fora")).when(settlementService).process(event);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> listener.onTransferRequested(event));

        verify(settlementService, never()).reject(any(), any());
    }
}
