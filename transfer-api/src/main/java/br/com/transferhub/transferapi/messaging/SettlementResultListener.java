package br.com.transferhub.transferapi.messaging;

import br.com.transferhub.transferapi.transfer.TransferService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome os resultados da liquidação publicados pelo settlement-worker.
 * Adaptador fino: a lógica (crédito / estorno / idempotência) vive no service.
 */
@Component
public class SettlementResultListener {

    private final TransferService transferService;

    public SettlementResultListener(TransferService transferService) {
        this.transferService = transferService;
    }

    @RabbitListener(queues = RabbitConfig.Q_TRANSFER_SETTLED)
    public void onTransferSettled(TransferSettled event) {
        transferService.settle(event.transferId());
    }

    @RabbitListener(queues = RabbitConfig.Q_TRANSFER_FAILED)
    public void onTransferFailed(TransferFailed event) {
        transferService.fail(event.transferId(), event.reason());
    }
}
