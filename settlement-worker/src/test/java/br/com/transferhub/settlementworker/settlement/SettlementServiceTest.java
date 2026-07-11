package br.com.transferhub.settlementworker.settlement;

import br.com.transferhub.settlementworker.messaging.TransferFailed;
import br.com.transferhub.settlementworker.messaging.TransferRequested;
import br.com.transferhub.settlementworker.messaging.TransferSettled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    SettlementRecordRepository repository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    SettlementService service;

    private final UUID transferId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private TransferRequested req(String amount) {
        return new TransferRequested(transferId, UUID.randomUUID(), targetId,
                new BigDecimal(amount), OffsetDateTime.now());
    }

    private SettlementRecord savedRecord() {
        ArgumentCaptor<SettlementRecord> captor = ArgumentCaptor.forClass(SettlementRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("dentro dos limites: grava SETTLED e publica TransferSettled")
    void aprovado_settled() {
        when(repository.existsByTransferId(transferId)).thenReturn(false);
        when(repository.sumSettledToTargetSince(eq(targetId), any())).thenReturn(BigDecimal.ZERO);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req("10000.00"));

        assertThat(savedRecord().getOutcome()).isEqualTo(SettlementOutcome.SETTLED);
        verify(eventPublisher).publishEvent(any(TransferSettled.class));
    }

    @Test
    @DisplayName("acima de 50.000 por transação: REJECTED e TransferFailed (nem consulta o histórico)")
    void rejeitado_porLimitePorTransacao() {
        when(repository.existsByTransferId(transferId)).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req("60000.00"));

        SettlementRecord record = savedRecord();
        assertThat(record.getOutcome()).isEqualTo(SettlementOutcome.REJECTED);
        assertThat(record.getRejectionReason()).isNotBlank();
        verify(eventPublisher).publishEvent(any(TransferFailed.class));
    }

    @Test
    @DisplayName("soma 24h + valor acima de 100.000: REJECTED e TransferFailed")
    void rejeitado_porLimiteDiario() {
        when(repository.existsByTransferId(transferId)).thenReturn(false);
        when(repository.sumSettledToTargetSince(eq(targetId), any())).thenReturn(new BigDecimal("80000.00"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req("30000.00")); // 80.000 + 30.000 = 110.000 > 100.000

        assertThat(savedRecord().getOutcome()).isEqualTo(SettlementOutcome.REJECTED);
        verify(eventPublisher).publishEvent(any(TransferFailed.class));
    }

    @Test
    @DisplayName("exatamente no limite diário (100.000): aprovado")
    void aprovado_noLimiteExato() {
        when(repository.existsByTransferId(transferId)).thenReturn(false);
        when(repository.sumSettledToTargetSince(eq(targetId), any())).thenReturn(new BigDecimal("70000.00"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.process(req("30000.00")); // 70.000 + 30.000 = 100.000 (não excede)

        assertThat(savedRecord().getOutcome()).isEqualTo(SettlementOutcome.SETTLED);
        verify(eventPublisher).publishEvent(any(TransferSettled.class));
    }

    @Test
    @DisplayName("transfer_id já processado: ignora (idempotência do consumer)")
    void jaProcessado_ignora() {
        when(repository.existsByTransferId(transferId)).thenReturn(true);

        service.process(req("10000.00"));

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
